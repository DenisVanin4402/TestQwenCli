package ru.sberbank.pprb.agent.model.enums;

/** Результат классификации одного текстового сообщения, без согласия на отправку. */
public enum MessageRoute {
    COMMAND,
    REFERENCE,
    MIXED,
    UNCLEAR
}
