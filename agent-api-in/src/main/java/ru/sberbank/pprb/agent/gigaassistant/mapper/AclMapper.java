package ru.sberbank.pprb.agent.gigaassistant.mapper;

import java.time.DateTimeException;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openapitools.jackson.nullable.JsonNullable;
import ru.sberbank.pprb.agent.common.acl.AclProfile;
import ru.sberbank.pprb.agent.common.mapper.MappingConfig;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclAdditionalInfo;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclCustomerInfo;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclInputMessage;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclMetadata;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclOrganization;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclOutputAdditionalInfo;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclOutputContent;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclOutputMessage;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclOutputMetadata;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclRequest;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclResponse;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclState;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclSuggestion;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.payment.ConfirmationValueDTO;
import ru.sberbank.pprb.agent.model.dto.payment.PaymentContextInDTO;
import ru.sberbank.pprb.agent.model.dto.turn.ResultDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnSuggestionDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.model.enums.Operation;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.ResultKind;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;

/**
 * Переносит проверенные ACL-данные в общий вход, а подготовленный оркестратором результат — в
 * адресованный ACL-ответ. Текст ответа и поля внешнего протокола не сохраняются в машине состояний.
 */
@Mapper(config = MappingConfig.class, imports = ResultKind.class)
public interface AclMapper {

    /**
     * Возвращает новый внутренний вход целиком: идентификаторы, намерение, реквизиты либо ошибку
     * формы. Метод не принимает изменяемый DTO и не меняет объекты ACL, переданные вызывающим
     * кодом.
     */
    default SessionTurnInDTO toInput(
            String requestHeader,
            String sessionHeader,
            AclRequest request,
            ResultDTO envelopeError,
            Operation operation) {
        UUID sessionId = correlationId(sessionHeader);
        UUID requestId = correlationId(requestHeader);
        if (envelopeError != null)
            return toInputFields(sessionId, requestId, null, null, envelopeError, null, List.of());
        try {
            PaymentContextInDTO context = toContext(contextValues(request.getMetadata()));
            List<ConfirmationValueDTO> confirmation = confirmationValues(request.getState());
            SessionEvent event = event(request.getMessage(), operation);
            NavigationAction navigation =
                    !"request".equals(request.getMessage().getPerformative())
                                    || request.getMessage().getContent() == null
                            ? null
                            : NavigationAction.resolve(
                                            request.getMessage().getContent().getActionCode())
                                    .orElse(null);
            String text =
                    request.getMessage().getContent() == null
                            ? null
                            : request.getMessage().getContent().getUserInput();
            if (text != null && text.isBlank()) text = null;
            boolean control = event == SessionEvent.CONFIRM || event == SessionEvent.CANCEL;
            boolean explicitCode =
                    request.getMessage().getContent() != null
                            && request.getMessage().getContent().getActionCode() != null;
            boolean conflict =
                    (control ? 1 : 0) + (explicitCode ? 1 : 0) + (text != null ? 1 : 0) > 1;
            ResultDTO error =
                    event == null
                                    && navigation == null
                                    && (!"request".equals(request.getMessage().getPerformative())
                                            || (!conflict && (text == null || explicitCode)))
                            ? new ResultDTO(
                                    ResultKind.ERROR,
                                    ResultCode.UNSUPPORTED_COMMAND,
                                    "Действие не поддерживается в этом этапе")
                            : null;
            return toInputFields(
                            sessionId,
                            requestId,
                            event,
                            context,
                            error,
                            event == SessionEvent.SELECT ? operation : null,
                            confirmation)
                    .toBuilder()
                    .userInput(text)
                    .navigationAction(navigation)
                    .inputSourceConflict(conflict)
                    .build();
        } catch (DateTimeException | NumberFormatException exception) {
            return toInputFields(
                    sessionId,
                    requestId,
                    null,
                    null,
                    invalid("Дата или сумма платежа имеет неверный формат"),
                    null,
                    List.of());
        } catch (IllegalArgumentException exception) {
            return toInputFields(
                    sessionId,
                    requestId,
                    null,
                    null,
                    invalid(exception.getMessage()),
                    null,
                    List.of());
        }
    }

    /** Собирает прикладной вход из структурных значений без потери дефектов подтверждения. */
    @Mapping(target = "userInput", ignore = true)
    @Mapping(target = "inputSourceConflict", ignore = true)
    @Mapping(target = "navigationAction", ignore = true)
    SessionTurnInDTO toInputFields(
            UUID sessionId,
            UUID requestId,
            SessionEvent event,
            PaymentContextInDTO context,
            ResultDTO validationError,
            Operation operation,
            List<ConfirmationValueDTO> confirmation);

    /** Иные валидные типы state не являются согласуемыми реквизитами. */
    default List<ConfirmationValueDTO> confirmationValues(List<AclState> state) {
        List<ConfirmationValueDTO> values = new ArrayList<ConfirmationValueDTO>();
        if (state == null) return values;
        for (AclState parameter : state) {
            if (parameter == null
                    || parameter.getType() == null
                    || !List.of("CONFIRMATION", "MEMORY", "REDIRECTION", "OPERATION")
                            .contains(parameter.getType())) {
                throw new IllegalArgumentException("Некорректный тип параметра state");
            }
            if ("CONFIRMATION".equals(parameter.getType())) {
                values.add(new ConfirmationValueDTO(parameter.getKey(), parameter.getValue()));
            }
        }
        return values;
    }

    /**
     * Преобразует только полную запись UUID; сокращённые значения UUID.fromString не допускаются.
     * Mapper сообщает ошибку данных, а адаптер выбирает для неё HTTP-ответ.
     */
    default UUID correlationId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Некорректный UUID сообщения или сессии", exception);
        }
    }

    /**
     * Преобразует только переданные реквизиты, включая ISO-дату и десятичную сумму; пропущенные
     * поля остаются null.
     */
    @Mapping(target = "currency", source = AclProfile.CURRENCY)
    @Mapping(target = "recipientName", source = AclProfile.RECIPIENT_NAME)
    @Mapping(target = "digitalUserId", source = AclProfile.DIGITAL_USER_ID)
    @Mapping(target = "paymentDate", source = AclProfile.PAYMENT_DATE)
    @Mapping(target = "paymentId", source = AclProfile.PAYMENT_ID)
    @Mapping(target = "paymentNumber", source = AclProfile.PAYMENT_NUMBER)
    @Mapping(target = "epkId", source = AclProfile.EPK_ID)
    @Mapping(target = "organizationName", source = AclProfile.ORGANIZATION_NAME)
    @Mapping(target = "amount", source = AclProfile.AMOUNT)
    PaymentContextInDTO toContext(Map<String, String> values);

    /**
     * Меняет адресатов местами и связывает ответ с UUID заголовка, даже если reply_with тела был
     * неверным.
     */
    @Mapping(
            target = "message",
            expression = "java(toMessage(input, requestId, output, httpStatus))")
    @Mapping(target = "metadata", expression = "java(toMetadata(output))")
    @Mapping(target = "suggestions", expression = "java(suggestions(output))")
    @Mapping(target = "state", expression = "java(confirmation(output))")
    AclResponse toResponse(
            AclInputMessage input, UUID requestId, TurnOutDTO output, int httpStatus);

    /**
     * Формирует адресованное сообщение; выбранный текст объясняет результат именно текущего
     * пользовательского хода.
     */
    @Mapping(target = "version", constant = AclProfile.VERSION)
    @Mapping(target = "performative", expression = "java(performative(output))")
    @Mapping(target = "sender", source = "input.receiver")
    @Mapping(target = "receiver", source = "input.sender")
    @Mapping(target = "conversationId", source = "input.conversationId")
    @Mapping(target = "inReplyTo", source = "requestId")
    @Mapping(target = "content", expression = "java(toContent(output, httpStatus))")
    AclOutputMessage toMessage(
            AclInputMessage input, UUID requestId, TurnOutDTO output, int httpStatus);

    /**
     * Переносит установленную ядром завершённость в metadata, в том числе для отказа без полной
     * проекции. Преобразование не читает сессию повторно и не выводит состояние из наличия view.
     */
    default AclOutputMetadata toMetadata(TurnOutDTO output) {
        AclOutputMetadata metadata = new AclOutputMetadata().finalMessage(output.isTerminal());
        switch (output.getResult().getCode()) {
            case INPUT_SOURCE_CONFLICT,
                            MULTIPLE_ACTIONS,
                            CLARIFICATION_REQUIRED,
                            TEXT_ANALYSIS_FAILED,
                            REFERENCE_ANSWER,
                            REFERENCE_NOT_FOUND,
                            REFERENCE_FAILED ->
                    metadata.addAdditionalInfoItem(
                            new AclOutputAdditionalInfo()
                                    .key("response_mode")
                                    .value("information"));
            default -> {}
        }
        return metadata;
    }

    /**
     * Размещает текст успешной обработки в result, а текст ошибки — в reason. Ошибка вызова corr
     * или отказ в принятии запроса передаются как ошибка обработки, а не как успешный результат.
     */
    @Mapping(
            target = "result",
            expression =
                    "java(output.getResult().getKind() == ResultKind.SUCCESS ? output.getResult().getMessage() : null)")
    @Mapping(
            target = "reason",
            expression =
                    "java(output.getResult().getKind() == ResultKind.ERROR ? output.getResult().getMessage() : null)")
    @Mapping(target = "statusCode", expression = "java(Integer.toString(httpStatus))")
    @Mapping(target = "confirmationView", source = "output.confirmationView")
    AclOutputContent toContent(TurnOutDTO output, int httpStatus);

    /** Выражает необходимость согласия через ACL, а не через локальные UI-команды. */
    default String performative(TurnOutDTO output) {
        if (output.getResult().getKind() == ResultKind.ERROR) {
            return "failure";
        }
        return output.getResult().getCode() == ResultCode.CONFIRMATION_REQUIRED
                ? "propose"
                : "inform";
    }

    /** Структурное преобразование единого каталога в саджесты ACL. */
    List<AclSuggestion> toSuggestions(List<TurnSuggestionDTO> suggestions);

    @Mapping(target = "guid", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(
            target = "performative",
            expression = "java(suggestionPerformative(suggestion.getKind()))")
    @Mapping(target = "messageText", source = "text")
    @Mapping(target = "displayMode", constant = "BUTTON")
    @Mapping(target = "isPostedToChat", constant = "true")
    AclSuggestion toSuggestion(TurnSuggestionDTO suggestion);

    /** Переводит внутреннее намерение кнопки в локальный ACL-профиль. */
    default AclSuggestion.PerformativeEnum suggestionPerformative(SuggestionKind kind) {
        return switch (kind) {
            case COMMAND -> AclSuggestion.PerformativeEnum.REQUEST;
            case CONFIRM -> AclSuggestion.PerformativeEnum.ACCEPT_PROPOSE;
            case CANCEL -> AclSuggestion.PerformativeEnum.REJECT_PROPOSE;
        };
    }

    default List<AclSuggestion> suggestions(TurnOutDTO output) {
        return !output.isTerminal() ? toSuggestions(output.getSuggestions()) : List.of();
    }

    /** Передаёт уже подготовленные параметры, не извлекая реквизиты из контекста. */
    default List<AclState> confirmation(TurnOutDTO output) {
        if (!"propose".equals(performative(output))) return List.of();
        return output.getConfirmation().stream()
                .map(
                        value ->
                                confirmationField(
                                        value.getKey(), value.getValue(), value.getDescription()))
                .toList();
    }

    /** Структура параметра исходного ACL без собственных полей локального UI. */
    default AclState confirmationField(String key, String value, String description) {
        return new AclState().key(key).value(value).type("CONFIRMATION").description(description);
    }

    /**
     * Переводит транспортное намерение в общий язык сценария, без выбора события по текущему
     * состоянию.
     */
    private SessionEvent event(AclInputMessage message, Operation operation) {
        if ("accept_propose".equals(message.getPerformative())) {
            return SessionEvent.CONFIRM;
        }
        if ("reject_propose".equals(message.getPerformative())) {
            return SessionEvent.CANCEL;
        }
        if (!"request".equals(message.getPerformative()) || message.getContent() == null) {
            return null;
        }
        if (operation != null) {
            return SessionEvent.SELECT;
        }
        return null;
    }

    /**
     * Собирает только явно переданные значения; отсутствие любого поля допустимо для последующего
     * сообщения сессии.
     */
    private Map<String, String> contextValues(JsonNullable<AclMetadata> supplied) {
        Map<String, String> values = new HashMap<>();
        AclMetadata metadata = optionalValue(supplied, "metadata");
        if (metadata == null) {
            return values;
        }
        // Пропуск контейнера отличается от явного null: второе не разрешает использовать
        // сохранённые реквизиты.
        AclOrganization organization = optionalValue(metadata.getOrganization(), "organization");
        AclCustomerInfo customer = optionalValue(metadata.getCustomerInfo(), "customer_info");
        if (organization != null) {
            putOptional(values, AclProfile.EPK_ID, organization.getEpkId());
        }
        if (customer != null) {
            putOptional(values, AclProfile.DIGITAL_USER_ID, customer.getDigitalUserId());
        }
        List<AclAdditionalInfo> parameters =
                optionalValue(metadata.getAdditionalInfo(), "additional_info");
        if (parameters != null) {
            for (AclAdditionalInfo parameter : parameters) {
                addPaymentValue(values, parameter);
            }
        }
        return values;
    }

    /**
     * Добавляет известный параметр ровно один раз; неизвестные ключи не становятся частью
     * бизнес-контекста.
     */
    private void addPaymentValue(Map<String, String> values, AclAdditionalInfo parameter) {
        if (parameter == null) {
            throw new IllegalArgumentException("Параметр платежа не может быть null");
        }
        if (parameter.getKey() == null || !AclProfile.PAYMENT_KEYS.contains(parameter.getKey())) {
            return;
        }
        String value = optionalValue(parameter.getValue(), parameter.getKey());
        if (value == null || value.isBlank() || values.containsKey(parameter.getKey())) {
            throw new IllegalArgumentException(
                    "Параметр платежа пропущен, пуст или передан повторно");
        }
        values.put(parameter.getKey(), value);
    }

    /**
     * Добавляет непустой идентификатор, если поле присутствовало; пропуск не превращает в пустую
     * строку.
     */
    private void putOptional(
            Map<String, String> values, String key, JsonNullable<String> supplied) {
        String value = optionalValue(supplied, key);
        if (value != null) {
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "Идентификатор организации или пользователя не может быть пустым");
            }
            values.put(key, value);
        }
    }

    /**
     * Возвращает null только для отсутствующего JSON-поля; явно переданный null всегда является
     * ошибкой формы.
     */
    private <T> T optionalValue(JsonNullable<T> supplied, String field) {
        if (supplied == null || !supplied.isPresent()) {
            return null;
        }
        if (supplied.get() == null) {
            throw new IllegalArgumentException("Поле " + field + " не может быть null");
        }
        return supplied.get();
    }

    /**
     * Создаёт контролируемую ошибку формы; ядро получает её вместо некорректных реквизитов или
     * команды.
     */
    private ResultDTO invalid(String message) {
        return new ResultDTO(ResultKind.ERROR, ResultCode.INVALID_REQUEST, message);
    }
}
