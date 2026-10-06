package ru.sberbank.pprb.agent.gigaassistant.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sberbank.pprb.agent.common.acl.AclProfile;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclInputMessage;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclRequest;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclResponse;
import ru.sberbank.pprb.agent.gigaassistant.mapper.AclMapper;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.turn.ResultDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.ResultKind;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;
import ru.sberbank.pprb.agent.service.port.in.SessionTurnUseCase;

/**
 * Проверяет транспортные требования ACL, вызывает общий сценарий и выбирает HTTP-статус ответа.
 * Сборка внутреннего входа и внешних DTO принадлежит mapper; допустимость перехода определяет SSM.
 */
@Component
@Profile("poc-local")
public class AclInputAdapter {
    /** Общий сценарий, возвращающий подготовленный ответ после завершения рабочей транзакции. */
    private final SessionTurnUseCase useCase;

    /** Преобразует заголовки и ACL-сообщение во внутренний вход, а результат — во внешний ответ. */
    private final AclMapper mapper;

    private final OperationCatalog operations;

    /** Настроенный адресат, который должен совпадать с маршрутом и receiver сообщения. */
    private final String agentCode;

    /** Подключает общий сценарий, mapping и код агента из конфигурации приложения. */
    public AclInputAdapter(
            SessionTurnUseCase useCase,
            AclMapper mapper,
            OperationCatalog operations,
            @Value("${poc.acl.agent-code}") String agentCode) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.operations = operations;
        this.agentCode = agentCode;
    }

    /**
     * Возвращает адресованный ACL-ответ после обработки нормализованного входа. Если корреляцию
     * восстановить нельзя, отвечает транспортной ошибкой без выдуманного UUID.
     */
    public ResponseEntity<AclResponse> handle(
            String routeAgent, String requestHeader, String sessionHeader, AclRequest request) {
        if (!agentCode.equals(routeAgent)) {
            return ResponseEntity.notFound().build();
        }
        SessionTurnInDTO input;
        try {
            ResultDTO envelopeError = validateEnvelope(request, requestHeader);
            AclInputMessage message = request.getMessage();
            Operation operation =
                    "request".equals(message.getPerformative()) && message.getContent() != null
                            ? operations
                                    .resolve(message.getContent().getActionCode())
                                    .map(spec -> spec.getOperation())
                                    .orElse(null)
                            : null;
            input = mapper.toInput(requestHeader, sessionHeader, request, envelopeError, operation);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        AclInputMessage message = request.getMessage();
        String idempotencyKey = input.getSessionId() + ":" + input.getRequestId();
        TurnOutDTO output = useCase.handle(input, idempotencyKey);
        int httpStatus = status(output.getResult());
        return ResponseEntity.status(httpStatus)
                .body(mapper.toResponse(message, input.getRequestId(), output, httpStatus));
    }

    /**
     * Возвращает ошибку версии, адресата или связи reply_with с заголовком; null означает
     * допустимую форму. Если данных для адресованного ответа нет, бросает исключение. Переданное
     * сообщение не изменяет.
     */
    private ResultDTO validateEnvelope(AclRequest request, String requestHeader) {
        if (request == null || request.getMessage() == null) {
            throw new IllegalArgumentException("Отсутствует сообщение ACL");
        }
        AclInputMessage message = request.getMessage();
        if (message.getSender() == null
                || message.getSender().isBlank()
                || message.getReceiver() == null
                || message.getReceiver().isBlank()) {
            throw new IllegalArgumentException("Не указаны адресаты ACL");
        }
        mapper.correlationId(message.getConversationId());
        mapper.correlationId(message.getReplyWith());
        if (!AclProfile.VERSION.equals(message.getVersion())
                || !agentCode.equals(message.getReceiver())
                || !message.getReplyWith().equalsIgnoreCase(requestHeader)) {
            return new ResultDTO(
                    ResultKind.ERROR,
                    ResultCode.INVALID_REQUEST,
                    "Проверьте версию, получателя и совпадение Request-Id с reply_with");
        }
        return null;
    }

    /**
     * Выбирает HTTP-код только на границе канала; внутренний результат не содержит HTTP-статуса.
     */
    private int status(ResultDTO result) {
        if (result.getKind() == ResultKind.SUCCESS) {
            return 200;
        }
        return switch (result.getCode()) {
            case OPERATION_FAILED, STORAGE_UNAVAILABLE, TEXT_ANALYSIS_FAILED -> 500;
            case SESSION_NOT_FOUND -> 404;
            case INVALID_REQUEST,
                            INVALID_CONFIRMATION,
                            STALE_PREPARATION,
                            CONFIRMATION_MISMATCH,
                            INVALID_COMMAND,
                            UNSUPPORTED_COMMAND,
                            CONTEXT_MISMATCH,
                            SESSION_BUSY,
                            SESSION_COMPLETED ->
                    400;
            case CLARIFICATION_REQUIRED,
                            CONFIRMATION_REQUIRED,
                            PREPARATION_CANCELLED,
                            NO_ACTIVE_PREPARATION,
                            REQUEST_SUBMITTED ->
                    200;
        };
    }
}
