package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sberbank.pprb.agent.service.port.out.WorkflowManager;

/**
 * Проверяет ответ настоящего HTTP-входа при ошибке внешнего менеджера: клиент получает failure, а
 * последующее чтение видит черновик до подтверждения, восстановленный после отката транзакции.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:http-rollback;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"poc-local", "h2"})
class PocHttpRollbackTest {
    @org.springframework.beans.factory.annotation.Autowired
    ru.sberbank.pprb.agent.service.port.in.SessionQueryUseCase queries;

    @LocalServerPort int port;
    @MockitoBean WorkflowManager workflow;

    /**
     * Сбой менеджера при подтверждении возвращает HTTP 500 и нефинальный ACL failure. Последующий
     * внутренний сервис показывает ожидание согласия без сохранённого подтверждения; менеджер
     * вызван ровно один раз.
     */
    @Test
    void failedActionReturnsFailureAndPreservesPreparation() throws Exception {
        PocHttpClient http = new PocHttpClient(port);
        UUID session = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var preparedResponse =
                http.post(session, request, http.message(request, "request", "status", true));
        assertThat(preparedResponse.statusCode()).isEqualTo(200);
        var proposal = http.body(preparedResponse);
        doThrow(new IllegalStateException("corr failed"))
                .when(workflow)
                .callOperation(any(), any());
        request = UUID.randomUUID();
        var response = http.post(session, request, http.confirmation(request, proposal));
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(http.body(response).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(http.body(response).at("/metadata/final_message").asBoolean()).isFalse();
        var restored = queries.find(session).orElseThrow();
        assertThat(restored.getState().name()).isEqualTo("AWAITING_CONFIRM");
        assertThat(restored.getPreparation().getConfirmedRequestId()).isNull();
        verify(workflow).callOperation(any(), any());
    }
}
