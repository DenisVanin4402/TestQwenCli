package ru.sberbank.pprb.agent.gigaassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.sberbank.pprb.agent.gigaassistant.config.AclJsonConfiguration;
import ru.sberbank.pprb.agent.gigaassistant.controller.AclController;
import ru.sberbank.pprb.agent.gigaassistant.mapper.AclMapper;
import ru.sberbank.pprb.agent.gigaassistant.service.AclInputAdapter;
import ru.sberbank.pprb.agent.model.dto.turn.ResultDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.ResultKind;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;
import ru.sberbank.pprb.agent.service.port.in.SessionTurnUseCase;

/**
 * Проверяет реальное чтение JSON на ACL-границе и передачу общего намерения без чтения FSM
 * адаптером.
 */
class AclBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {"resume_operation", "reset_operation"})
    void navigationRemainsSeparateFromBusinessEvents(String code) throws Exception {
        var body = envelope("request", code, null);
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk());
        var input = capturedInput();
        assertThat(input.getValidationError()).isNull();
        assertThat(input.getEvent()).isNull();
        assertThat(input.getOperation()).isNull();
        assertThat(input.getNavigationAction().getActionCode()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept_propose", "reject_propose"})
    void conflictingNavigationNeverBecomesConsent(String performative) throws Exception {
        var body = envelope(performative, "resume_operation", null);
        if (performative.equals("request"))
            ((ObjectNode) body.at("/message/content")).put("user_input", "да");
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk());
        assertThat(capturedInput().isInputSourceConflict()).isTrue();
        assertThat(capturedInput().getValidationError()).isNull();
    }

    private static final String REQUEST_ID = "22222222-2222-4222-8222-222222222222";
    private static final String SESSION_ID = "11111111-1111-4111-8111-111111111111";
    private SessionTurnUseCase useCase;
    private MockMvc http;
    private ObjectMapper json;

    /** Собирает HTTP-границу; сценарий подменён, чтобы проверить именно переданные ему данные. */
    @BeforeEach
    void setUp() {
        useCase = mock(SessionTurnUseCase.class);
        var configuration = new AclJsonConfiguration();
        var builder = Jackson2ObjectMapperBuilder.json().modules(configuration.aclNullableModule());
        configuration.aclStringCoercion().customize(builder);
        json = builder.build();
        var operations = mock(ru.sberbank.pprb.agent.service.port.in.OperationCatalog.class);
        when(operations.resolve("status"))
                .thenReturn(
                        java.util.Optional.of(
                                ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO
                                        .builder()
                                        .operation(
                                                ru.sberbank.pprb.agent.model.enums.Operation.STATUS)
                                        .actionCode("status")
                                        .confirmationFields(java.util.List.of())
                                        .build()));
        var adapter =
                new AclInputAdapter(
                        useCase, Mappers.getMapper(AclMapper.class), operations, "invest-pro");
        http =
                MockMvcBuilders.standaloneSetup(new AclController(adapter))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                        .build();
        when(useCase.handle(any(), anyString()))
                .thenAnswer(
                        invocation -> {
                            SessionTurnInDTO input = invocation.getArgument(0);
                            ResultDTO result =
                                    input.getValidationError() == null
                                            ? new ResultDTO(
                                                    ResultKind.SUCCESS,
                                                    ResultCode.CONFIRMATION_REQUIRED,
                                                    "Проверьте сводку")
                                            : input.getValidationError();
                            return TurnOutDTO.builder()
                                    .requestId(input.getRequestId())
                                    .result(result)
                                    .build();
                        });
    }

    /** Mapper сохраняет дефекты согласия до guards и исключает другие валидные типы state. */
    @Test
    void preservesDuplicateAndNullConfirmationValues() throws Exception {
        ObjectNode body = envelope("accept_propose", null, fullMetadata());
        var state = body.putArray("state");
        state.addObject()
                .put("key", "paymentNumber")
                .put("value", "42")
                .put("type", "CONFIRMATION");
        state.addObject().put("key", "paymentNumber").putNull("value").put("type", "CONFIRMATION");
        state.addObject().put("key", "unrelated").put("value", "ignored").put("type", "MEMORY");
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk());
        var input = capturedInput();
        assertThat(input.getEvent()).isEqualTo(SessionEvent.CONFIRM);
        assertThat(input.getOperation()).isNull();
        assertThat(input.getConfirmation()).hasSize(2);
        assertThat(input.getConfirmation().get(1).getKey()).isEqualTo("paymentNumber");
        assertThat(input.getConfirmation().get(1).getValue()).isNull();
    }

    @Test
    @DisplayName(
            "Неизвестные поля игнорируются, а реквизиты, включая кавычки, читаются без искажения")
    void mapsKnownContextAndIgnoresUnknownFields() throws Exception {
        ObjectNode body = envelope("request", "status", fullMetadata());
        body.putObject("unknown").put("anything", true);
        String recipient = "ООО \"Альфа\" \\ отдел";
        ((ObjectNode) body.at("/metadata/additional_info/5")).put("value", recipient);
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.in_reply_to").value(REQUEST_ID))
                .andExpect(jsonPath("$.message.sender").value("invest-pro"))
                .andExpect(jsonPath("$.message.receiver").value("LOCAL_TEST"))
                .andExpect(jsonPath("$.metadata.final_message").value(false));
        SessionTurnInDTO input = capturedInput();
        assertThat(input.getEvent()).isEqualTo(SessionEvent.SELECT);
        assertThat(input.getContext().getEpkId()).isEqualTo("test-org-1");
        assertThat(input.getContext().getDigitalUserId()).isEqualTo("test-user-1");
        assertThat(input.getContext().getPaymentDate()).hasToString("2026-10-03");
        assertThat(input.getContext().getAmount()).isEqualByComparingTo("12500.00");
        assertThat(input.getContext().getRecipientName()).isEqualTo(recipient);
    }

    @Test
    @DisplayName(
            "Ключ содержит канонические UUID сессии и сообщения; разные сессии не разделяют ключ")
    void passesSeparateCanonicalIdempotencyKey() throws Exception {
        String requestId = "CCCCCCCC-CCCC-4CCC-8CCC-CCCCCCCCCCCC";
        for (String session :
                new String[] {
                    "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA", "BBBBBBBB-BBBB-4BBB-8BBB-BBBBBBBBBBBB"
                }) {
            ObjectNode body = envelope("request", "status", fullMetadata());
            ((ObjectNode) body.get("message"))
                    .put("reply_with", "cccccccc-cccc-4ccc-8ccc-cccccccccccc");
            http.perform(
                            post("/api/v1/ai/agents/invest-pro")
                                    .header("Request-Id", requestId)
                                    .header("Gigachat-Session-Id", session)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body.toString()))
                    .andExpect(status().isOk());
        }
        var keys = ArgumentCaptor.forClass(String.class);
        verify(useCase, times(2)).handle(any(), keys.capture());
        assertThat(keys.getAllValues())
                .containsExactly(
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa:cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb:cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    }

    @Test
    @DisplayName("Частичный контекст сохраняет пропуски; несколько источников не выбирают действие")
    void keepsPartialContextAndDetectsSourceConflict() throws Exception {
        ObjectNode metadata = json.createObjectNode();
        metadata.putArray("additional_info")
                .addObject()
                .put("key", "paymentNumber")
                .put("value", "42");
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        envelope("accept_propose", "unsupported", metadata)
                                                .toString()))
                .andExpect(status().isOk());
        SessionTurnInDTO input = capturedInput();
        assertThat(input.isInputSourceConflict()).isTrue();
        assertThat(input.getContext().getPaymentNumber()).isEqualTo("42");
        assertThat(input.getContext().getEpkId()).isNull();
        assertThat(input.getContext().getAmount()).isNull();
        assertThat(input.getValidationError()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept_propose", "reject_propose"})
    void preservesTextAndDetectsSourceConflict(String performative) throws Exception {
        var body =
                envelope(
                        performative,
                        "request".equals(performative) ? "status" : null,
                        fullMetadata());
        ((ObjectNode) body.at("/message/content")).put("user_input", "Какая комиссия?");
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk());
        var input = capturedInput();
        assertThat(input.isInputSourceConflict()).isTrue();
        assertThat(input.getUserInput()).isEqualTo("Какая комиссия?");
    }

    @Test
    void informationMetadataIsDistinctFromOperationalInform() throws Exception {
        org.mockito.Mockito.doReturn(
                        TurnOutDTO.builder()
                                .result(
                                        new ResultDTO(
                                                ResultKind.ERROR,
                                                ResultCode.REFERENCE_FAILED,
                                                "Справка недоступна"))
                                .build())
                .when(useCase)
                .handle(any(), anyString());
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(envelope("request", "status", null).toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.metadata.additional_info[0].key").value("response_mode"))
                .andExpect(jsonPath("$.metadata.additional_info[0].value").value("information"))
                .andExpect(jsonPath("$.metadata.final_message").value(false))
                .andExpect(jsonPath("$.state").isEmpty())
                .andExpect(jsonPath("$.suggestions").isEmpty());
    }

    @Test
    void unsupportedPerformativeIsNotHiddenBySourceConflict() throws Exception {
        ObjectNode body = envelope("inform", "status", fullMetadata());
        ((ObjectNode) body.at("/message/content")).put("user_input", "Какая комиссия?");
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.performative").value("failure"))
                .andExpect(jsonPath("$.metadata.additional_info").isEmpty());
        assertThat(capturedInput().getValidationError().getCode())
                .isEqualTo(ResultCode.UNSUPPORTED_COMMAND);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "{\"organization\":{\"epk_id\":null}}",
                "{\"additional_info\":[{\"key\":\"amount\",\"value\":null}]}",
                "{\"additional_info\":[{\"key\":\"amount\",\"value\":\"\"}]}",
                "{\"additional_info\":[{\"key\":\"amount\",\"value\":\"1\"},{\"key\":\"amount\",\"value\":\"2\"}]}"
            })
    @DisplayName("Явный null, пустое значение и повтор ключа отклоняются как INVALID_REQUEST")
    void rejectsExplicitInvalidContext(String metadata) throws Exception {
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        envelope("request", "status", json.readTree(metadata))
                                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.performative").value("failure"));
        assertThat(capturedInput().getValidationError().getCode())
                .isEqualTo(ResultCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName(
            "Ответ сохраняет известную ядру завершённость без полной проекции и UUID из заголовка")
    void keepsHeaderIdentityAndSavedTerminalFlagOnFailure() throws Exception {
        doAnswer(
                        invocation -> {
                            SessionTurnInDTO input = invocation.getArgument(0);
                            return TurnOutDTO.builder()
                                    .requestId(input.getRequestId())
                                    .result(input.getValidationError())
                                    .terminal(true)
                                    .build();
                        })
                .when(useCase)
                .handle(any(), anyString());
        ObjectNode body = envelope("request", "status", null);
        ((ObjectNode) body.get("message"))
                .put("reply_with", "33333333-3333-4333-8333-333333333333");
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.in_reply_to").value(REQUEST_ID))
                .andExpect(jsonPath("$.metadata.final_message").value(true));
        assertThat(capturedInput().getValidationError().getCode())
                .isEqualTo(ResultCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Повреждённый UUID не создаёт вымышленный идентификатор и не вызывает сценарий")
    void rejectsMalformedIdentityBeforeUseCase() throws Exception {
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", "not-a-uuid")
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(envelope("request", "status", null).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.in_reply_to").doesNotExist());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("Числовой JSON value не преобразуется молча в строковый реквизит ACL")
    void rejectsNumericMetadataValue() throws Exception {
        ObjectNode metadata = fullMetadata();
        ((ObjectNode) metadata.at("/additional_info/3")).put("value", 12500.00);
        http.perform(
                        post("/api/v1/ai/agents/invest-pro")
                                .header("Request-Id", REQUEST_ID)
                                .header("Gigachat-Session-Id", SESSION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(envelope("request", "status", metadata).toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    /** Извлекает единственный вход ядра для проверки смысла преобразованных данных. */
    private SessionTurnInDTO capturedInput() {
        var input = ArgumentCaptor.forClass(SessionTurnInDTO.class);
        verify(useCase).handle(input.capture(), anyString());
        return input.getValue();
    }

    /**
     * Строит JSON деревом: значения с кавычками или обратной косой чертой корректно экранируются
     * Jackson.
     */
    private ObjectNode envelope(String performative, String action, JsonNode metadata) {
        ObjectNode body = json.createObjectNode();
        ObjectNode message = body.putObject("message");
        message.put("version", "1.6")
                .put("performative", performative)
                .put("sender", "LOCAL_TEST")
                .put("receiver", "invest-pro")
                .put("conversation_id", "44444444-4444-4444-8444-444444444444")
                .put("reply_with", REQUEST_ID)
                .put("unknown", true);
        message.putObject("content").put("action_code", action).put("text", "не используется");
        if (metadata != null) body.set("metadata", metadata);
        return body;
    }

    /**
     * Возвращает новый синтетический контекст первого сообщения; отдельный тест может изменить
     * нужное поле.
     */
    private ObjectNode fullMetadata() {
        ObjectNode metadata = json.createObjectNode();
        metadata.putObject("organization").put("epk_id", "test-org-1").put("unknown", true);
        metadata.putObject("customer_info").put("digital_user_id", "test-user-1");
        var parameters = metadata.putArray("additional_info");
        for (String[] field :
                new String[][] {
                    {"paymentId", "test-payment-42"},
                    {"paymentNumber", "42"},
                    {"paymentDate", "2026-10-03"},
                    {"amount", "12500.00"},
                    {"currency", "RUB"},
                    {"recipientName", "ООО Альфа"},
                    {"organizationName", "ООО Вектор"}
                }) {
            parameters.addObject().put("key", field[0]).put("value", field[1]);
        }
        return metadata;
    }
}
