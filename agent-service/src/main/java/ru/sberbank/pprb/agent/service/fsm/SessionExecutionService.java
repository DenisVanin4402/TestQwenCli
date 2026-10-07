package ru.sberbank.pprb.agent.service.fsm;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.sberbank.pprb.agent.db.repository.SessionRepository;
import ru.sberbank.pprb.agent.db.session.SessionSemaphore;
import ru.sberbank.pprb.agent.model.dto.payment.PreparationDTO;
import ru.sberbank.pprb.agent.model.dto.payment.TrustedPaymentContextDTO;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.entity.persistence.session.SessionEntity;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.model.enums.SessionStateCategory;
import ru.sberbank.pprb.agent.model.enums.TurnOutcome;
import ru.sberbank.pprb.agent.service.fsm.guard.common.ContextConsistencyGuard;

/**
 * Создаёт или восстанавливает одну машину для обработки сообщения и сохраняет тот же экземпляр.
 * Предметные данные сценария находятся в расширенном состоянии машины (ExtendedState), а штатный
 * механизм хранения Spring Statemachine читает и записывает их в транзакции вызывающего кода.
 */
@Service
@RequiredArgsConstructor
public class SessionExecutionService {
    /** Имя поля lastResult в сохранённом формате ExtendedState. */
    private static final String LAST_RESULT = "lastResult";

    /** Имя поля lastRequestId в сохранённом формате ExtendedState. */
    private static final String LAST_REQUEST_ID = "lastRequestId";

    /** Имя поля preparation в сохранённом формате ExtendedState. */
    private static final String PREPARATION = "preparation";

    /** Имя поля sessionId в сохранённом формате ExtendedState. */
    private static final String SESSION_ID = "sessionId";

    /** Имя поля context в сохранённом формате ExtendedState. */
    private static final String CONTEXT = "context";

    /** Имя поля projectionVersion в сохранённом формате ExtendedState. */
    private static final String PROJECTION_VERSION = "projectionVersion";

    /** Имя поля lastPreparationNo в сохранённом формате ExtendedState. */
    private static final String LAST_PREPARATION_NO = "lastPreparationNo";

    /** Проверяет целостность восстановленного снимка без доступа к новому входу. */
    private final StoredSnapshotValidator storedValidator;

    /** Допустимые переходы сценария с условиями и действиями, которые подключаются к машине. */
    private final SessionStateMachineConfiguration configuration;

    /** Постоянная строка сессии и её транзакционные блокировки. */
    private final SessionRepository sessions;

    /** Создание семафора отдельной транзакцией до обработки события. */
    private final SessionSemaphore semaphore;

    /**
     * Проверяет наличие штатной записи машины, чтобы отличать создание сессии от восстановления.
     */
    private final JpaStateMachineRepository repository;

    /**
     * Восстанавливает и сохраняет машину штатным механизмом Spring Statemachine в общей транзакции.
     */
    private final DefaultStateMachinePersister<SessionState, SessionEvent, Object> persister;

    /**
     * Восстанавливает данные под уже захваченным семафором, выполняет событие и сохраняет
     * результат. При исключении изменённый экземпляр отбрасывается, а рабочая транзакция должна
     * откатиться.
     */
    private SessionSnapshotDTO processLocked(
            SessionEntity session, SessionTurnInDTO input, boolean navigationReset) {
        StateMachine<SessionState, SessionEvent> machine = null;
        Exception failure = null;
        SessionExecutionContext execution = new SessionExecutionContext(input);
        try {
            boolean exists = repository.existsById(session.getSessionId().toString());
            if (navigationReset && !exists) return null;

            verifyFormat(session);
            if (!exists) {
                execution.snapshot =
                        SessionSnapshotDTO.builder()
                                .sessionId(session.getSessionId())
                                .state(SessionState.CHOOSING_REQUEST_TYPE)
                                .build();
            }
            machine = configuration.create(session.getSessionId());
            if (exists) {
                persister.restore(machine, session.getSessionId().toString());
                execution.snapshot = snapshot(machine, session.getSessionId());
                execution.restoredTerminal =
                        execution.snapshot.getState().getCategory()
                                == SessionStateCategory.TERMINAL;

                if (navigationReset) {
                    if (!ContextConsistencyGuard.matches(
                            input.getContext(), execution.snapshot.getContext())) {
                        throw new SessionPersistenceException(
                                ResultCode.CONTEXT_MISMATCH, "Контекст не совпадает");
                    }
                    if (execution.snapshot.getState().getCategory()
                            != SessionStateCategory.INTERMEDIATE) return execution.snapshot;
                }

            } else {
                machine.startReactively().block();
            }

            machine.sendEvent(
                            Mono.just(
                                    MessageBuilder.withPayload(input.getEvent())
                                            .setHeader(SessionExecutionContext.HEADER, execution)
                                            .build()))
                    .concatMap(result -> result.complete())
                    .then()
                    .block();
            // Завершение обработки события не гарантирует успеха: SSM могла перехватить исключение
            // условия или действия. Перед сохранением возвращаем такую ошибку на границу
            // транзакции.
            if (execution.error != null) {
                throw new SessionPersistenceException(
                        ResultCode.OPERATION_FAILED,
                        "Не удалось завершить действие",
                        execution.error);
            }
            if (machine.hasStateMachineError()) {
                throw new SessionPersistenceException(
                        ResultCode.OPERATION_FAILED, "Ошибка исполнения машины");
            }
            if (execution.rejectionCode != null) {
                throw new SessionPersistenceException(
                        execution.rejectionCode, "Переход отклонён guard");
            }
            if (execution.admitted && !execution.actionCompleted) {
                throw new SessionPersistenceException(
                        ResultCode.OPERATION_FAILED, "Допущенное действие не завершилось");
            }
            SessionSnapshotDTO snapshot = execution.snapshot;
            if (snapshot.getContext() == null) {
                throw new SessionPersistenceException(
                        ResultCode.INVALID_COMMAND, "Бизнес-сессия не инициализирована");
            }
            if (!execution.actionCompleted) {
                if (navigationReset)
                    throw new SessionPersistenceException(
                            ResultCode.INVALID_COMMAND, "Отмена шага не принята");
                snapshot.setLastResult(
                        machine.getState().getId() == SessionState.COMPLETED
                                ? TurnOutcome.SESSION_COMPLETED
                                : TurnOutcome.INVALID_COMMAND);
            }
            snapshot.setState(machine.getState().getId());
            snapshot.setLastRequestId(input.getRequestId());
            snapshot.setProjectionVersion(snapshot.getProjectionVersion() + 1);
            putScenario(machine, snapshot);
            persister.persist(machine, session.getSessionId().toString());
            return snapshot;
        } catch (Exception exception) {
            failure = exception;
            throw new SessionPersistenceException(storage(exception), execution.restoredTerminal);
        } finally {
            stop(machine, failure);
        }
    }

    /**
     * Восстанавливает снимок существующей машины под уже захваченной блокировкой чтения, без
     * обработки событий и записи. Отсутствие сохранённой машины допустимо после сбоя первого
     * сообщения; повреждение существующих данных возвращается как ошибка восстановления.
     */
    private Optional<SessionSnapshotDTO> read(SessionEntity session) {
        if (!repository.existsById(session.getSessionId().toString())) {
            return Optional.empty();
        }
        StateMachine<SessionState, SessionEvent> machine = null;
        Exception failure = null;
        try {
            verifyFormat(session);
            machine = configuration.create(session.getSessionId());
            persister.restore(machine, session.getSessionId().toString());
            return Optional.of(snapshot(machine, session.getSessionId()));
        } catch (Exception exception) {
            failure = exception;
            throw storage(exception);
        } finally {
            stop(machine, failure);
        }
    }

    /**
     * Проверяет совместимость сохранённого формата до чтения сериализованных данных машины.
     * Несовместимость считается ошибкой хранения и не разрешает создать новую машину вместо старой.
     */
    private void verifyFormat(SessionEntity session) {
        if (!SessionSemaphore.FORMAT_ID.equals(session.getFormatId())) {
            throw new SessionPersistenceException(
                    ResultCode.STORAGE_UNAVAILABLE, "Несовместимый формат FSM");
        }
    }

    /**
     * Проверяет принадлежность восстановленной машины запрошенной сессии и наличие обязательных
     * данных сценария. Шаг берётся из состояния самой машины, а контекст, подготовка и результат —
     * из её расширенного состояния; повреждённые данные не заменяются значениями по умолчанию.
     * Черновик должен относиться к исходному платежу и последнему номеру подготовки, а наличие
     * обоих полей согласия должно соответствовать шагу: ожидание подтверждения либо завершение.
     */
    private SessionSnapshotDTO snapshot(
            StateMachine<SessionState, SessionEvent> machine, UUID sessionId) {
        Map<Object, Object> values = machine.getExtendedState().getVariables();
        if (machine.getState() == null
                || !sessionId.equals(values.get(SESSION_ID))
                || !(values.get(CONTEXT) instanceof TrustedPaymentContextDTO)
                || !(values.get(PROJECTION_VERSION) instanceof Long version)
                || version < 1
                || !(values.get(LAST_PREPARATION_NO) instanceof Long number)
                || number < 1
                || !(values.get(LAST_RESULT) instanceof TurnOutcome)
                || !(values.get(LAST_REQUEST_ID) instanceof UUID)) {
            throw new IllegalStateException("Сохранённые данные не соответствуют сессии");
        }
        PreparationDTO preparation = (PreparationDTO) values.get(PREPARATION);
        TrustedPaymentContextDTO payment = (TrustedPaymentContextDTO) values.get(CONTEXT);
        SessionState state = machine.getState().getId();
        storedValidator.verify(payment, preparation, state, number);
        return SessionSnapshotDTO.builder()
                .sessionId(sessionId)
                .context(payment)
                .state(state)
                .projectionVersion(version)
                .lastPreparationNo(number)
                .preparation(preparation)
                .lastResult((TurnOutcome) values.get(LAST_RESULT))
                .lastRequestId((UUID) values.get(LAST_REQUEST_ID))
                .build();
    }

    /**
     * Обновляет расширенное состояние машины перед сохранением, удаляя прежние значения, включая
     * отменённую подготовку. Записывает только предметные данные; тексты ответов создаёт
     * оркестратор.
     */
    private void putScenario(
            StateMachine<SessionState, SessionEvent> machine, SessionSnapshotDTO snapshot) {
        Map<Object, Object> values = machine.getExtendedState().getVariables();
        values.clear();
        values.put(SESSION_ID, snapshot.getSessionId());
        values.put(CONTEXT, snapshot.getContext());
        values.put(PROJECTION_VERSION, snapshot.getProjectionVersion());
        values.put(LAST_PREPARATION_NO, snapshot.getLastPreparationNo());
        values.put(LAST_RESULT, snapshot.getLastResult());
        values.put(LAST_REQUEST_ID, snapshot.getLastRequestId());
        if (snapshot.getPreparation() != null) {
            values.put(PREPARATION, snapshot.getPreparation());
        }
    }

    /**
     * Останавливает временный экземпляр машины после обработки или чтения. Если основная операция
     * уже завершилась ошибкой, ошибка остановки добавляется к ней и не скрывает причину отката.
     */
    private void stop(StateMachine<SessionState, SessionEvent> machine, Exception failure) {
        if (machine == null) {
            return;
        }
        try {
            machine.stopReactively().block();
        } catch (RuntimeException exception) {
            if (failure != null) {
                failure.addSuppressed(exception);
            } else {
                throw storage(exception);
            }
        }
    }

    /**
     * Сохраняет известный код ошибки либо преобразует сбой штатного хранилища в непроверяемое
     * исключение, вызывающее откат JPA-транзакции. Ошибка чтения не означает отсутствия машины.
     */
    private SessionPersistenceException storage(Exception exception) {
        if (exception instanceof SessionPersistenceException known) {
            return known;
        }
        return new SessionPersistenceException(
                ResultCode.STORAGE_UNAVAILABLE,
                "Сохранённое состояние сценария недоступно",
                exception);
    }

    /**
     * Обеспечивает постоянную строку сессии до начала рабочей транзакции. Если конкурентный запрос
     * создал её первым, проверяет наличие записи после отката неудачной вставки.
     */
    public void ensureSemaphore(UUID sessionId) {
        try {
            semaphore.ensure(sessionId);
        } catch (DataIntegrityViolationException exception) {
            // Конкурентная вставка уже завершилась откатом отдельной транзакции.
            // Другие ошибки ограничений нельзя выдавать за успешную регистрацию.
            if (!sessions.existsById(sessionId)) {
                throw exception;
            }
        }
    }

    /**
     * Без ожидания занятой строки получает исключительную блокировку сессии и обрабатывает
     * сообщение. Блокировка удерживается до фиксации состояния машины и предметного результата либо
     * до отката всей рабочей транзакции.
     */
    @Transactional
    public SessionSnapshotDTO process(SessionTurnInDTO input) {
        SessionEntity session;
        try {
            session =
                    sessions.getAndLock(input.getSessionId())
                            .orElseThrow(
                                    () ->
                                            new SessionPersistenceException(
                                                    ResultCode.SESSION_NOT_FOUND,
                                                    "Семафор сессии отсутствует"));
        } catch (PessimisticLockingFailureException exception) {
            throw busy(exception);
        }
        return processLocked(session, input, false);
    }

    /**
     * Отменяет существующий промежуточный шаг через SSM без создания семафора или второй машины.
     */
    @Transactional
    public Optional<SessionSnapshotDTO> cancelIfPresent(SessionTurnInDTO input) {
        try {
            Optional<SessionEntity> session = sessions.getAndLock(input.getSessionId());
            if (session.isEmpty()) return Optional.empty();
            SessionTurnInDTO cancel =
                    input.toBuilder().event(SessionEvent.CANCEL).navigationAction(null).build();
            return Optional.ofNullable(processLocked(session.get(), cancel, true));
        } catch (PessimisticLockingFailureException exception) {
            throw busy(exception);
        }
    }

    /**
     * Восстанавливает существующую сессию под блокировкой чтения в короткой транзакции. Если записи
     * или сохранённой машины нет, возвращает пустой результат; при конфликте с обработкой сообщения
     * сообщает занятость без повторного захвата блокировки. Транзакция не помечена readOnly:
     * PostgreSQL запрещает FOR SHARE в такой транзакции, хотя этот метод не сохраняет данные.
     */
    @Transactional
    public Optional<SessionSnapshotDTO> find(UUID sessionId) {
        try {
            Optional<SessionEntity> session = sessions.getForRead(sessionId);
            return session.flatMap(this::read);
        } catch (PessimisticLockingFailureException exception) {
            throw busy(exception);
        }
    }

    /**
     * Преобразует конфликт блокировки NOWAIT в понятный оркестратору исход занятости сессии. Запрос
     * завершается без очереди, повторного захвата и выполнения предметного действия.
     */
    private SessionPersistenceException busy(RuntimeException cause) {
        return new SessionPersistenceException(
                ResultCode.SESSION_BUSY, "Сессия уже обрабатывает другое сообщение", cause);
    }
}
