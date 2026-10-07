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
import ru.sberbank.pprb.agent.model.dto.turn.ConfirmationDisplayFieldDTO;
import ru.sberbank.pprb.agent.model.dto.turn.ConfirmationViewDTO;
import ru.sberbank.pprb.agent.model.dto.turn.ReferenceAnswerDTO;
import ru.sberbank.pprb.agent.model.dto.turn.ResultDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnSuggestionDTO;
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
    private final String activeInvalidCommand;

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
        activeInvalidCommand = template("invalid-command.active", 0);
        for (String key :
                List.of(
                        "status.title",
                        "status.payment-number",
                        "status.organization",
                        "status.payment-date",
                        "status.recipient")) {
            requireText(key);
        }
        /** Требует непустой текст каталога, включая общий технический отказ. */
        requireText(DEFAULT_ERROR);
        requireText("text.clarification");
        requireText("error.TEXT_ANALYSIS_FAILED");
        requireText("text.input-source-conflict");
        requireText("text.multiple-actions");
        requireText("text.reference-not-found");
        requireText("error.REFERENCE_FAILED");
        for (String key :
                List.of(
                        "navigation.question",
                        "navigation.choice",
                        "navigation.missing",
                        "button.resume",
                        "button.reset",
                        "button.confirm",
                        "button.cancel")) {
            requireText(key);
        }
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
                            operation.getOperation() == Operation.STATUS
                                    ? 0
                                    : operation.getConfirmationFields().size()));
        }
        proposals = Map.copyOf(prepared);
    }

    /**
     * Готовит текст, параметры подтверждения и саджесты из зафиксированного снимка; не изменяет его
     * и не читает БД.
     */
    public TurnOutDTO render(SessionSnapshotDTO snapshot) {
        return renderOutcome(snapshot, snapshot.getLastResult(), snapshot.getLastRequestId());
    }

    private TurnOutDTO renderOutcome(
            SessionSnapshotDTO snapshot, TurnOutcome outcome, UUID requestId) {
        PreparationDTO preparation = snapshot.getPreparation();
        List<ConfirmationParameterDTO> confirmation = List.of();
        String summary = "";
        String title = "";
        ConfirmationViewDTO view = null;
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
            if (outcome == TurnOutcome.CONFIRMATION_REQUIRED
                    && spec.getOperation() == Operation.STATUS) {
                TrustedPaymentContextDTO context = preparation.getContext();
                view =
                        new ConfirmationViewDTO(
                                messages.getString("status.title"),
                                List.of(
                                        new ConfirmationDisplayFieldDTO(
                                                messages.getString("status.payment-number"),
                                                values.get("paymentNumber")),
                                        new ConfirmationDisplayFieldDTO(
                                                messages.getString("status.organization"),
                                                context.getOrganizationName()),
                                        new ConfirmationDisplayFieldDTO(
                                                messages.getString("status.payment-date"),
                                                paymentDate.format(context.getPaymentDate())),
                                        new ConfirmationDisplayFieldDTO(
                                                messages.getString("status.recipient"),
                                                context.getRecipientName())),
                                format(proposals.get(Operation.STATUS)));
                summary =
                        view.getTitle()
                                + "\n\n"
                                + view.getFields().stream()
                                        .map(field -> field.getLabel() + ": " + field.getValue())
                                        .collect(Collectors.joining("\n"))
                                + "\n\n"
                                + view.getQuestion();
            } else {
                summary = format(proposals.get(spec.getOperation()), parameters);
            }
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
        String text;
        if (outcome == TurnOutcome.CONFIRMATION_REQUIRED) {
            text = summary;
        } else if (outcome == TurnOutcome.INVALID_COMMAND
                && snapshot.getState().getCategory() == SessionStateCategory.INTERMEDIATE) {
            text = format(activeInvalidCommand);
        } else {
            text = format(templates.get(outcome), title);
        }
        ResultKind kind =
                outcome == TurnOutcome.SESSION_COMPLETED || outcome == TurnOutcome.INVALID_COMMAND
                        ? ResultKind.ERROR
                        : ResultKind.SUCCESS;
        return TurnOutDTO.builder()
                .requestId(requestId)
                .confirmationView(view)
                .terminal(snapshot.getState().getCategory() == SessionStateCategory.TERMINAL)
                .confirmation(
                        outcome == TurnOutcome.CONFIRMATION_REQUIRED ? confirmation : List.of())
                .suggestions(
                        outcome == TurnOutcome.CONFIRMATION_REQUIRED
                                ? List.of(
                                        new TurnSuggestionDTO(
                                                messages.getString("button.confirm"),
                                                null,
                                                SuggestionKind.CONFIRM),
                                        new TurnSuggestionDTO(
                                                messages.getString("button.cancel"),
                                                null,
                                                SuggestionKind.CANCEL))
                                : operationSuggestions(snapshot.getState()))
                .result(new ResultDTO(kind, outcome.getCode(), text))
                .build();
    }

    /** Восстанавливает представление шага, не меняя сохранённый исход или идентификатор хода. */
    public TurnOutDTO renderCurrent(SessionSnapshotDTO snapshot, UUID requestId) {
        if (snapshot == null || snapshot.getState().getCategory() == SessionStateCategory.INITIAL) {
            TurnOutDTO output =
                    information(
                            requestId,
                            ResultCode.OPERATION_CHOICE_REQUIRED,
                            messages.getString(
                                    snapshot == null ? "navigation.missing" : "navigation.choice"));
            output.setSuggestions(operationSuggestions(SessionState.CHOOSING_REQUEST_TYPE));
            return output;
        }
        return switch (snapshot.getState().getCategory()) {
            case TERMINAL -> renderOutcome(snapshot, TurnOutcome.SESSION_COMPLETED, requestId);
            case INTERMEDIATE -> renderIntermediate(snapshot, requestId);
            case INITIAL -> throw new IllegalStateException("Начальный шаг уже обработан");
        };
    }

    /** У каждого промежуточного шага должно быть собственное представление сохранённых данных. */
    private TurnOutDTO renderIntermediate(SessionSnapshotDTO snapshot, UUID requestId) {
        return switch (snapshot.getState()) {
            case AWAITING_CONFIRM ->
                    renderOutcome(snapshot, TurnOutcome.CONFIRMATION_REQUIRED, requestId);
            default ->
                    throw new IllegalStateException(
                            "Нет представления шага " + snapshot.getState());
        };
    }

    /** Добавляет только навигацию соответствующих исходов, не раскрывая подготовку. */
    public TurnOutDTO withNavigation(TurnOutDTO output, SessionSnapshotDTO snapshot) {
        if (!EnumSet.of(
                        ResultCode.REFERENCE_ANSWER,
                        ResultCode.REFERENCE_NOT_FOUND,
                        ResultCode.CLARIFICATION_REQUIRED,
                        ResultCode.MULTIPLE_ACTIONS,
                        ResultCode.INPUT_SOURCE_CONFLICT,
                        ResultCode.INVALID_COMMAND)
                .contains(output.getResult().getCode())) return output;
        SessionStateCategory category =
                snapshot == null ? SessionStateCategory.INITIAL : snapshot.getState().getCategory();
        output.setConfirmation(List.of());
        output.setTerminal(category == SessionStateCategory.TERMINAL);
        output.setSuggestions(
                switch (category) {
                    case INITIAL -> operationSuggestions(SessionState.CHOOSING_REQUEST_TYPE);
                    case TERMINAL -> List.of();
                    case INTERMEDIATE ->
                            List.of(
                                    new TurnSuggestionDTO(
                                            messages.getString("button.resume"),
                                            NavigationAction.RESUME.getActionCode(),
                                            SuggestionKind.COMMAND),
                                    new TurnSuggestionDTO(
                                            messages.getString("button.reset"),
                                            NavigationAction.RESET.getActionCode(),
                                            SuggestionKind.COMMAND));
                });
        if (category == SessionStateCategory.INTERMEDIATE) {
            ResultDTO result = output.getResult();
            output.setResult(
                    new ResultDTO(
                            result.getKind(),
                            result.getCode(),
                            result.getMessage()
                                    + "\n\n"
                                    + messages.getString("navigation.question")));
        }
        return output;
    }

    /** Единый источник кнопок операций для ответов и начального fixture. */
    public List<TurnSuggestionDTO> operationSuggestions(SessionState state) {
        return operations.choices(state).stream()
                .map(
                        operation ->
                                new TurnSuggestionDTO(
                                        operation.getTitle(),
                                        operation.getActionCode(),
                                        SuggestionKind.COMMAND))
                .toList();
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

    /** Готовит уточнение; оркестратор отдельно добавляет навигацию по актуальному снимку. */
    public TurnOutDTO clarification(UUID requestId) {
        return information(
                requestId,
                ResultCode.CLARIFICATION_REQUIRED,
                messages.getString("text.clarification"));
    }

    /** Конфликт источников не исполняет ни одно из переданных действий. */
    public TurnOutDTO inputSourceConflict(UUID requestId) {
        return information(
                requestId,
                ResultCode.INPUT_SOURCE_CONFLICT,
                messages.getString("text.input-source-conflict"));
    }

    /** Несколько действий требуют нового однозначного сообщения. */
    public TurnOutDTO multipleActions(UUID requestId) {
        return information(
                requestId,
                ResultCode.MULTIPLE_ACTIONS,
                messages.getString("text.multiple-actions"));
    }

    /** Переносит ответ как текст, не интерпретируя его в качестве шаблона. */
    public TurnOutDTO reference(UUID requestId, ReferenceAnswerDTO answer) {
        return answer.getFound()
                ? information(requestId, ResultCode.REFERENCE_ANSWER, answer.getAnswer())
                : information(
                        requestId,
                        ResultCode.REFERENCE_NOT_FOUND,
                        messages.getString("text.reference-not-found"));
    }

    private TurnOutDTO information(UUID requestId, ResultCode code, String text) {
        return TurnOutDTO.builder()
                .requestId(requestId)
                .result(new ResultDTO(ResultKind.SUCCESS, code, text))
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
