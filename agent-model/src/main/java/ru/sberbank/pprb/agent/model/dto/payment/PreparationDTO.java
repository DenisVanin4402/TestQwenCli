package ru.sberbank.pprb.agent.model.dto.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sberbank.pprb.agent.model.enums.Operation;

/**
 * Черновик запроса статуса с данными платежа, которые пользователь должен отдельно подтвердить.
 * Машина состояний сохраняет этот снимок, а оркестратор готовит по нему сводку для пользователя.
 * Создание нового черновика требует нового согласия и само по себе не разрешает отправку.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreparationDTO {
    /**
     * UUID конкретного черновика. При новой подготовке меняется; после подтверждения становится
     * идентификатором внешней операции, передаваемой менеджеру.
     */
    private UUID preparationId;

    /** Сообщение, создавшее подготовку; не меняется последующими ходами. */
    private UUID preparedRequestId;

    /** Независимый набор значений, показанный в предложении. */
    private List<ConfirmationValueDTO> confirmationSnapshot;

    /**
     * Порядковый номер черновика внутри сессии. Увеличивается при новой подготовке и передаётся в
     * представление сессии, чтобы потребитель мог различать показанные пользователю черновики.
     */
    private long preparationNo;

    /** Операция, которую пользователь подтверждает вместе с данными этого черновика. */
    private Operation operation;

    /**
     * Снимок исходных данных платежа на момент подготовки. Оркестратор использует его для сводки, а
     * действие подтверждения — для внешнего вызова, согласованного пользователем.
     */
    private TrustedPaymentContextDTO context;

    /**
     * Request-Id отдельного сообщения, которым пользователь подтвердил именно этот черновик. До
     * согласия значение отсутствует и отправка запрещена.
     */
    private UUID confirmedRequestId;

    /**
     * Момент сохранения согласия на текущий черновик. Отсутствует до подтверждения и не переносится
     * в новую подготовку.
     */
    private Instant confirmedAt;
}
