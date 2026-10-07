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
    @Test
    void navigationBypassesLlmAndStorageFailureCannotBecomeMenu() {
        var input =
                text().toBuilder()
                        .userInput(null)
                        .navigationAction(NavigationAction.RESUME)
                        .build();
        var output = orchestrator.handle(input, "key");
        assertThat(output.getResult().getCode()).isEqualTo(ResultCode.OPERATION_CHOICE_REQUIRED);
        verify(fsm).find(input.getSessionId());
        verifyNoMoreInteractions(fsm);
        verifyNoInteractions(classifier, reference);
        when(fsm.find(input.getSessionId()))
                .thenThrow(
                        new ru.sberbank.pprb.agent.service.fsm.SessionPersistenceException(
                                ResultCode.SESSION_BUSY, "busy"));
        assertThat(orchestrator.handle(input, "key").getResult().getCode())
                .isEqualTo(ResultCode.SESSION_BUSY);
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.UNCLEAR, null));
        var failed =
                orchestrator.handle(
                        input.toBuilder().navigationAction(null).userInput("?").build(), "key");
        assertThat(failed.getResult().getCode()).isEqualTo(ResultCode.SESSION_BUSY);
        assertThat(failed.getSuggestions()).isEmpty();
    }

    private final SessionExecutionService fsm = mock(SessionExecutionService.class);
    private final TextMessageClassifier classifier = mock(TextMessageClassifier.class);
    private final OperationCatalog catalog = mock(OperationCatalog.class);
    private final ReferenceAnswerProvider reference = mock(ReferenceAnswerProvider.class);
    private final SessionResponseRenderer renderer =
            new SessionResponseRenderer(catalog, "session-responses", "dd.MM.yyyy");
    private final SessionTurnOrchestrator orchestrator =
            new SessionTurnOrchestrator(fsm, renderer, classifier, catalog, reference);

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
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.UNCLEAR, null));
        var output = orchestrator.handle(input, "key");
        assertThat(output.getRequestId()).isEqualTo(input.getRequestId());
        assertThat(output.getResult().getCode()).isEqualTo(ResultCode.CLARIFICATION_REQUIRED);
        assertThat(output.getResult().getKind()).isEqualTo(ResultKind.SUCCESS);
        assertThat(output.getResult().getMessage()).containsIgnoringCase("уточните");
        assertThat(output.getConfirmation()).isEmpty();
        assertThat(output.getSuggestions()).isEmpty();
        assertThat(output.isTerminal()).isFalse();
        verify(fsm).find(input.getSessionId());
        verifyNoMoreInteractions(fsm);
        verify(classifier).classify(input.getUserInput(), List.of());
    }

    @Test
    void mapsStatusToTheExistingSelectWithoutMutatingInput() {
        var input = text();
        var spec = mock(OperationSpecDTO.class);
        when(spec.getOperation()).thenReturn(Operation.STATUS);
        when(catalog.resolve("status")).thenReturn(Optional.of(spec));
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.COMMAND, "status"));
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
        var output = orchestrator.handle(text(), "key");
        assertThat(output.getResult().getCode()).isEqualTo(ResultCode.TEXT_ANALYSIS_FAILED);
        assertThat(output.getResult().getMessage())
                .isEqualTo("Не удалось обработать сообщение. Попробуйте позже.");
        assertThat(output.getSuggestions()).isEmpty();
        verifyNoInteractions(fsm);
    }

    @Test
    void explicitEventsBypassLlm() {
        var input = text();
        input.setUserInput(null);
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

    @Test
    void sourceConflictPreventsEvenExplicitConfirmation() {
        var input =
                text().toBuilder().event(SessionEvent.CONFIRM).inputSourceConflict(true).build();
        assertThat(orchestrator.handle(input, "key").getResult().getCode())
                .isEqualTo(ResultCode.INPUT_SOURCE_CONFLICT);
        verifyNoInteractions(classifier, reference);
        verify(fsm).find(input.getSessionId());
        verifyNoMoreInteractions(fsm);
    }

    @Test
    void mixedRequestDoesNothing() {
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.MIXED, null));
        assertThat(orchestrator.handle(text(), "key").getResult().getCode())
                .isEqualTo(ResultCode.MULTIPLE_ACTIONS);
        verifyNoInteractions(reference);
        verify(fsm).find(any());
        verifyNoMoreInteractions(fsm);
    }

    @Test
    void referenceDistinguishesAnswerMissingKnowledgeAndFailureWithoutFsm() {
        var input = text();
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.REFERENCE, null));
        when(reference.answer(input.getUserInput()))
                .thenReturn(
                        new ReferenceAnswerDTO(true, "Комиссия — 50 рублей {0}", List.of("KB-03")))
                .thenReturn(new ReferenceAnswerDTO(false, null, List.of()))
                .thenThrow(new ReferenceAnswerException("synthetic"));
        var answer = orchestrator.handle(input, "key");
        verify(reference).answer(input.getUserInput());
        assertThat(answer.getRequestId()).isEqualTo(input.getRequestId());
        assertThat(answer.getResult().getMessage()).isEqualTo("Комиссия — 50 рублей {0}");
        assertThat(answer.getResult().getCode()).isEqualTo(ResultCode.REFERENCE_ANSWER);
        assertThat(answer.getSuggestions()).isEmpty();
        assertThat(answer.getConfirmation()).isEmpty();
        assertThat(orchestrator.handle(input, "key").getResult().getCode())
                .isEqualTo(ResultCode.REFERENCE_NOT_FOUND);
        assertThat(orchestrator.handle(input, "key").getResult().getCode())
                .isEqualTo(ResultCode.REFERENCE_FAILED);
        verify(fsm, times(2)).find(input.getSessionId());
        verifyNoMoreInteractions(fsm);
    }
}
