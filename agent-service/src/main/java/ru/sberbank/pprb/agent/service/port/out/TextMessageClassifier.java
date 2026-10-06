package ru.sberbank.pprb.agent.service.port.out;

import java.util.List;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.dto.turn.MessageAnalysisDTO;

/** Разбирает самостоятельный текст без чтения состояния или выполнения операции. */
public interface TextMessageClassifier {
    /**
     * Возвращает проверенный JSON-результат либо техническую ошибку; сам DTO не может быть null.
     */
    MessageAnalysisDTO classify(String text, List<OperationSpecDTO> operations);
}
