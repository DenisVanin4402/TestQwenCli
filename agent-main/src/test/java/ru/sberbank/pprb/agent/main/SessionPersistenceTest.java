package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.sberbank.pprb.agent.db.repository.SessionRepository;
import ru.sberbank.pprb.agent.db.session.SessionSemaphore;
import ru.sberbank.pprb.agent.model.dto.payment.PaymentContextInDTO;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.entity.persistence.session.SessionEntity;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.SessionExecutionService;
import ru.sberbank.pprb.agent.service.fsm.SessionPersistenceException;
import ru.sberbank.pprb.agent.service.port.out.WorkflowManager;

/**
 * Проверяет обработку и восстановление сессии с настоящими Spring Statemachine, Liquibase и JPA.
 * Подменённый внешний менеджер позволяет вызвать успех или ошибку действия и проверить совместную
 * фиксацию либо откат состояния машины и прикладной записи, а также отсутствие повторных вызовов.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:session-runtime;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"poc-local", "h2"})
class SessionPersistenceTest {
    @Autowired ru.sberbank.pprb.agent.service.port.in.SessionTurnUseCase turns;

    @Test
    void navigationReadsWithoutWritingAndResetInvalidatesOldConsent() throws Exception {
        UUID id = UUID.randomUUID();
        var navigation =
                SessionTurnInDTO.builder()
                        .sessionId(id)
                        .requestId(UUID.randomUUID())
                        .navigationAction(NavigationAction.RESET)
                        .build();
        assertThat(engine.cancelIfPresent(navigation)).isEmpty();
        assertThat(
                        turns.handle(
                                        navigation.toBuilder()
                                                .navigationAction(NavigationAction.RESUME)
                                                .build(),
                                        "key")
                                .getResult()
                                .getCode())
                .isEqualTo(ResultCode.OPERATION_CHOICE_REQUIRED);
        assertThat(sessions.existsById(id)).isFalse();
        var proposal = prepare(id);
        process(id, SessionEvent.SELECT);
        byte[] before = nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext();
        clearInvocations(persister);
        var resumed =
                turns.handle(
                        navigation.toBuilder().navigationAction(NavigationAction.RESUME).build(),
                        "key");
        assertThat(resumed.getResult().getCode()).isEqualTo(ResultCode.CONFIRMATION_REQUIRED);
        assertThat(resumed.getRequestId()).isEqualTo(navigation.getRequestId());
        assertThat(nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext())
                .isEqualTo(before);
        verify(persister, never()).persist(any(), any());
        var reset = engine.cancelIfPresent(navigation).orElseThrow();
        assertThat(reset.getState()).isEqualTo(SessionState.CHOOSING_REQUEST_TYPE);
        assertThat(reset.getPreparation()).isNull();
        assertThat(reset.getContext()).usingRecursiveComparison().isEqualTo(proposal.getContext());
        assertThat(reset.getLastPreparationNo()).isEqualTo(1);
        before = nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext();
        engine.cancelIfPresent(navigation);
        assertThat(nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext())
                .isEqualTo(before);
        var next = prepare(id);
        assertThat(next.getPreparation().getPreparationNo()).isEqualTo(2);
        assertThatThrownBy(() -> confirm(id, proposal))
                .isInstanceOfSatisfying(
                        SessionPersistenceException.class,
                        error ->
                                assertThat(error.getCode())
                                        .isEqualTo(ResultCode.STALE_PREPARATION));
        var completed = confirm(id, next);
        before = nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext();
        assertThat(engine.cancelIfPresent(navigation).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(completed);
        assertThat(nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext())
                .isEqualTo(before);
        verify(workflow, times(1)).callOperation(any(), any());
    }

    @Test
    void navigationChecksContextAndResetRollsBackOnSaveFailure() throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        var supplied = new PaymentContextInDTO();
        supplied.setPaymentNumber("other");
        var input =
                SessionTurnInDTO.builder()
                        .sessionId(id)
                        .requestId(UUID.randomUUID())
                        .navigationAction(NavigationAction.RESET)
                        .context(supplied)
                        .build();
        assertThatThrownBy(() -> engine.cancelIfPresent(input))
                .isInstanceOfSatisfying(
                        SessionPersistenceException.class,
                        error ->
                                assertThat(error.getCode()).isEqualTo(ResultCode.CONTEXT_MISMATCH));
        var mismatch =
                turns.handle(
                        input.toBuilder().navigationAction(NavigationAction.RESUME).build(), "key");
        assertThat(mismatch.getResult().getCode()).isEqualTo(ResultCode.CONTEXT_MISMATCH);
        assertThat(mismatch.getSuggestions()).isEmpty();
        assertThat(mismatch.getConfirmation()).isEmpty();
        doThrow(new IllegalStateException("save failed")).when(persister).persist(any(), any());
        assertThatThrownBy(() -> engine.cancelIfPresent(input.toBuilder().context(null).build()))
                .isInstanceOf(SessionPersistenceException.class);
        assertThat(engine.find(id).orElseThrow()).usingRecursiveComparison().isEqualTo(proposal);
        verifyNoInteractions(workflow);
    }

    /** Восстанавливает native-байты прежней реализации и подтверждает старое предложение. */
    @Test
    void restoresStatusV3FromBeforeRefactoring() throws Exception {
        UUID id = UUID.fromString("ecb3faab-6cb5-4e5c-a36f-196738898102");
        prepare(id);
        try (var stream = getClass().getResourceAsStream("/fsm/status-v3-before.base64")) {
            assertThat(stream).isNotNull();
            byte[] bytes = java.util.Base64.getDecoder().decode(stream.readAllBytes());
            new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(
                            status -> {
                                var nativeRow = nativeStates.findById(id.toString()).orElseThrow();
                                nativeRow.setStateMachineContext(bytes);
                                nativeStates.save(nativeRow);
                            });
        }
        var proposal = engine.find(id).orElseThrow();
        assertThat(proposal.getState()).isEqualTo(SessionState.AWAITING_CONFIRM);
        assertThat(proposal.getPreparation().getPreparationNo()).isEqualTo(1);
        assertThat(proposal.getPreparation().getContext().getPaymentNumber()).isEqualTo("42");
        assertThat(confirm(id, proposal).getState()).isEqualTo(SessionState.COMPLETED);
        verify(workflow).callOperation(eq(Operation.STATUS), any());
    }

    @Autowired SessionExecutionService engine;
    @Autowired SessionRepository sessions;
    @MockitoBean WorkflowManager workflow;
    @MockitoSpyBean DefaultStateMachinePersister<SessionState, SessionEvent, Object> persister;
    @Autowired org.springframework.statemachine.data.jpa.JpaStateMachineRepository nativeStates;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @MockitoSpyBean
    ru.sberbank.pprb.agent.service.fsm.SessionStateMachineConfiguration configuration;

    @MockitoSpyBean ru.sberbank.pprb.agent.service.fsm.guard.common.ActivePreparationGuard active;

    /** Ошибка, перехваченная SSM внутри guard, не должна приводить к сохранению состояния. */
    @Test
    void propagatesGuardFailureWithoutSaving() throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        var failure = new IllegalStateException("guard failed");
        doThrow(failure).when(active).evaluate(any());
        clearInvocations(persister);
        assertThatThrownBy(() -> confirm(id, proposal))
                .isInstanceOfSatisfying(
                        SessionPersistenceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ResultCode.OPERATION_FAILED))
                .hasRootCause(failure);
        verify(persister, never()).persist(any(), any());
        assertThat(engine.find(id).orElseThrow()).usingRecursiveComparison().isEqualTo(proposal);
        verifyNoInteractions(workflow);
    }

    /** Остановка происходит до commit; её ошибка не подменяет основную ошибку action. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void stopFailureRollsBackAndPreservesPrimaryFailure(boolean actionFails) throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        var stopping = new java.util.concurrent.atomic.AtomicBoolean();
        var stopFailure = new IllegalStateException("stop failed");
        var actionFailure = new IllegalStateException("action failed");
        doAnswer(
                        call -> {
                            StateMachine<SessionState, SessionEvent> actual =
                                    (StateMachine<SessionState, SessionEvent>)
                                            call.callRealMethod();
                            StateMachine<SessionState, SessionEvent> machine =
                                    mock(
                                            actual.getClass(),
                                            org.mockito.AdditionalAnswers.delegatesTo(actual));
                            doAnswer(
                                            stop ->
                                                    stopping.get()
                                                            ? reactor.core.publisher.Mono.error(
                                                                    stopFailure)
                                                            : actual.stopReactively())
                                    .when(machine)
                                    .stopReactively();
                            return machine;
                        })
                .when(configuration)
                .create(id);
        doAnswer(
                        call -> {
                            stopping.set(true);
                            if (actionFails) throw actionFailure;
                            return null;
                        })
                .when(workflow)
                .callOperation(any(), any());
        var error = catchThrowable(() -> confirm(id, proposal));
        assertThat(error).isInstanceOf(SessionPersistenceException.class);
        if (actionFails) {
            assertThat(error).hasRootCause(actionFailure);
            assertThat(error.getCause().getSuppressed()).containsExactly(stopFailure);
        } else {
            assertThat(error).hasRootCause(stopFailure);
        }
        stopping.set(false);
        assertThat(engine.find(id).orElseThrow()).usingRecursiveComparison().isEqualTo(proposal);
        verify(workflow).callOperation(any(), any());
    }

    /**
     * Отказ guard не пишет даже служебные счётчики; отдельность сообщения привязана к подготовке.
     */
    @Test
    void guardDenialPreservesNativeBytesAndPreparationRequest() throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        process(id, SessionEvent.SELECT);
        var before = engine.find(id).orElseThrow();
        byte[] bytes = nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext();
        var input =
                new SessionTurnInDTO(
                        id,
                        proposal.getPreparation().getPreparedRequestId(),
                        SessionEvent.CONFIRM,
                        null,
                        null,
                        null,
                        proposal.getPreparation().getConfirmationSnapshot(),
                        null,
                        false,
                        null);
        clearInvocations(persister);
        assertThatThrownBy(() -> engine.process(input))
                .isInstanceOfSatisfying(
                        SessionPersistenceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ResultCode.INVALID_COMMAND));
        verify(persister, never()).persist(any(), any());
        assertThat(nativeStates.findById(id.toString()).orElseThrow().getStateMachineContext())
                .isEqualTo(bytes);
        assertThat(engine.find(id).orElseThrow()).usingRecursiveComparison().isEqualTo(before);
        verifyNoInteractions(workflow);
    }

    /** Невалидный первый ход не сохраняет каркас; старая пустая строка не получает новый формат. */
    @Test
    void rejectsVirginInvalidInputsAndOldEmptySemaphore() throws Exception {
        UUID id = UUID.randomUUID();
        engine.ensureSemaphore(id);
        for (var command : SessionEvent.values()) {
            assertThatThrownBy(() -> process(id, command))
                    .isInstanceOf(SessionPersistenceException.class);
            assertThat(nativeStates.existsById(id.toString())).isFalse();
            assertThat(engine.find(id)).isEmpty();
        }
        var row = sessions.findById(id).orElseThrow();
        row.setFormatId("status-v2");
        sessions.saveAndFlush(row);
        clearInvocations(persister);
        assertThatThrownBy(() -> prepare(id))
                .isInstanceOfSatisfying(
                        SessionPersistenceException.class,
                        error ->
                                assertThat(error.getCode())
                                        .isEqualTo(ResultCode.STORAGE_UNAVAILABLE));
        verify(persister, never()).restore(any(), any());
        verify(persister, never()).persist(any(), any());
        assertThat(sessions.findById(id).orElseThrow().getFormatId()).isEqualTo("status-v2");
        verifyNoInteractions(workflow);
    }

    /**
     * Большой предметный снимок сохраняется штатным LOB persister без обрезания. Повреждённый
     * binary checkpoint затем отклоняется, а не заменяется новой машиной.
     */
    @Test
    void preservesLargeNativeContextAndRejectsBrokenBinary() {
        UUID id = UUID.randomUUID();
        engine.ensureSemaphore(id);
        String recipient = "Получатель".repeat(10000);
        engine.process(
                new SessionTurnInDTO(
                        id,
                        UUID.randomUUID(),
                        SessionEvent.SELECT,
                        new PaymentContextInDTO(
                                "org",
                                "user",
                                "payment",
                                "42",
                                LocalDate.of(2026, 10, 3),
                                new BigDecimal("12500.00"),
                                "RUB",
                                recipient,
                                "Организация"),
                        null,
                        Operation.STATUS,
                        java.util.List.of(),
                        null,
                        false,
                        null));
        assertThat(engine.find(id).orElseThrow().getPreparation().getContext().getRecipientName())
                .isEqualTo(recipient);
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            var nativeState = nativeStates.findById(id.toString()).orElseThrow();
                            assertThat(nativeState.getStateMachineContext().length)
                                    .isGreaterThan(65536);
                            nativeState.setStateMachineContext(new byte[] {1, 2, 3});
                            nativeStates.save(nativeState);
                        });
        assertThatThrownBy(() -> engine.find(id)).isInstanceOf(SessionPersistenceException.class);
        assertThatThrownBy(() -> process(id, SessionEvent.SELECT))
                .isInstanceOf(SessionPersistenceException.class);
        assertThat(nativeStates.existsById(id.toString())).isTrue();
        verifyNoInteractions(workflow);
    }

    /**
     * Подмена платежа в восстановленном черновике отклоняет подтверждение до вызова менеджера, даже
     * если исходный контекст самой сессии сохранился без изменений.
     */
    @Test
    void rejectsCorruptPreparation() throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        doAnswer(
                        call -> {
                            Object restored = call.callRealMethod();
                            StateMachine<SessionState, SessionEvent> machine = call.getArgument(0);
                            var preparation =
                                    (ru.sberbank.pprb.agent.model.dto.payment.PreparationDTO)
                                            machine.getExtendedState()
                                                    .getVariables()
                                                    .get("preparation");
                            preparation.getContext().setPaymentId("another-payment");
                            return restored;
                        })
                .when(persister)
                .restore(any(), any());
        assertThatThrownBy(() -> confirm(id, proposal))
                .isInstanceOf(SessionPersistenceException.class);
        verifyNoInteractions(workflow);
    }

    /**
     * Повторный выбор на подтверждении отклоняется, а отдельное согласие вызывает менеджер один раз
     * и фиксирует прикладную запись. Сохраняется тот же экземпляр машины, который был восстановлен;
     * чтение и команда завершённой сессии вызов не повторяют.
     */
    @Test
    void commitsActionAndRestoresWithoutRepeatingEffect() throws Exception {
        UUID id = UUID.randomUUID();
        SessionSnapshotDTO proposal = prepare(id);
        UUID operationId = proposal.getPreparation().getPreparationId();
        SessionSnapshotDTO rejected = process(id, SessionEvent.SELECT);
        assertThat(rejected.getLastResult()).isEqualTo(TurnOutcome.INVALID_COMMAND);
        assertThat(rejected.getState()).isEqualTo(SessionState.AWAITING_CONFIRM);
        assertThat(rejected.getPreparation())
                .usingRecursiveComparison()
                .isEqualTo(proposal.getPreparation());
        verifyNoInteractions(workflow);
        UUID applicationRow = UUID.randomUUID();
        doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            saveApplicationRow(applicationRow);
                            return null;
                        })
                .when(workflow)
                .callOperation(any(), any());

        clearInvocations(persister);
        SessionSnapshotDTO completed = confirm(id, proposal);
        assertThat(completed.getState()).isEqualTo(SessionState.COMPLETED);
        assertThat(sessions.existsById(applicationRow)).isTrue();
        var restoredMachine = org.mockito.ArgumentCaptor.forClass(StateMachine.class);
        var savedMachine = org.mockito.ArgumentCaptor.forClass(StateMachine.class);
        verify(persister).restore(restoredMachine.capture(), eq(id.toString()));
        verify(persister).persist(savedMachine.capture(), eq(id.toString()));
        assertThat(savedMachine.getValue()).isSameAs(restoredMachine.getValue());
        assertThat(engine.find(id).orElseThrow().getPreparation().getConfirmedRequestId())
                .isNotNull();
        assertThat(confirm(id, proposal).getLastResult()).isEqualTo(TurnOutcome.SESSION_COMPLETED);
        verify(workflow)
                .callOperation(
                        eq(Operation.STATUS),
                        argThat(
                                context ->
                                        context.getOperationId().equals(operationId)
                                                && context.getPaymentId().equals("payment")
                                                && context.getChanges() == null));
    }

    /**
     * Ошибка менеджера после записи в БД откатывает и эту запись, и согласие в состоянии машины.
     * Восстановленная сессия остаётся на прежнем шаге и с прежней версией представления.
     */
    @Test
    void rollsBackActionAndNativeState() {
        UUID id = UUID.randomUUID();
        SessionSnapshotDTO proposal = prepare(id);
        UUID applicationRow = UUID.randomUUID();
        doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            saveApplicationRow(applicationRow);
                            throw new IllegalStateException("action failure");
                        })
                .when(workflow)
                .callOperation(any(), any());
        assertThatThrownBy(() -> confirm(id, proposal))
                .isInstanceOf(SessionPersistenceException.class);
        assertThat(sessions.existsById(applicationRow)).isFalse();
        SessionSnapshotDTO restored = engine.find(id).orElseThrow();
        assertThat(restored.getState()).isEqualTo(SessionState.AWAITING_CONFIRM);
        assertThat(restored.getProjectionVersion()).isEqualTo(proposal.getProjectionVersion());
        assertThat(restored.getPreparation().getConfirmedRequestId()).isNull();
        verify(workflow).callOperation(any(), any());
    }

    /**
     * Сбой первого сохранения оставляет постоянную строку блокировки, но не создаёт машину.
     * Подтверждение без подготовки отклоняется, а следующий полный запрос успешно создаёт сессию.
     */
    @Test
    void recoversInitializationAfterRollback() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("persist failed"))
                .doCallRealMethod()
                .when(persister)
                .persist(any(), any());
        assertThatThrownBy(() -> prepare(id)).isInstanceOf(SessionPersistenceException.class);
        assertThat(sessions.existsById(id)).isTrue();
        assertThat(engine.find(id)).isEmpty();
        assertThatThrownBy(() -> process(id, SessionEvent.CONFIRM))
                .isInstanceOf(SessionPersistenceException.class);
        assertThat(prepare(id).getState()).isEqualTo(SessionState.AWAITING_CONFIRM);
        verifyNoInteractions(workflow);
    }

    /**
     * Сбой сохранения после вызова менеджера откатывает локальную запись и переход машины. Счётчик
     * внешнего эффекта остаётся равным единице: локальный откат не отменяет и не повторяет уже
     * выполненный вызов.
     */
    @Test
    void persistenceFailureDoesNotRetryExternalEffect() throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        UUID applicationRow = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();
        doAnswer(
                        call -> {
                            effects.incrementAndGet();
                            saveApplicationRow(applicationRow);
                            return null;
                        })
                .when(workflow)
                .callOperation(any(), any());
        doThrow(new IllegalStateException("save failed")).when(persister).persist(any(), any());
        assertThatThrownBy(() -> confirm(id, proposal))
                .isInstanceOf(SessionPersistenceException.class);
        assertThat(effects).hasValue(1);
        assertThat(sessions.existsById(applicationRow)).isFalse();
        assertThat(engine.find(id).orElseThrow().getState())
                .isEqualTo(SessionState.AWAITING_CONFIRM);
        verify(workflow).callOperation(any(), any());
    }

    /**
     * Несовместимый идентификатор формата отклоняет и обработку сообщения, и чтение сессии до
     * десериализации. Восстановление и внешний менеджер при этом не вызываются.
     */
    @Test
    void rejectsFormatBeforeDeserialization() throws Exception {
        UUID id = UUID.randomUUID();
        var proposal = prepare(id);
        SessionEntity row = sessions.findById(id).orElseThrow();
        row.setFormatId("status-v2");
        sessions.saveAndFlush(row);
        clearInvocations(persister);
        assertThatThrownBy(() -> process(id, SessionEvent.SELECT))
                .isInstanceOf(SessionPersistenceException.class);
        assertThatThrownBy(() -> engine.find(id)).isInstanceOf(SessionPersistenceException.class);
        verify(persister, never()).restore(any(), any());
        verifyNoInteractions(workflow);
    }

    /**
     * Создаёт строку блокировки и выполняет первый запрос с полным набором тестовых данных платежа.
     */
    SessionSnapshotDTO prepare(UUID id) {
        engine.ensureSemaphore(id);
        return engine.process(
                new SessionTurnInDTO(
                        id,
                        UUID.randomUUID(),
                        SessionEvent.SELECT,
                        new PaymentContextInDTO(
                                "org",
                                "user",
                                "payment",
                                "42",
                                LocalDate.of(2026, 10, 3),
                                new BigDecimal("12500.00"),
                                "RUB",
                                "Получатель",
                                "Организация"),
                        null,
                        Operation.STATUS,
                        java.util.List.of(),
                        null,
                        false,
                        null));
    }

    /**
     * Отправляет следующую команду с новым Request-Id, используя ранее сохранённые данные платежа.
     */
    SessionSnapshotDTO process(UUID id, SessionEvent command) {
        engine.ensureSemaphore(id);
        return engine.process(
                new SessionTurnInDTO(
                        id,
                        UUID.randomUUID(),
                        command,
                        null,
                        null,
                        command == SessionEvent.SELECT ? Operation.STATUS : null,
                        java.util.List.of(),
                        null,
                        false,
                        null));
    }

    /** Согласие явно возвращает снимок конкретного предложения, без чтения актуальной БД. */
    SessionSnapshotDTO confirm(UUID id, SessionSnapshotDTO proposal) {
        return engine.process(
                new SessionTurnInDTO(
                        id,
                        UUID.randomUUID(),
                        SessionEvent.CONFIRM,
                        null,
                        null,
                        null,
                        proposal.getPreparation().getConfirmationSnapshot(),
                        null,
                        false,
                        null));
    }

    /**
     * Создаёт тестовую прикладную запись через настоящий JPA-репозиторий. Её наличие после успеха
     * или отсутствие после ошибки показывает, что действие использует транзакцию машины.
     */
    void saveApplicationRow(UUID id) {
        SessionEntity row = new SessionEntity();
        row.setSessionId(id);
        row.setFormatId(SessionSemaphore.FORMAT_ID);
        sessions.saveAndFlush(row);
    }
}
