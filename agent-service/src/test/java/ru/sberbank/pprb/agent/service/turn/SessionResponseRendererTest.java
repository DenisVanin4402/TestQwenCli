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
        var fields = new ArrayList<>(fields());
        fields.add(
                new ConfirmationFieldDTO(
                        "paymentDate",
                        PaymentField.PAYMENT_DATE,
                        ConfirmationValueType.DATE,
                        "Дата"));
        var preparation =
                PreparationDTO.builder()
                        .operation(Operation.STATUS)
                        .preparationNo(2)
                        .context(
                                TrustedPaymentContextDTO.builder()
                                        .paymentNumber("Не показывать")
                                        .build())
                        .confirmationSnapshot(
                                List.of(
                                        new ConfirmationValueDTO("paymentNumber", "0042"),
                                        new ConfirmationValueDTO("paymentDate", "2026-10-03"),
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
                        operations(fields),
                        catalog("status.proposal", "Платёж №{0} от {1}"),
                        format);
        var view = renderer.render(snapshot);
        assertThat(view.getResult().getMessage()).isEqualTo("Платёж №0042 от " + expectedDate);
        assertThat(view.getConfirmation())
                .extracting(ConfirmationParameterDTO::getValue)
                .containsExactly("0042", "2026-10-03", "2");
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
