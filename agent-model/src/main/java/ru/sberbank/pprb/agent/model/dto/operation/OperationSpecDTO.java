package ru.sberbank.pprb.agent.model.dto.operation;

import java.util.*;
import lombok.*;
import ru.sberbank.pprb.agent.model.enums.*;

/** Библиотечно независимое описание операции и схемы её подтверждения. */
@Value
public class OperationSpecDTO {
    /** Внутренний код операции; при CONFIRM берётся только из сохранённой подготовки. */
    Operation operation;

    /** Код саджеста, по которому входной request выбирает операцию. */
    String actionCode;

    /** Подпись действия для пользователя. */
    String title;

    /** Текст клика, отображаемый в истории чата. */
    String messageText;

    /** Ключ локального шаблона предложения, без готового текста в FSM. */
    String proposalTemplateKey;

    /** Упорядоченная схема бизнес-полей; служебный номер добавляется отдельно. */
    List<ConfirmationFieldDTO> confirmationFields;

    /** Сохраняет описание операции и неизменяемую копию схемы подтверждения. */
    @Builder
    public OperationSpecDTO(
            Operation operation,
            String actionCode,
            String title,
            String messageText,
            String proposalTemplateKey,
            List<ConfirmationFieldDTO> confirmationFields) {
        this.operation = operation;
        this.actionCode = actionCode;
        this.title = title;
        this.messageText = messageText;
        this.proposalTemplateKey = proposalTemplateKey;
        this.confirmationFields = List.copyOf(confirmationFields);
    }
}
