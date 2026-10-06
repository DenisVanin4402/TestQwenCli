package ru.sberbank.pprb.agent.service.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.*;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.SessionExecutionService;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;
import ru.sberbank.pprb.agent.service.port.out.*;

/** Текст нормализуется до транзакции; отсутствие команды не касается FSM. */
class TextMessageTurnTest {
    private final SessionExecutionService fsm = mock(SessionExecutionService.class);
    private final TextMessageClassifier classifier = mock(TextMessageClassifier.class);
    private final OperationCatalog catalog = mock(OperationCatalog.class);
    private final SessionResponseRenderer renderer =
            new SessionResponseRenderer(catalog, "session-responses", "dd.MM.yyyy");
    private final SessionTurnOrchestrator orchestrator =
            new SessionTurnOrchestrator(fsm, renderer, classifier, catalog);

    private SessionTurnInDTO text() {
        return SessionTurnInDTO.builder()
                .sessionId(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .userInput("Узнай статус платежа")
                .build();
    }

    @Test
    void asksForClarificationWithoutAnyFsmInteraction() {
        var input = text();
        when(classifier.classify(anyString(), anyList())).thenReturn(new MessageAnalysisDTO(null));
        var output = orchestrator.handle(input, "key");
        assertThat(output.getRequestId()).isEqualTo(input.getRequestId());
        assertThat(output.getResult().getCode()).isEqualTo(ResultCode.CLARIFICATION_REQUIRED);
        assertThat(output.getResult().getKind()).isEqualTo(ResultKind.SUCCESS);
        assertThat(output.getResult().getMessage()).contains("уточните");
        assertThat(output.getConfirmation()).isEmpty();
        assertThat(output.getAvailableOperations()).isEmpty();
        assertThat(output.isTerminal()).isFalse();
        verifyNoInteractions(fsm);
        verify(classifier).classify(input.getUserInput(), List.of());
    }

    @Test
    void mapsStatusToTheExistingSelectWithoutMutatingInput() {
        var input = text();
        var spec = mock(OperationSpecDTO.class);
        when(spec.getOperation()).thenReturn(Operation.STATUS);
        when(catalog.resolve("status")).thenReturn(Optional.of(spec));
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO("status"));
        when(fsm.process(any()))
                .thenReturn(
                        SessionSnapshotDTO.builder()
                                .lastRequestId(input.getRequestId())
                                .state(SessionState.CHOOSING_REQUEST_TYPE)
                                .lastResult(TurnOutcome.NO_ACTIVE_PREPARATION)
                                .build());
        orchestrator.handle(input, "key");
        var event = ArgumentCaptor.forClass(SessionTurnInDTO.class);
        verify(fsm).ensureSemaphore(input.getSessionId());
        verify(fsm).process(event.capture());
        assertThat(event.getValue().getEvent()).isEqualTo(SessionEvent.SELECT);
        assertThat(event.getValue().getOperation()).isEqualTo(Operation.STATUS);
        assertThat(event.getValue().getUserInput()).isNull();
        assertThat(input.getEvent()).isNull();
    }

    @Test
    void reportsTechnicalFailureWithoutFsm() {
        when(classifier.classify(anyString(), anyList()))
                .thenThrow(new TextAnalysisException("synthetic"));
        assertThat(orchestrator.handle(text(), "key").getResult().getCode())
                .isEqualTo(ResultCode.TEXT_ANALYSIS_FAILED);
        verifyNoInteractions(fsm);
    }

    @Test
    void explicitEventsBypassLlmEvenWithAccompanyingText() {
        var input = text();
        input.setEvent(SessionEvent.CANCEL);
        when(fsm.process(input))
                .thenReturn(
                        SessionSnapshotDTO.builder()
                                .lastRequestId(input.getRequestId())
                                .state(SessionState.CHOOSING_REQUEST_TYPE)
                                .lastResult(TurnOutcome.NO_ACTIVE_PREPARATION)
                                .build());
        orchestrator.handle(input, "key");
        verify(fsm).process(input);
        verifyNoInteractions(classifier);
    }
}
