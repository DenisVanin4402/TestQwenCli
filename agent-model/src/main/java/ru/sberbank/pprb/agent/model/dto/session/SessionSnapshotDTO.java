package ru.sberbank.pprb.agent.model.dto.session;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sberbank.pprb.agent.model.dto.payment.PreparationDTO;
import ru.sberbank.pprb.agent.model.dto.payment.TrustedPaymentContextDTO;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.model.enums.TurnOutcome;

/**
 * Сохранённые данные обращения, восстановленные из штатного хранилища машины состояний. Движок
 * возвращает снимок оркестратору после завершения транзакции для подготовки ответа. Снимок содержит
 * предметный результат, но не готовый текст или поля внешнего протокола.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionSnapshotDTO {
    /** UUID сессии, которой принадлежат сохранённая машина состояний и данные этого снимка. */
    private UUID sessionId;

    /**
     * Исходные данные платежа и его принадлежности организации и пользователю. Задаются при
     * создании сессии и используются для проверки последующих обращений к той же сессии.
     */
    private TrustedPaymentContextDTO context;

    /** Сохранённый шаг сценария, определяющий допустимые следующие действия пользователя. */
    private SessionState state;

    /**
     * Счётчик изменений представления сессии. Передаётся потребителю вместе с данными, чтобы тот
     * мог определить порядок обновлений; не служит условием допуска перехода машины состояний.
     */
    private long projectionVersion;

    /**
     * Последний выданный порядковый номер черновика. Сохраняется после отмены, чтобы следующая
     * подготовка получила новый номер в пределах той же сессии.
     */
    private long lastPreparationNo;

    /** Текущий черновик запроса статуса и согласие на него; после отмены черновика отсутствует. */
    private PreparationDTO preparation;

    /**
     * Последний сохранённый исход обработки сообщения. Оркестратор выбирает по нему текст ответа;
     * сам текст в состоянии машины не сохраняется.
     */
    private TurnOutcome lastResult;

    /** Request-Id сообщения, которому соответствует последний сохранённый исход обработки. */
    private UUID lastRequestId;
}
