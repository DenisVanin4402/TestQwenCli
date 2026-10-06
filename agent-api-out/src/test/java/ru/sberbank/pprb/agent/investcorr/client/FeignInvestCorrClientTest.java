package ru.sberbank.pprb.agent.investcorr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import ru.sberbank.pprb.agent.investcorr.config.InvestCorrConfiguration;
import ru.sberbank.pprb.agent.investcorr.exception.InvestCorrCallException;
import ru.sberbank.pprb.agent.investcorr.mapper.InvestCorrMapperImpl;
import ru.sberbank.pprb.agent.investcorr.service.InvestCorrAdapter;
import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;
import ru.sberbank.pprb.agent.model.enums.Operation;

/**
 * Проверяет настоящий generated Feign через локальный HTTP и отсутствие скрытых физических
 * повторов.
 */
class FeignInvestCorrClientTest {

    @Test
    @DisplayName(
            "Feign использует URL, заголовок и payload; технические ошибки выходят исключением без повтора")
    void realHttpContractAndTechnicalOutcomes() throws Exception {
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> response =
                new AtomicReference<>("{\"outcome\":\"ACCEPTED\",\"reference\":\"ref\"}");
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/prototype/v1/status-requests",
                exchange -> {
                    requests.incrementAndGet();
                    payload.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                    byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status.get(), body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            context()
                    .withPropertyValues(
                            "integrations.invest-corr.stub=disabled",
                            "integrations.invest-corr.url=http://127.0.0.1:"
                                    + server.getAddress().getPort())
                    .run(
                            context -> {
                                assertThat(context)
                                        .hasSingleBean(InvestCorrClient.class)
                                        .hasSingleBean(InvestCorrHttpApi.class);
                                assertThat(context).doesNotHaveBean(StubInvestCorrClient.class);
                                InvestCorrAdapter adapter =
                                        context.getBean(InvestCorrAdapter.class);
                                OperationContext input = submission();
                                adapter.callOperation(Operation.STATUS, input);
                                assertThat(key.get()).isEqualTo(input.getOperationId().toString());
                                assertThat(payload.get())
                                        .contains(
                                                input.getOperationId().toString(),
                                                "\"operation\":\"status\"",
                                                "\"paymentId\":\"payment\"");
                                for (int code : new int[] {400, 503}) {
                                    status.set(code);
                                    assertThatThrownBy(
                                                    () ->
                                                            adapter.callOperation(
                                                                    Operation.STATUS, input))
                                            .isInstanceOf(InvestCorrCallException.class);
                                }
                                status.set(200);
                                for (String body :
                                        new String[] {
                                            "invalid",
                                            "{}",
                                            "{\"outcome\":\"UNRECOGNIZED\"}",
                                            "{\"outcome\":\"ACCEPTED\"}",
                                            "{\"outcome\":\"REJECTED\"}"
                                        }) {
                                    response.set(body);
                                    assertThatThrownBy(
                                                    () ->
                                                            adapter.callOperation(
                                                                    Operation.STATUS, input))
                                            .isInstanceOf(InvestCorrCallException.class);
                                }
                                response.set(
                                        "{\"outcome\":\"REJECTED\",\"reasonCode\":\"DECLINED\",\"reasonText\":\"Не принят\"}");
                                assertThatThrownBy(
                                                () ->
                                                        adapter.callOperation(
                                                                Operation.STATUS, input))
                                        .isInstanceOf(InvestCorrCallException.class);
                                assertThat(requests.get()).isEqualTo(9);
                            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName(
            "Истёкший readTimeout Feign не вызывает скрытого повтора и не считается отказом банка")
    void timeoutIsOneUncertainAttempt() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/prototype/v1/status-requests",
                exchange -> {
                    requests.incrementAndGet();
                    try {
                        new CountDownLatch(1).await(400, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try {
            context()
                    .withPropertyValues(
                            "integrations.invest-corr.stub=disabled",
                            "integrations.invest-corr.url=http://127.0.0.1:"
                                    + server.getAddress().getPort(),
                            "spring.cloud.openfeign.client.config.default.readTimeout=100")
                    .run(
                            context -> {
                                assertThatThrownBy(
                                                () ->
                                                        context.getBean(InvestCorrAdapter.class)
                                                                .callOperation(
                                                                        Operation.STATUS,
                                                                        submission()))
                                        .isInstanceOf(InvestCorrCallException.class);
                                assertThat(requests.get()).isEqualTo(1);
                            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName(
            "Stub создаётся без Feign; отсутствующий режим и неверный реальный URL запрещают запуск")
    void exclusiveClientsAndInvalidSettings() {
        context()
                .withPropertyValues("integrations.invest-corr.stub=enabled")
                .run(
                        context -> {
                            assertThat(context)
                                    .hasSingleBean(StubInvestCorrClient.class)
                                    .doesNotHaveBean(InvestCorrHttpApi.class);
                        });
        context().run(context -> assertThat(context).hasFailed());
        context()
                .withPropertyValues("integrations.invest-corr.stub=invalid")
                .run(context -> assertThat(context).hasFailed());
        context()
                .withPropertyValues(
                        "integrations.invest-corr.stub=disabled",
                        "integrations.invest-corr.url=relative")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * Проверяет запрет бесконечного ожидания и повторов для имени клиента из конфигурации.
     * Индивидуальные значения должны учитываться и при замене стандартного имени corr.
     */
    @Test
    @DisplayName("Настройки конкретного corr не разрешают бесконечный тайм-аут или повтор")
    void rejectsUnsafeClientOverrides() {
        for (String setting :
                new String[] {
                    "readTimeout=0", "connectTimeout=-1", "retryer=feign.Retryer$Default"
                }) {
            context()
                    .withPropertyValues(
                            "integrations.invest-corr.stub=disabled",
                            "integrations.invest-corr.url=http://127.0.0.1:1",
                            "spring.cloud.openfeign.client.config.test-corr." + setting)
                    .run(context -> assertThat(context).hasFailed());
        }
        // Положительное значение клиента заменяет default, поэтому проверяется итоговая настройка.
        context()
                .withPropertyValues(
                        "integrations.invest-corr.stub=disabled",
                        "integrations.invest-corr.url=http://127.0.0.1:1",
                        "spring.cloud.openfeign.client.config.default.readTimeout=0",
                        "spring.cloud.openfeign.client.config.test-corr.readTimeout=1000")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /** Собирает штатные Spring Cloud converters и generated Feign без ручного HTTP-клиента. */
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                FeignAutoConfiguration.class,
                                PropertyPlaceholderAutoConfiguration.class,
                                HttpMessageConvertersAutoConfiguration.class,
                                JacksonAutoConfiguration.class))
                .withUserConfiguration(
                        InvestCorrConfiguration.class,
                        InvestCorrAdapter.class,
                        InvestCorrMapperImpl.class)
                .withPropertyValues(
                        "integrations.invest-corr.name=test-corr",
                        "integrations.invest-corr.stub-scenario=accepted",
                        "spring.cloud.openfeign.client.config.default.connectTimeout=1000",
                        "spring.cloud.openfeign.client.config.default.readTimeout=1000");
    }

    /** Отправка содержит собственный ID, не связанный с UUID пользовательского сообщения. */
    private OperationContext submission() {
        return new OperationContext(UUID.randomUUID(), "org", "user", "payment", null);
    }
}
