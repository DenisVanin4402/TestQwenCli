package ru.sberbank.pprb.agent.service.turn;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.pprb.agent.common.idempotency.Idempotent;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.MessageAnalysisDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;
import ru.sberbank.pprb.agent.service.fsm.SessionExecutionService;
import ru.sberbank.pprb.agent.service.fsm.SessionPersistenceException;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;
import ru.sberbank.pprb.agent.service.port.in.SessionQueryException;
import ru.sberbank.pprb.agent.service.port.in.SessionQueryUseCase;
import ru.sberbank.pprb.agent.service.port.in.SessionTurnUseCase;
import ru.sberbank.pprb.agent.service.port.out.TextAnalysisException;
import ru.sberbank.pprb.agent.service.port.out.TextMessageClassifier;

/**
 * Связывает входной API с обработкой сообщения машиной состояний и подготавливает содержание
 * ответа. Строка блокировки создаётся до рабочей транзакции, а тексты формируются после её
 * завершения по возвращённому снимку, без повторного чтения изменяемой сессии.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionTurnOrchestrator implements SessionTurnUseCase, SessionQueryUseCase {
    /** Возвращает предметный результат только после завершения рабочей транзакции. */
    private final SessionExecutionService fsm;

    /** Заполняет локальные шаблоны по предметному снимку после завершения транзакции. */
    private final SessionResponseRenderer renderer;

    /** Классифицирует текст до любого обращения к исполнителю/хранилищу сессии. */
    private final TextMessageClassifier classifier;

    /** Проверяет код модели по тому же каталогу, что используется саджестами. */
    private final OperationCatalog operations;

    /**
     * Обрабатывает проверенный вход через движок и готовит ответ после завершения транзакции. Ключ
     * передаётся отдельной строкой для будущей библиотеки идемпотентности; пока она не подключена,
     * аннотация не возвращает сохранённый ответ на повтор и не сверяет его содержимое.
     */
    @Override
    @Idempotent(
            signature = "SessionTurnOrchestrator.handle",
            needCheckHash = true,
            keyParameterIndex = 1)
    public TurnOutDTO handle(SessionTurnInDTO input, String idempotencyKey) {
        if (input.getValidationError() != null) {
            return TurnOutDTO.builder()
                    .requestId(input.getRequestId())
                    .result(input.getValidationError())
                    .build();
        }
        if (input.getEvent() == null) {
            if (input.getUserInput() == null || input.getUserInput().isBlank()) {
                return failure(input, ResultCode.INVALID_REQUEST, false);
            }
            try {
                MessageAnalysisDTO analysis =
                        classifier.classify(
                                input.getUserInput(), operations.registeredOperations());
                if (analysis == null) throw new TextAnalysisException("Пустой результат разбора");
                if (analysis.getActionCode() == null)
                    return renderer.clarification(input.getRequestId());
                OperationSpecDTO operation =
                        operations
                                .resolve(analysis.getActionCode())
                                .orElseThrow(
                                        () ->
                                                new TextAnalysisException(
                                                        "Неизвестный код операции"));
                input =
                        input.toBuilder()
                                .event(SessionEvent.SELECT)
                                .operation(operation.getOperation())
                                .userInput(null)
                                .confirmation(java.util.List.of())
                                .build();
            } catch (TextAnalysisException exception) {
                log.warn("Text analysis failed: session={}", input.getSessionId());
                return failure(input, ResultCode.TEXT_ANALYSIS_FAILED, false);
            }
        }
        SessionSnapshotDTO snapshot;
        try {
            fsm.ensureSemaphore(input.getSessionId());
            snapshot = fsm.process(input);
        } catch (SessionPersistenceException exception) {
            log.warn(
                    "Session execution failed: session={}, code={}",
                    input.getSessionId(),
                    exception.getCode(),
                    exception);
            return failure(input, exception.getCode(), exception.isTerminal());
        } catch (RuntimeException exception) {
            log.error("Session transaction failed: session={}", input.getSessionId(), exception);
            return failure(input, ResultCode.STORAGE_UNAVAILABLE, false);
        }
        // Состояние уже зафиксировано. Ошибка формирования ответа не должна повторно запускать
        // действие машины и внешний вызов, поэтому обработка ответа находится за границей try.
        return renderer.render(snapshot);
    }

    /**
     * Получает согласованный предметный снимок существующей сессии после завершения чтения.
     * Отсутствующая сессия не создаётся, а предметные действия при этом запросе не выполняются.
     */
    @Override
    public Optional<SessionSnapshotDTO> find(UUID sessionId) {
        try {
            return fsm.find(sessionId);
        } catch (SessionPersistenceException exception) {
            throw new SessionQueryException(exception.getCode(), exception);
        } catch (RuntimeException exception) {
            throw new SessionQueryException(ResultCode.STORAGE_UNAVAILABLE, exception);
        }
    }

    /**
     * Готовит ответ после неуспешной обработки без повторного восстановления занятой или
     * повреждённой сессии. Техническая ошибка не доказывает отсутствие внешнего эффекта, поэтому
     * текст не обещает, что банковский сервис не успел выполнить операцию.
     */
    private TurnOutDTO failure(SessionTurnInDTO input, ResultCode code, boolean terminal) {
        return TurnOutDTO.builder()
                .requestId(input.getRequestId())
                .result(renderer.failure(code))
                .terminal(terminal)
                .build();
    }
}
