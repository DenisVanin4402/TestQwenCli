package ru.sberbank.pprb.agent.service.fsm;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.guard.Guard;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.PreparationDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.action.*;
import ru.sberbank.pprb.agent.service.fsm.definition.OperationDefinition;
import ru.sberbank.pprb.agent.service.fsm.guard.common.*;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;

/**
 * Единственное место регистрации операций и штатных переходов SSM. Алгоритмы — в guards/actions.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class SessionStateMachineConfiguration implements OperationCatalog {
    /** Программные определения доступных операций и их компонентов. */
    private final List<OperationDefinition> definitions;

    /** Полнота контекста первого выбора. */
    private final InitialContextGuard initial;

    /** Запрет создания сессии отменой предложения. */
    private final ExistingSessionGuard existing;

    /** Наличие активной неподтверждённой подготовки. */
    private final ActivePreparationGuard active;

    /** Согласованность переданных реквизитов с серверным контекстом. */
    private final ContextConsistencyGuard context;

    /** Соответствие номера подтверждаемой подготовки. */
    private final PreparationNumberGuard number;

    /** Отдельное сообщение согласия. */
    private final SeparateConfirmationGuard separate;

    /** Точный состав и значения подтверждения. */
    private final ConfirmationDataGuard data;

    /** Удаление активной подготовки. */
    private final CancelPreparationAction cancel;

    /** Предметный результат отмены без активной подготовки. */
    private final ReportNoPreparationAction noPreparation;

    /** Не допускает неоднозначный выбор операции из каталога при запуске приложения. */
    @jakarta.annotation.PostConstruct
    void verifyDefinitions() {
        Set<Operation> operations = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (OperationDefinition definition : definitions) {
            OperationSpecDTO spec = definition.getSpec();
            if (NavigationAction.resolve(spec.getActionCode()).isPresent()
                    || !operations.add(spec.getOperation())
                    || !codes.add(spec.getActionCode())) {
                throw new IllegalArgumentException("Повторная регистрация операции или actionCode");
            }
        }
    }

    /** Статический bean позволяет внедрить определения без цикла создания конфигурации. */
    @Bean
    public static OperationDefinition statusOperation(
            PrepareOperationAction prepare, ExecutePreparedOperationAction execute) {
        return new OperationDefinition(
                new OperationSpecDTO(
                        Operation.STATUS,
                        "status",
                        "Запросить статус",
                        "Запросить статус",
                        "status.proposal",
                        List.of(
                                new ConfirmationFieldDTO(
                                        "paymentNumber",
                                        PaymentField.PAYMENT_NUMBER,
                                        ConfirmationValueType.STRING,
                                        "Номер платежа"))),
                prepare,
                execute,
                List.of(),
                List.of());
    }

    /** Создаёт отдельную машину без кеша, фоновых действий и собственного описания переходов. */
    public StateMachine<SessionState, SessionEvent> create(UUID sessionId) throws Exception {
        StateMachineBuilder.Builder<SessionState, SessionEvent> builder =
                StateMachineBuilder.<SessionState, SessionEvent>builder();
        builder.configureConfiguration()
                .withConfiguration()
                .machineId(sessionId.toString())
                .autoStartup(false);
        builder.configureStates()
                .withStates()
                .initial(SessionState.CHOOSING_REQUEST_TYPE)
                .states(EnumSet.allOf(SessionState.class));
        builder.configureTransitions()
                .withExternal()
                .source(SessionState.CHOOSING_REQUEST_TYPE)
                .event(SessionEvent.SELECT)
                .target(SessionState.AWAITING_CONFIRM)
                .guard(guards(List.of(initial, context)))
                .action(
                        tracked(
                                c ->
                                        SessionExecutionContext.from(c)
                                                .operation
                                                .getPrepareAction()
                                                .execute(c)))
                .and()
                .withExternal()
                .source(SessionState.CHOOSING_REQUEST_TYPE)
                .event(SessionEvent.CANCEL)
                .target(SessionState.CHOOSING_REQUEST_TYPE)
                .guard(guards(List.of(existing, context)))
                .action(tracked(noPreparation))
                .and()
                .withExternal()
                .source(SessionState.AWAITING_CONFIRM)
                .event(SessionEvent.CANCEL)
                .target(SessionState.CHOOSING_REQUEST_TYPE)
                .guard(guards(List.of(existing, context)))
                .action(tracked(cancel))
                .and()
                .withExternal()
                .source(SessionState.AWAITING_CONFIRM)
                .event(SessionEvent.CONFIRM)
                .target(SessionState.COMPLETED)
                .guard(guards(List.of(active, context, number, separate, data)))
                .action(
                        tracked(
                                c ->
                                        SessionExecutionContext.from(c)
                                                .operation
                                                .getExecuteAction()
                                                .execute(c)));
        return builder.build();
    }

    /** Последовательная композиция штатных guards; первый отказ останавливает обработку. */
    private Guard<SessionState, SessionEvent> guards(
            List<Guard<SessionState, SessionEvent>> common) {
        return stateContext -> {
            SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
            try {
                SessionEvent event = execution.getInput().getEvent();
                PreparationDTO preparation = execution.snapshot.getPreparation();
                Operation operation =
                        event == SessionEvent.SELECT
                                ? execution.getInput().getOperation()
                                : event == SessionEvent.CONFIRM && preparation != null
                                        ? preparation.getOperation()
                                        : null;
                execution.operation =
                        definitions.stream()
                                .filter(d -> d.getSpec().getOperation() == operation)
                                .findFirst()
                                .orElse(null);
                for (Guard<SessionState, SessionEvent> guard : common) {
                    if (!guard.evaluate(stateContext))
                        return execution.reject(ResultCode.INVALID_COMMAND);
                }
                if (event != SessionEvent.CANCEL) {
                    if (execution.operation == null)
                        return execution.reject(ResultCode.INVALID_COMMAND);
                    List<Guard<SessionState, SessionEvent>> additional =
                            event == SessionEvent.SELECT
                                    ? execution.operation.getPrepareGuards()
                                    : execution.operation.getExecuteGuards();
                    for (Guard<SessionState, SessionEvent> guard : additional) {
                        if (!guard.evaluate(stateContext))
                            return execution.reject(ResultCode.INVALID_COMMAND);
                    }
                }
                execution.admitted = true;
                return true;
            } catch (Throwable error) {
                execution.error = error;
                throw new SessionPersistenceException(
                        ResultCode.OPERATION_FAILED, "Ошибка guard", error);
            }
        };
    }

    /**
     * Возвращает перехваченную SSM ошибку на транзакционную границу и отмечает окончание action.
     */
    private Action<SessionState, SessionEvent> tracked(Action<SessionState, SessionEvent> action) {
        return context -> {
            SessionExecutionContext execution = SessionExecutionContext.from(context);
            try {
                action.execute(context);
                execution.actionCompleted = true;
            } catch (Throwable error) {
                execution.error = error;
                throw new SessionPersistenceException(
                        ResultCode.OPERATION_FAILED, "Ошибка action", error);
            }
        };
    }

    /** Возвращает только метаданные операции, не раскрывая callbacks наружу. */
    @Override
    public Optional<OperationSpecDTO> operationSpec(Operation operation) {
        return definitions.stream()
                .map(OperationDefinition::getSpec)
                .filter(spec -> spec.getOperation() == operation)
                .findFirst();
    }

    /** Сопоставляет action_code входа зарегистрированной операции. */
    @Override
    public Optional<OperationSpecDTO> resolve(String code) {
        return definitions.stream()
                .map(OperationDefinition::getSpec)
                .filter(spec -> spec.getActionCode().equals(code))
                .findFirst();
    }

    /** Предлагает операции только на шаге выбора; допустимость входа решает сама SSM. */
    @Override
    public List<OperationSpecDTO> choices(SessionState state) {
        return state.getCategory() == SessionStateCategory.INITIAL
                ? registeredOperations()
                : List.of();
    }

    /** Единый список операций для саджестов и текстового разбора. */
    @Override
    public List<OperationSpecDTO> registeredOperations() {
        return definitions.stream().map(OperationDefinition::getSpec).toList();
    }
}
