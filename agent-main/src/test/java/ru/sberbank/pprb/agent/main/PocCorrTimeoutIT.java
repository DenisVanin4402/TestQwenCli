package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Проверяет сетевой timeout отдельно от конкурентного E2E, которому нужен больший бюджет ожидания.
 * Corr успевает принять операцию, но удерживает ответ; агент возвращает ошибку и откатывает
 * локальное согласие.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "integrations.invest-corr.stub=disabled",
            "spring.cloud.openfeign.client.config.invest-corr.readTimeout=1000"
        })
@ActiveProfiles({"poc-local", "postgres"})
class PocCorrTimeoutIT {
    @org.springframework.beans.factory.annotation.Autowired
    ru.sberbank.pprb.agent.service.port.in.SessionQueryUseCase queries;

    /** Учёт физических запросов; сервер моделирует внешний эффект при каждом принятии. */
    private static final AtomicInteger calls = new AtomicInteger();

    /** Удерживает ответ до того момента, когда тест проверит timeout приложения. */
    private static final CountDownLatch release = new CountDownLatch(1);

    /** Отдельный сервер с задержкой, не разделяющий настройки конкурентного набора. */
    private static final HttpServer corr = server();

    @LocalServerPort private int port;

    /** Подключает выделенную PostgreSQL и управляемый HTTP-сервер. */
    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("POC_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> required("POC_TEST_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> required("POC_TEST_POSTGRES_PASSWORD"));
        registry.add(
                "integrations.invest-corr.url",
                () -> "http://127.0.0.1:" + corr.getAddress().getPort());
    }

    @Test
    void timeoutRollsBackConsentWithoutRetryingExternalEffect() throws Exception {
        PocHttpClient http = new PocHttpClient(port);
        UUID session = UUID.randomUUID();
        UUID prepare = UUID.randomUUID();
        var preparedResponse =
                http.post(session, prepare, http.message(prepare, "request", "status", true));
        assertThat(preparedResponse.statusCode()).isEqualTo(200);
        var proposal = http.body(preparedResponse);
        UUID confirm = UUID.randomUUID();
        try {
            var response = http.post(session, confirm, http.confirmation(confirm, proposal));
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(http.body(response).at("/metadata/final_message").asBoolean()).isFalse();
            assertThat(calls).hasValue(1);
            var saved = queries.find(session).orElseThrow();
            assertThat(saved.getState().name()).isEqualTo("AWAITING_CONFIRM");
            assertThat(saved.getPreparation().getConfirmedRequestId() != null).isFalse();
        } finally {
            release.countDown();
        }
    }

    /** Запускает сервер до создания Feign; принятие фиксируется до задержки ответа. */
    private static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/prototype/v1/status-requests",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        calls.incrementAndGet();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) {
                                throw new IOException(
                                        "Тест не завершил проверку timeout за 10 секунд");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось запустить тестовый corr", exception);
        }
    }

    /** Полная приёмка требует настоящую PostgreSQL и не должна незаметно переключаться на H2. */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Требуется " + name);
        return value;
    }

    /** Освобождает только ресурсы, созданные этим тестом. */
    @AfterAll
    static void close() {
        release.countDown();
        corr.stop(0);
    }
}
