package ru.sberbank.pprb.agent.main;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Вызывает настоящий HTTP-вход POC синтетическими сообщениями ACL. */
final class PocHttpClient {
    /** Бюджет HTTP-проверки учитывает запуск и нагрузку стенда; NOWAIT проверяется отдельно. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final String address;
    private final String aclPrefix;
    private final Duration requestTimeout;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    PocHttpClient(int port) {
        this(port, "");
    }

    /** Выбирает основной ACL или его локальное зеркало при общем формате сообщений. */
    PocHttpClient(int port, String aclPrefix) {
        this(port, aclPrefix, REQUEST_TIMEOUT);
    }

    /** Реальный LLM-прогон учитывает длительность ограниченных технических попыток. */
    PocHttpClient(int port, String aclPrefix, Duration timeout) {
        address = "http://127.0.0.1:" + port;
        this.aclPrefix = aclPrefix;
        this.requestTimeout = timeout;
    }

    /**
     * Создаёт сообщение ACL; при первом обращении добавляет организацию, пользователя и тестовый
     * платёж.
     */
    ObjectNode message(UUID requestId, String performative, String action, boolean initial) {
        ObjectNode body = json.createObjectNode();
        ObjectNode message = body.putObject("message");
        message.put("version", "1.6");
        message.put("performative", performative);
        message.put("sender", "LOCAL_TEST");
        message.put("receiver", "invest-pro");
        message.put("conversation_id", "44444444-4444-4444-8444-444444444444");
        message.put("reply_with", requestId.toString());
        ObjectNode content = message.putObject("content");
        if (action != null) {
            content.put("action_code", action);
        }
        if (initial) {
            // Последующие сообщения могут обходиться без этих данных: приложение загружает их из
            // БД.
            ObjectNode metadata = body.putObject("metadata");
            metadata.putObject("organization").put("epk_id", "test-org-1");
            metadata.putObject("customer_info").put("digital_user_id", "test-user-1");
            var info = metadata.putArray("additional_info");
            String[][] fields = {
                {"paymentId", "test-payment-42"},
                {"paymentNumber", "42"},
                {"paymentDate", "2026-10-03"},
                {"amount", "12500.00"},
                {"currency", "RUB"},
                {"recipientName", "ООО Альфа"},
                {"organizationName", "ООО Вектор"}
            };
            for (String[] field : fields) {
                info.addObject().put("key", field[0]).put("value", field[1]);
            }
        }
        return body;
    }

    /** Явно возвращает параметры конкретного полученного предложения. */
    ObjectNode confirmation(UUID requestId, JsonNode proposal) {
        var input = message(requestId, "accept_propose", null, false);
        input.set("state", proposal.path("state").deepCopy());
        return input;
    }

    /** Передаёт новый ход с отдельными заголовками корреляции. */
    HttpResponse<String> post(UUID sessionId, UUID requestId, JsonNode body)
            throws IOException, InterruptedException {
        return postHeaders(sessionId.toString(), requestId.toString(), body);
    }

    /**
     * Позволяет E2E-сценариям проверить отсутствие и некорректную форму обязательных заголовков.
     */
    HttpResponse<String> postHeaders(String sessionId, String requestId, JsonNode body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(
                                URI.create(address + aclPrefix + "/api/v1/ai/agents/invest-pro"))
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json");
        if (sessionId != null) {
            request.header("Gigachat-Session-Id", sessionId);
        }
        if (requestId != null) {
            request.header("Request-Id", requestId);
        }
        return client.send(
                request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Читает каталог или ресурс экрана, не выполняя пользовательское действие. */
    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(address + path))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Разбирает JSON настоящего HTTP-ответа, чтобы проверить поля, полученные клиентом. */
    JsonNode body(HttpResponse<String> response) throws IOException {
        return json.readTree(response.body());
    }
}
