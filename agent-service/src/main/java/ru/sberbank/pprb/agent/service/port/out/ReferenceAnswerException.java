package ru.sberbank.pprb.agent.service.port.out;

/** Технический сбой справки; не означает отсутствия знаний. */
public class ReferenceAnswerException extends RuntimeException {
    public ReferenceAnswerException(String message) {
        super(message);
    }
}
