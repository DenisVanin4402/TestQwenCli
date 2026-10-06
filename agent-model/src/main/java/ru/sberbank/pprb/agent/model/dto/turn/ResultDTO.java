package ru.sberbank.pprb.agent.model.dto.turn;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.ResultKind;

/**
 * Подготовленный ответ на одно сообщение пользователя: вид результата, причина и понятный текст.
 * Оркестратор формирует его после обработки сценария, а входной адаптер выбирает по нему
 * HTTP-статус и поля протокола. Этот объект не хранится в машине состояний: там сохраняется
 * предметный исход.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultDTO {
    /**
     * Успех или ошибка обработки сообщения. Ошибка вызова corr, отказ в принятии запроса или
     * некорректный ответ corr приводят к ERROR; SUCCESS не означает получение статуса платежа.
     */
    private ResultKind kind;

    /**
     * Код причины для адаптера и клиента, например CONFIRMATION_REQUIRED. Не является банковским
     * статусом платежа.
     */
    private ResultCode code;

    /**
     * Подготовленный приложением текст ответа пользователю; технический текст upstream сюда
     * напрямую не переносится.
     */
    private String message;
}
