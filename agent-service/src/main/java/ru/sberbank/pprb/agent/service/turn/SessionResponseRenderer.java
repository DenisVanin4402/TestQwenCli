package ru.sberbank.pprb.agent.service.turn;

import java.text.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.session.*;
import ru.sberbank.pprb.agent.model.dto.turn.ResultDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;

/** Формирует текст и параметры из сохранённого предложения после commit, без чтения БД. */
@Component
public class SessionResponseRenderer {
    private static final String DEFAULT_ERROR = "error.DEFAULT";
    private final ResourceBundle messages;
    private final OperationCatalog operations;
    private final DateTimeFormatter paymentDate;
    private final Map<TurnOutcome, String> templates;
    private final Map<Operation, String> proposals;

    @Autowired
    /** Загружает и проверяет шаблоны исходов и операций до приёма запросов. */
    public SessionResponseRenderer(
            OperationCatalog operations,
            @Value("${poc.responses.catalog}") String catalog,
            @Value("${poc.responses.payment-date-format}") String dateFormat) {
        this(operations, ResourceBundle.getBundle(catalog, Locale.ROOT), dateFormat);
    }

    /** Загружает и проверяет шаблоны исходов и операций до приёма запросов. */
    SessionResponseRenderer(
            OperationCatalog operations, ResourceBundle messages, String dateFormat) {
        this.operations = operations;
        this.messages = messages;
        paymentDate = DateTimeFormatter.ofPattern(dateFormat, Locale.ROOT);
        /** Требует непустой текст каталога, включая общий технический отказ. */
        requireText(DEFAULT_ERROR);
        requireText("text.clarification");
        requireText("error.TEXT_ANALYSIS_FAILED");
        Map<TurnOutcome, String> outcomes = new EnumMap<TurnOutcome, String>(TurnOutcome.class);
        for (TurnOutcome outcome : TurnOutcome.values()) {
            outcomes.put(
                    outcome,
                    template(
                            outcome.name() + ".message",
                            outcome == TurnOutcome.REQUEST_SUBMITTED
                                            || outcome == TurnOutcome.CONFIRMATION_REQUIRED
                                    ? 1
                                    : 0));
        }
        templates = Map.copyOf(outcomes);
        Map<Operation, String> prepared = new EnumMap<Operation, String>(Operation.class);
        for (OperationSpecDTO operation : operations.choices(SessionState.CHOOSING_REQUEST_TYPE)) {
            prepared.put(
                    operation.getOperation(),
                    template(
                            operation.getProposalTemplateKey(),
                            operation.getConfirmationFields().size()));
        }
        proposals = Map.copyOf(prepared);
    }

    /**
     * Готовит текст, параметры подтверждения и саджесты из зафиксированного снимка; не изменяет его
     * и не читает БД.
     */
    public TurnOutDTO render(SessionSnapshotDTO snapshot) {
        PreparationDTO preparation = snapshot.getPreparation();
        List<ConfirmationParameterDTO> confirmation = List.of();
        String summary = "";
        String title = "";
        if (preparation != null) {
            OperationSpecDTO spec =
                    operations.operationSpec(preparation.getOperation()).orElseThrow();
            title = spec.getTitle();
            Map<String, String> values =
                    preparation.getConfirmationSnapshot().stream()
                            .collect(
                                    Collectors.toMap(
                                            ConfirmationValueDTO::getKey,
                                            ConfirmationValueDTO::getValue));
            Object[] parameters =
                    spec.getConfirmationFields().stream()
                            .map(field -> display(values.get(field.getKey()), field.getValueType()))
                            .toArray();
            summary = format(proposals.get(spec.getOperation()), parameters);
            Map<String, String> descriptions =
                    spec.getConfirmationFields().stream()
                            .collect(
                                    Collectors.toMap(
                                            ConfirmationFieldDTO::getKey,
                                            ConfirmationFieldDTO::getDescription));
            descriptions.put(ConfirmationValueDTO.PREPARATION_NO, "Номер подготовки");
            confirmation =
                    preparation.getConfirmationSnapshot().stream()
                            .map(
                                    value ->
                                            new ConfirmationParameterDTO(
                                                    value.getKey(),
                                                    value.getValue(),
                                                    descriptions.get(value.getKey())))
                            .toList();
        }
        TurnOutcome outcome = snapshot.getLastResult();
        String text =
                outcome == TurnOutcome.CONFIRMATION_REQUIRED
                        ? summary
                        : format(templates.get(outcome), title);
        ResultKind kind =
                outcome == TurnOutcome.SESSION_COMPLETED || outcome == TurnOutcome.INVALID_COMMAND
                        ? ResultKind.ERROR
                        : ResultKind.SUCCESS;
        return TurnOutDTO.builder()
                .requestId(snapshot.getLastRequestId())
                .terminal(snapshot.getState() == SessionState.COMPLETED)
                .confirmation(confirmation)
                .availableOperations(operations.choices(snapshot.getState()))
                .result(new ResultDTO(kind, outcome.getCode(), text))
                .build();
    }

    /**
     * Строит типизированный отказ без восстановления сессии и без обещания отмены внешнего эффекта.
     */
    public ResultDTO failure(ResultCode code) {
        String key = "error." + code.name();
        return new ResultDTO(
                ResultKind.ERROR,
                code,
                messages.getString(messages.containsKey(key) ? key : DEFAULT_ERROR));
    }

    /** Непонятая команда получает ответ без чтения, создания или изменения FSM. */
    public TurnOutDTO clarification(UUID requestId) {
        return TurnOutDTO.builder()
                .requestId(requestId)
                .result(
                        new ResultDTO(
                                ResultKind.SUCCESS,
                                ResultCode.CLARIFICATION_REQUIRED,
                                messages.getString("text.clarification")))
                .build();
    }

    /** Форматирует дату только для показа; wire сохраняет исходное ISO-значение. */
    private String display(String value, ConfirmationValueType type) {
        return type == ConfirmationValueType.DATE
                ? paymentDate.format(LocalDate.parse(value))
                : value;
    }

    /** Требует непустой текст каталога, включая общий технический отказ. */
    private String requireText(String key) {
        String text = messages.getString(key);
        if (text.isBlank()) throw new IllegalArgumentException("Отсутствует текст " + key);
        return text;
    }

    /** Проверяет строковые аргументы шаблона относительно состава схемы операции. */
    private String template(String key, int count) {
        String text = requireText(key);
        MessageFormat format = new MessageFormat(text, Locale.ROOT);
        if (format.getFormatsByArgumentIndex().length > count
                || Arrays.stream(format.getFormats()).anyMatch(Objects::nonNull)) {
            throw new IllegalArgumentException("Неверные аргументы шаблона " + key);
        }
        return text;
    }

    /**
     * Создаёт отдельный MessageFormat, чтобы параллельные запросы не разделяли изменяемый
     * formatter.
     */
    private String format(String template, Object... parameters) {
        return new MessageFormat(template, Locale.ROOT).format(parameters);
    }
}
