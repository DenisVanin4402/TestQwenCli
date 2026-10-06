package ru.sberbank.pprb.agent.service.port.out;

/** Технический отказ разбора; сообщения провайдера не передаются клиенту. */
public class TextAnalysisException extends RuntimeException {
    public TextAnalysisException(String message) {
        super(message);
    }
}
