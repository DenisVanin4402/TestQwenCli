package ru.sberbank.pprb.agent.chatui;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.sberbank.pprb.agent.chatui.controller.LocalHelperController;
import ru.sberbank.pprb.agent.chatui.mapper.FixtureMapper;
import ru.sberbank.pprb.agent.chatui.service.FixtureCatalog;
import ru.sberbank.pprb.agent.gigaassistant.mapper.AclMapper;

/** Проверяет единственный helper: серверный fixture и начальный саджест по общей схеме ACL. */
class LocalHelperBoundaryTest {
    @Test
    void exposesFixtureAndDoesNotExposeSession() throws Exception {
        var operations =
                org.mockito.Mockito.mock(
                        ru.sberbank.pprb.agent.service.turn.SessionResponseRenderer.class);
        org.mockito.Mockito.when(
                        operations.operationSuggestions(
                                ru.sberbank.pprb.agent.model.enums.SessionState
                                        .CHOOSING_REQUEST_TYPE))
                .thenReturn(
                        java.util.List.of(
                                new ru.sberbank.pprb.agent.model.dto.turn.TurnSuggestionDTO(
                                        "Запросить статус",
                                        "status",
                                        ru.sberbank.pprb.agent.model.enums.SuggestionKind
                                                .COMMAND)));
        var http =
                MockMvcBuilders.standaloneSetup(
                                new LocalHelperController(
                                        new FixtureCatalog(),
                                        Mappers.getMapper(FixtureMapper.class),
                                        Mappers.getMapper(AclMapper.class),
                                        operations,
                                        "invest-pro",
                                        "enabled"))
                        .build();
        http.perform(get("/local-api/v1/fixtures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures[0].metadata.additional_info.length()").value(7))
                .andExpect(jsonPath("$.fixtures[0].payment.paymentId").value("test-payment-42"))
                .andExpect(jsonPath("$.suggestions[0].action_code").value("status"))
                .andExpect(jsonPath("$.suggestions[0].display_mode").value("BUTTON"))
                .andExpect(jsonPath("$.suggestions[0].performative").value("request"))
                .andExpect(jsonPath("$.availableCommands").doesNotExist());
        http.perform(get("/local-api/v1/sessions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
