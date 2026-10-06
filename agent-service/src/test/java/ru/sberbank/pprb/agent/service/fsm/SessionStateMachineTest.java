package ru.sberbank.pprb.agent.service.fsm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.guard.Guard;
import reactor.core.publisher.Mono;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.action.*;
import ru.sberbank.pprb.agent.service.fsm.definition.OperationDefinition;
import ru.sberbank.pprb.agent.service.fsm.guard.common.*;

/** Проверяет порядок штатных callbacks на настоящей SSM, без собственного плана перехода. */
class SessionStateMachineTest {
    /** Первый отказ блокирует последующие guards/action; новая машина имеет независимый допуск. */
    @Test
    void shortCircuitsGuardsAndDoesNotShareRejectionWithNextMachine() throws Exception {
        List<String> trace = new ArrayList<>();
        AtomicBoolean deny = new AtomicBoolean(true);
        Guard<SessionState, SessionEvent> first =
                c -> {
                    trace.add("first");
                    return !deny.get()
                            || SessionExecutionContext.from(c).reject(ResultCode.CONTEXT_MISMATCH);
                };
        Guard<SessionState, SessionEvent> second =
                c -> {
                    trace.add("second");
                    return true;
                };
        Action<SessionState, SessionEvent> action = c -> trace.add("action");
        var initial = mock(InitialContextGuard.class);
        when(initial.evaluate(any()))
                .thenAnswer(
                        call -> {
                            trace.add("initial");
                            return true;
                        });
        var consistent = mock(ContextConsistencyGuard.class);
        when(consistent.evaluate(any()))
                .thenAnswer(
                        call -> {
                            trace.add("context");
                            return true;
                        });
        var definition =
                new OperationDefinition(
                        new OperationSpecDTO(
                                Operation.STATUS,
                                "status",
                                "Статус",
                                "Статус",
                                "status.proposal",
                                List.of()),
                        action,
                        action,
                        List.of(first, second),
                        List.of());
        var configuration =
                new SessionStateMachineConfiguration(
                        List.of(definition),
                        initial,
                        new ExistingSessionGuard(),
                        new ActivePreparationGuard(),
                        consistent,
                        new PreparationNumberGuard(),
                        new SeparateConfirmationGuard(),
                        new ConfirmationDataGuard(),
                        new CancelPreparationAction(),
                        new ReportNoPreparationAction());

        var rejected = select(configuration);
        assertThat(trace).containsExactly("initial", "context", "first");
        assertThat(rejected.rejectionCode).isEqualTo(ResultCode.CONTEXT_MISMATCH);
        assertThat(rejected.actionCompleted).isFalse();
        deny.set(false);
        trace.clear();
        var accepted = select(configuration);
        assertThat(trace).containsExactly("initial", "context", "first", "second", "action");
        assertThat(accepted.rejectionCode).isNull();
        assertThat(accepted.actionCompleted).isTrue();
        assertThat(rejected.rejectionCode).isEqualTo(ResultCode.CONTEXT_MISMATCH);
    }

    /**
     * Выполняет реальное событие и проверяет выбранный SSM переход без сохранения transient-данных.
     */
    private SessionExecutionContext select(SessionStateMachineConfiguration configuration)
            throws Exception {
        var id = UUID.randomUUID();
        var execution =
                new SessionExecutionContext(
                        SessionTurnInDTO.builder()
                                .sessionId(id)
                                .event(SessionEvent.SELECT)
                                .operation(Operation.STATUS)
                                .build());
        execution.snapshot =
                SessionSnapshotDTO.builder()
                        .sessionId(id)
                        .state(SessionState.CHOOSING_REQUEST_TYPE)
                        .build();
        var machine = configuration.create(id);
        try {
            machine.startReactively().block();
            machine.sendEvent(
                            Mono.just(
                                    MessageBuilder.withPayload(SessionEvent.SELECT)
                                            .setHeader(SessionExecutionContext.HEADER, execution)
                                            .build()))
                    .concatMap(result -> result.complete())
                    .then()
                    .block();
            assertThat(machine.getState().getId())
                    .isEqualTo(
                            execution.actionCompleted
                                    ? SessionState.AWAITING_CONFIRM
                                    : SessionState.CHOOSING_REQUEST_TYPE);
            assertThat(machine.getExtendedState().getVariables()).isEmpty();
            return execution;
        } finally {
            machine.stopReactively().block();
        }
    }
}
