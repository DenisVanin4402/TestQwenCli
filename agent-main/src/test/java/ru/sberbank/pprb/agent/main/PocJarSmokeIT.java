package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запускает упакованный POC в отдельной JVM и проверяет продолжение диалога после перезапуска. */
class PocJarSmokeIT {
    /** Каждый запуск теста получает собственную файловую БД, не затрагивающую локальное демо. */
    @TempDir Path directory;

    @Test
    @DisplayName(
            "JAR сохраняет сводку в файловой H2; новая JVM восстанавливает её и принимает подтверждение")
    void restartsPackagedApplicationAndResumesConversation() throws Exception {
        UUID sessionId = UUID.randomUUID();
        int port = freePort();
        PocHttpClient http = new PocHttpClient(port);
        String originalSummary;
        com.fasterxml.jackson.databind.JsonNode proposal;
        Process first = start(port, "first");
        try {
            awaitReady(first, http, "first");
            UUID requestId = UUID.randomUUID();
            var response =
                    http.post(
                            sessionId,
                            requestId,
                            http.message(requestId, "request", "status", true));
            assertThat(response.statusCode()).isEqualTo(200);
            proposal = http.body(response);
            assertThat(proposal.path("state")).hasSize(2);
            originalSummary = proposal.at("/message/content/result").asText();
            assertThat(originalSummary).contains("42");
        } finally {
            stop(first);
        }

        // Вторая JVM использует ту же файловую БД и восстанавливает подготовку штатным persister.
        Process second = start(port, "second");
        try {
            awaitReady(second, http, "second");
            // Подтверждение в новой JVM возможно только при восстановленной подготовке.
            UUID requestId = UUID.randomUUID();
            var response = http.post(sessionId, requestId, http.confirmation(requestId, proposal));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(http.body(response).at("/metadata/final_message").asBoolean()).isTrue();
            requestId = UUID.randomUUID();
            assertThat(
                            http.post(sessionId, requestId, http.confirmation(requestId, proposal))
                                    .statusCode())
                    .isEqualTo(400);
        } finally {
            stop(second);
        }
    }

    /**
     * Выделяет локальный порт для теста; сокет закрывается перед запуском HTTP-сервера дочерней
     * JVM.
     */
    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Запускает именно Boot JAR, чтобы проверить упаковку зависимостей и профилей Maven. */
    private Process start(int port, String run) throws IOException {
        Path jar = Path.of("target", "agent-main-1.0-SNAPSHOT.jar").toAbsolutePath();
        assertThat(jar).isRegularFile();
        Path log = log(run);
        Files.createDirectories(log.getParent());
        return new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-jar",
                        jar.toString(),
                        "--spring.profiles.active=poc-local,h2",
                        "--spring.config.import=classpath:config/gigachat-prompts.yaml,optional:classpath:/absent-test-env.properties",
                        "--spring.ai.model.chat=none",
                        "--server.port=" + port,
                        "--spring.datasource.url=jdbc:h2:file:"
                                + directory.resolve("poc").toAbsolutePath()
                                + ";WRITE_DELAY=0")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    /** Ждёт фактический HTTP-ответ; падение процесса сообщает вместе с его собственным журналом. */
    private void awaitReady(Process process, PocHttpClient http, String run) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(process.isAlive()).withFailMessage(() -> readLog(run)).isTrue();
            try {
                if (http.get("/local-api/v1/fixtures").statusCode() == 200) {
                    return;
                }
            } catch (IOException startupInProgress) {
                // До завершения миграций HTTP-порт ещё может быть закрыт.
            }
            Thread.sleep(200);
        }
        throw new AssertionError("JAR не вышел на HTTP-готовность: " + readLog(run));
    }

    /** Останавливает только созданную тестом JVM и дожидается освобождения файловой БД. */
    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Оставляет стандартные диагностические файлы в target, отдельно для двух JVM. */
    private Path log(String run) {
        return Path.of("target", "jar-smoke", run + ".log").toAbsolutePath();
    }

    /** При ошибке старта включает журнал дочернего процесса в сообщение теста. */
    private String readLog(String run) {
        try {
            return Files.readString(log(run));
        } catch (IOException exception) {
            return "Не удалось прочитать журнал JAR: " + exception.getMessage();
        }
    }
}
