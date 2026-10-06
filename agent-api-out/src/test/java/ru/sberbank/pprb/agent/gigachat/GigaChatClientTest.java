package ru.sberbank.pprb.agent.gigachat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chat.giga.springai.autoconfigure.GigaChatAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Проверяет SDK и OAuth через настоящий локальный HTTP, не подменяя ChatClient. */
class GigaChatClientTest {
    /** Подключает production-конфигурацию клиента через её настоящий пакет. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan("ru.sberbank.pprb.agent.gigachat.config")
    static class ClientConfiguration {}

    /** Проверяет payload и жизненный цикл токена, которым управляет SDK. */
    @Test
    void exchangesMessagesAndReusesOrRefreshesToken() throws Exception {
        try (var server = new GigaServer()) {
            context(server)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(ChatClient.class);
                                assertThat(server.authRequests.get()).isZero();
                                var client = context.getBean("gigaChatClient", ChatClient.class);
                                assertThat(call(client)).isEqualTo("Ответ");
                                assertThat(call(client)).isEqualTo("Ответ");
                                assertThat(server.authRequests.get()).isEqualTo(1);
                                assertThat(server.chatRequests.get()).isEqualTo(2);
                                assertThat(server.basic).isEqualTo("Basic synthetic-key");
                                assertThat(server.scope).isEqualTo("scope=GIGACHAT_API_PERS");
                                assertThat(UUID.fromString(server.rqUid)).isNotNull();
                                assertThat(server.bearer).isEqualTo("Bearer synthetic-token-1");
                                assertThat(server.payload.path("model").asText())
                                        .isEqualTo("GigaChat-3-Ultra");
                                assertThat(server.payload.path("max_tokens").asInt()).isEqualTo(64);
                                assertThat(
                                                server.payload
                                                        .path("messages")
                                                        .get(0)
                                                        .path("role")
                                                        .asText())
                                        .isEqualTo("system");
                                assertThat(
                                                server.payload
                                                        .path("messages")
                                                        .get(0)
                                                        .path("content")
                                                        .asText())
                                        .isEqualTo("Краткий ответ");
                                assertThat(
                                                server.payload
                                                        .path("messages")
                                                        .get(1)
                                                        .path("content")
                                                        .asText())
                                        .isEqualTo("Привет");
                            });
            server.shortLived = true;
            server.authRequests.set(0);
            context(server)
                    .run(
                            context -> {
                                var client = context.getBean("gigaChatClient", ChatClient.class);
                                call(client);
                                Thread.sleep(1600);
                                call(client);
                                assertThat(server.authRequests.get()).isEqualTo(2);
                                assertThat(server.bearer).isEqualTo("Bearer synthetic-token-2");
                            });
        }
    }

    /** На каждый отказ приходится ровно один физический запрос генерации. */
    @Test
    void rejectsHttpInvalidAndEmptyResponsesWithoutRetry() throws Exception {
        try (var server = new GigaServer()) {
            context(server)
                    .run(
                            context -> {
                                var client = context.getBean("gigaChatClient", ChatClient.class);
                                for (int status : new int[] {401, 429, 503}) {
                                    server.chatStatus = status;
                                    int before = server.chatRequests.get();
                                    assertThatThrownBy(() -> call(client))
                                            .isInstanceOf(RuntimeException.class);
                                    assertThat(server.chatRequests.get()).isEqualTo(before + 1);
                                }
                                server.chatStatus = 200;
                                for (String body :
                                        new String[] {
                                            "invalid-json",
                                            "{}",
                                            "{\"choices\":[]}",
                                            GigaServer.response("  ")
                                        }) {
                                    server.chatBody = body;
                                    int before = server.chatRequests.get();
                                    assertThatThrownBy(() -> call(client))
                                            .isInstanceOf(RuntimeException.class);
                                    assertThat(server.chatRequests.get()).isEqualTo(before + 1);
                                }
                            });
        }
    }

    /** OAuth и генерация имеют конечный read timeout и не повторяются после сбоя. */
    @Test
    void authFailureAndBothReadTimeoutsDoNotRetry() throws Exception {
        try (var server = new GigaServer()) {
            server.authStatus = 401;
            context(server)
                    .run(
                            context -> {
                                assertThatThrownBy(
                                                () ->
                                                        call(
                                                                context.getBean(
                                                                        "gigaChatClient",
                                                                        ChatClient.class)))
                                        .isInstanceOf(RuntimeException.class);
                                assertThat(server.authRequests.get()).isEqualTo(1);
                                assertThat(server.chatRequests.get()).isZero();
                            });
            server.authStatus = 200;
            server.authDelay = true;
            context(server)
                    .withPropertyValues("spring.ai.gigachat.internal.read-timeout=200ms")
                    .run(
                            context -> {
                                assertThatThrownBy(
                                                () ->
                                                        call(
                                                                context.getBean(
                                                                        "gigaChatClient",
                                                                        ChatClient.class)))
                                        .isInstanceOf(RuntimeException.class);
                                assertThat(server.authRequests.get()).isEqualTo(2);
                                assertThat(server.chatRequests.get()).isZero();
                            });
            server.authDelay = false;
            server.chatDelay = true;
            context(server)
                    .withPropertyValues("spring.ai.gigachat.internal.read-timeout=200ms")
                    .run(
                            context -> {
                                assertThatThrownBy(
                                                () ->
                                                        call(
                                                                context.getBean(
                                                                        "gigaChatClient",
                                                                        ChatClient.class)))
                                        .isInstanceOf(RuntimeException.class);
                                assertThat(server.chatRequests.get()).isEqualTo(1);
                            });
        }
    }

    /** Единый синтетический запрос позволяет проверить передачу обеих ролей сообщений. */
    private String call(ChatClient client) {
        return client.prompt().system("Краткий ответ").user("Привет").call().content();
    }

    /** Все URL замкнуты на локальный сервер; реальные credentials здесь не используются. */
    private ApplicationContextRunner context(GigaServer server) {
        return new ApplicationContextRunner()
                .withInitializer(
                        new org.springframework.boot.test.context
                                .ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.import=classpath:config/gigachat-prompts.yaml")
                .withConfiguration(
                        AutoConfigurations.of(
                                JacksonAutoConfiguration.class,
                                        HttpMessageConvertersAutoConfiguration.class,
                                RestClientAutoConfiguration.class,
                                        SpringAiRetryAutoConfiguration.class,
                                GigaChatAutoConfiguration.class, ChatClientAutoConfiguration.class))
                .withUserConfiguration(ClientConfiguration.class)
                .withPropertyValues(
                        "spring.ai.model.chat=gigachat",
                        "spring.ai.model.image=none",
                        "spring.ai.gigachat.embedding.enabled=false",
                        "spring.ai.retry.max-attempts=1",
                        "spring.ai.gigachat.auth.bearer.api-key=synthetic-key",
                        "spring.ai.gigachat.auth.scope=GIGACHAT_API_PERS",
                        "spring.ai.gigachat.auth.bearer.url=" + server.url() + "/oauth",
                        "spring.ai.gigachat.base-url=" + server.url() + "/v1",
                        "spring.ai.gigachat.internal.connect-timeout=1s",
                        "spring.ai.gigachat.internal.read-timeout=3s",
                        "spring.ai.gigachat.chat.options.model=GigaChat-3-Ultra",
                        "spring.ai.gigachat.chat.options.max-tokens=64",
                        "spring.ai.gigachat.chat.options.internal-tool-execution-enabled=false");
    }

    /** Локальные ответы протокола; поток нужен, чтобы задержанный OAuth не блокировал chat. */
    private static class GigaServer implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        final AtomicInteger authRequests = new AtomicInteger();
        final AtomicInteger chatRequests = new AtomicInteger();
        volatile int authStatus = 200;
        volatile int chatStatus = 200;
        volatile boolean authDelay;
        volatile boolean chatDelay;
        volatile boolean shortLived;
        volatile String basic;
        volatile String scope;
        volatile String rqUid;
        volatile String bearer;
        volatile JsonNode payload;
        volatile String chatBody = response("Ответ");

        /** Обслуживает только OAuth и генерацию, считая эти запросы раздельно. */
        GigaServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext(
                    "/oauth",
                    exchange -> {
                        int number = authRequests.incrementAndGet();
                        basic = exchange.getRequestHeaders().getFirst("Authorization");
                        rqUid = exchange.getRequestHeaders().getFirst("RqUID");
                        scope =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        long expires = System.currentTimeMillis() + (shortLived ? 1500 : 600_000);
                        send(
                                exchange,
                                authStatus,
                                authStatus == 200
                                        ? "{\"access_token\":\"synthetic-token-"
                                                + number
                                                + "\",\"expires_at\":"
                                                + expires
                                                + "}"
                                        : "{\"error\":\"unauthorized\"}",
                                authDelay);
                    });
            server.createContext(
                    "/v1/chat/completions",
                    exchange -> {
                        chatRequests.incrementAndGet();
                        bearer = exchange.getRequestHeaders().getFirst("Authorization");
                        payload = new ObjectMapper().readTree(exchange.getRequestBody());
                        send(exchange, chatStatus, chatBody, chatDelay);
                    });
            server.start();
        }

        /** Минимальный ответ протокола GigaChat с заданным текстом. */
        static String response(String text) {
            return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                    + text
                    + "\"},\"finish_reason\":\"stop\"}],\"model\":\"GigaChat-3-Ultra\","
                    + "\"created\":1,\"object\":\"chat.completion\",\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
        }

        /** Задержка превышает тестовый timeout; прерывание закрывает обмен при остановке. */
        void send(HttpExchange exchange, int status, String body, boolean delay)
                throws IOException {
            try (exchange) {
                if (delay) {
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }

        /** Возвращает фактически выделенный локальный адрес. */
        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** Освобождает сокет и обработчики, включая задержанные ответы. */
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
