package ru.sberbank.pprb.agent.service.turn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;

/** Текст и state используют сохранённые значения; неверный каталог запрещает старт. */
class SessionResponseRendererTest {

    @ParameterizedTest
    @EnumSource(
            value = ResultCode.class,
            names = {
                "REFERENCE_ANSWER",
                "REFERENCE_NOT_FOUND",
                "CLARIFICATION_REQUIRED",
                "MULTIPLE_ACTIONS",
                "INPUT_SOURCE_CONFLICT",
                "INVALID_COMMAND"
            })
    void navigationUsesCategoryAndNeverExposesConfirmation(ResultCode code) {
        var renderer =
                new SessionResponseRenderer(
                        operations(fields()), "session-responses", "dd.MM.yyyy");
        for (SessionState state : SessionState.values()) {
            var snapshot = SessionSnapshotDTO.builder().state(state).build();
            var output =
                    ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO.builder()
                            .requestId(UUID.randomUUID())
                            .result(
                                    new ru.sberbank.pprb.agent.model.dto.turn.ResultDTO(
                                            code == ResultCode.INVALID_COMMAND
                                                    ? ResultKind.ERROR
                                                    : ResultKind.SUCCESS,
                                            code,
                                            "Исходный текст"))
                            .build();
            renderer.withNavigation(output, snapshot);
            assertThat(output.getResult().getCode()).isEqualTo(code);
            assertThat(output.getConfirmation()).isEmpty();
            if (state == SessionState.AWAITING_CONFIRM) {
                assertThat(output.getResult().getMessage())
                        .isEqualTo("Исходный текст\n\nХотите продолжить незавершённую операцию?");
                assertThat(output.getSuggestions()).extracting("text").containsExactly("Да", "Нет");
                assertThat(output.getSuggestions())
                        .extracting("actionCode")
                        .containsExactly("resume_operation", "reset_operation");
            } else {
                assertThat(output.getResult().getMessage()).isEqualTo("Исходный текст");
                assertThat(output.getSuggestions())
                        .hasSize(state == SessionState.COMPLETED ? 0 : 1);
            }
            assertThat(output.isTerminal()).isEqualTo(state == SessionState.COMPLETED);
        }
    }

    @Test
    void currentStepDoesNotRepeatSavedErrorOrMutateSnapshot() {
        var renderer =
                new SessionResponseRenderer(
                        operations(fields()), "session-responses", "dd.MM.yyyy");
        var preparation =
                PreparationDTO.builder()
                        .operation(Operation.STATUS)
                        .preparationNo(3)
                        .context(context())
                        .confirmationSnapshot(
                                List.of(
                                        new ConfirmationValueDTO("paymentNumber", "0042"),
                                        new ConfirmationValueDTO("preparationNo", "3")))
                        .build();
        var savedRequest = UUID.randomUUID();
        var request = UUID.randomUUID();
        var snapshot =
                SessionSnapshotDTO.builder()
                        .state(SessionState.AWAITING_CONFIRM)
                        .preparation(preparation)
                        .lastResult(TurnOutcome.INVALID_COMMAND)
                        .lastRequestId(savedRequest)
                        .projectionVersion(8)
                        .build();
        var output = renderer.renderCurrent(snapshot, request);
        assertThat(output.getResult().getCode()).isEqualTo(ResultCode.CONFIRMATION_REQUIRED);
        assertThat(output.getRequestId()).isEqualTo(request);
        assertThat(output.getConfirmation()).extracting("value").containsExactly("0042", "3");
        assertThat(output.getSuggestions())
                .extracting("kind")
                .containsExactly(SuggestionKind.CONFIRM, SuggestionKind.CANCEL);
        assertThat(snapshot.getLastResult()).isEqualTo(TurnOutcome.INVALID_COMMAND);
        assertThat(snapshot.getLastRequestId()).isEqualTo(savedRequest);
        assertThat(snapshot.getProjectionVersion()).isEqualTo(8);
        assertThat(renderer.renderCurrent(null, request).getSuggestions()).hasSize(1);
    }

    /** Отказ на шаге выбора не должен приписывать уже отменённую подготовку. */
    @Test
    void invalidCommandWithoutPreparationDoesNotClaimAnActiveOperation() {
        var renderer =
                new SessionResponseRenderer(
                        operations(fields()), "session-responses", "dd.MM.yyyy");
        var snapshot =
                SessionSnapshotDTO.builder()
                        .state(SessionState.CHOOSING_REQUEST_TYPE)
                        .lastResult(TurnOutcome.INVALID_COMMAND)
                        .lastRequestId(UUID.randomUUID())
                        .build();
        var output = renderer.withNavigation(renderer.render(snapshot), snapshot);
        assertThat(output.getResult().getMessage()).isEqualTo("Сейчас это действие недоступно.");
        assertThat(output.getConfirmation()).isEmpty();
        assertThat(output.getSuggestions()).extracting("actionCode").containsExactly("status");
    }

    /** Подменяет только читаемый каталог, сохраняя реальный renderer и mapper. */
    private OperationCatalog operations(List<ConfirmationFieldDTO> fields) {
        var operations = mock(OperationCatalog.class);
        var spec =
                new OperationSpecDTO(
                        Operation.STATUS, "status", "Статус", "Статус", "status.proposal", fields);
        when(operations.choices(SessionState.CHOOSING_REQUEST_TYPE)).thenReturn(List.of(spec));
        when(operations.operationSpec(Operation.STATUS)).thenReturn(Optional.of(spec));
        return operations;
    }

    /** Минимальная схема подтверждения строкового номера платежа. */
    private List<ConfirmationFieldDTO> fields() {
        return List.of(
                new ConfirmationFieldDTO(
                        "paymentNumber",
                        PaymentField.PAYMENT_NUMBER,
                        ConfirmationValueType.STRING,
                        "Номер"));
    }

    @ParameterizedTest
    @CsvSource({"dd.MM.yyyy,03.10.2026", "yyyy-MM-dd,2026-10-03"})
    /**
     * Сводка и wire берут сохранённые значения даже при отличающемся контексте; формат даты меняет
     * только показ.
     */
    void rendersSavedValuesWithoutRereadingContext(String format, String expectedDate) {
        var preparation =
                PreparationDTO.builder()
                        .operation(Operation.STATUS)
                        .preparationNo(2)
                        .context(context())
                        .confirmationSnapshot(
                                List.of(
                                        new ConfirmationValueDTO("paymentNumber", "0042"),
                                        new ConfirmationValueDTO("preparationNo", "2")))
                        .build();
        var snapshot =
                SessionSnapshotDTO.builder()
                        .state(SessionState.AWAITING_CONFIRM)
                        .preparation(preparation)
                        .lastResult(TurnOutcome.CONFIRMATION_REQUIRED)
                        .projectionVersion(4)
                        .build();
        var renderer =
                new SessionResponseRenderer(
                        operations(fields()),
                        ResourceBundle.getBundle("session-responses", Locale.ROOT),
                        format);
        var view = renderer.render(snapshot);
        assertThat(view.getResult().getMessage())
                .isEqualTo(
                        "Запрос статуса платежа\n\nНомер платежа: 0042\nОрганизация: Организация\nДата платежа: "
                                + expectedDate
                                + "\nПолучатель: Получатель\n\nОтправить запрос статуса этого платежа?");
        assertThat(view).extracting("confirmationView").isNotNull();
        var resumed = renderer.renderCurrent(snapshot, UUID.randomUUID());
        assertThat(resumed.getResult().getMessage()).isEqualTo(view.getResult().getMessage());
        assertThat(view.getConfirmation())
                .extracting(ConfirmationParameterDTO::getValue)
                .containsExactly("0042", "2");
        assertThat(snapshot.getPreparation()).isSameAs(preparation);
        assertThat(snapshot.getProjectionVersion()).isEqualTo(4);
        assertThat(preparation.getConfirmedRequestId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{1}", "{0,number}", "{0,number} {0}", "{bad}", ""})
    /** Неверные индексы и форматы шаблона запрещают старт renderer. */
    void rejectsInvalidArgumentSchemaBeforeServingRequests(String pattern) {
        assertThatThrownBy(
                        () ->
                                new SessionResponseRenderer(
                                        operations(fields()),
                                        catalog("status.proposal", pattern),
                                        "dd.MM.yyyy"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    /** Отсутствие общего текста ошибки запрещает старт. */
    void requiresFallbackAtStartup() {
        assertThatThrownBy(
                        () ->
                                new SessionResponseRenderer(
                                        operations(fields()),
                                        catalog("error.DEFAULT", null),
                                        "dd.MM.yyyy"))
                .isInstanceOf(MissingResourceException.class);
    }

    private TrustedPaymentContextDTO context() {
        return TrustedPaymentContextDTO.builder()
                .paymentNumber("Не показывать")
                .organizationName("Организация")
                .recipientName("Получатель")
                .paymentDate(java.time.LocalDate.of(2026, 10, 3))
                .build();
    }

    /** Меняет один текст реального каталога для проверки валидации конфигурации. */
    private ResourceBundle catalog(String key, String replacement) {
        var original = ResourceBundle.getBundle("session-responses", Locale.ROOT);
        Map<String, String> values = new HashMap<>();
        original.keySet().forEach(name -> values.put(name, original.getString(name)));
        if (replacement == null) values.remove(key);
        else values.put(key, replacement);
        return new ResourceBundle() {
            @Override
            protected Object handleGetObject(String name) {
                return values.get(name);
            }

            @Override
            public Enumeration<String> getKeys() {
                return Collections.enumeration(values.keySet());
            }
        };
    }
}
