package ru.sberbank.pprb.agent.gigachat;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.util.StringUtils;

/** Логирует промпты/ответ с callId и отклоняет пустой ответ модели. */
@Slf4j
public class GigaChatResponseAdvisor implements CallAdvisor {
    /** Логирует и проверяет вызов без изменения запроса или повторной генерации. */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        UUID callId = UUID.randomUUID();
        log.info(
                "GigaChat request: callId={}, messages={}",
                callId,
                request.prompt().getInstructions().stream()
                        .map(message -> message.getMessageType() + ": " + message.getText())
                        .toList());
        ChatClientResponse response;
        try {
            response = chain.nextCall(request);
        } catch (RuntimeException exception) {
            log.info(
                    "GigaChat response: callId={}, errorType={}",
                    callId,
                    exception.getClass().getSimpleName());
            throw exception;
        }
        log.info(
                "GigaChat response: callId={}, text={}",
                callId,
                response == null
                                || response.chatResponse() == null
                                || response.chatResponse().getResult() == null
                                || response.chatResponse().getResult().getOutput() == null
                        ? null
                        : response.chatResponse().getResult().getOutput().getText());
        if (response == null
                || response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null
                || !StringUtils.hasText(
                        response.chatResponse().getResult().getOutput().getText())) {
            throw new IllegalStateException("GigaChat вернул пустой текстовый ответ");
        }
        return response;
    }

    /** Постоянное техническое имя не содержит данных запроса. */
    @Override
    public String getName() {
        return "GigaChatResponseAdvisor";
    }

    /** Проверка оборачивает штатный завершающий advisor модели. */
    @Override
    public int getOrder() {
        return 0;
    }
}
