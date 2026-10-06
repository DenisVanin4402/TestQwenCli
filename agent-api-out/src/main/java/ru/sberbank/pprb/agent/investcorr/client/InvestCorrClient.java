package ru.sberbank.pprb.agent.investcorr.client;

import ru.sberbank.pprb.agent.investcorr.generated.model.StatusRequest;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusResponse;

/**
 * Граница одного обращения к corr по временному HTTP-контракту. Конфигурация выбирает реальный
 * HTTP-клиент либо локальную заглушку; автоматические повторы не входят в контракт клиента.
 */
public interface InvestCorrClient {
    /**
     * Передаёт подготовленный запрос с ключом, совпадающим с UUID подтверждённой внешней операции.
     * Возвращает ответ для проверки менеджером либо сообщает ошибку; повторного вызова при сбое не
     * выполняет.
     */
    StatusResponse submit(String idempotencyKey, StatusRequest request);
}
