package ru.sberbank.pprb.agent.investcorr.client;

import feign.FeignException;
import feign.codec.DecodeException;
import lombok.RequiredArgsConstructor;
import ru.sberbank.pprb.agent.investcorr.exception.InvestCorrCallException;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusRequest;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusResponse;

/**
 * Выполняет один HTTP-вызов corr через Feign и сгенерированный контракт, преобразует технические
 * ошибки в общий тип исключения адаптера. Сбой транспорта, ответа или его чтения не доказывает, что
 * внешний сервис не выполнил операцию, и не запускает повтор.
 */
@RequiredArgsConstructor
public class FeignInvestCorrClient implements InvestCorrClient {
    /** Spring Cloud клиент, чьи HTTP-сигнатуры сгенерированы из временного контракта corr. */
    private final InvestCorrHttpApi api;

    /**
     * Отправляет подготовленный запрос с ключом подтверждённой операции и возвращает ответ corr.
     * Ошибки HTTP, транспорта и декодирования передаются менеджеру с причиной сбоя; проверка
     * предметного содержания успешного ответа остаётся ответственностью менеджера.
     */
    @Override
    public StatusResponse submit(String idempotencyKey, StatusRequest request) {
        try {
            return api.submitStatus(idempotencyKey, request);
        } catch (DecodeException exception) {
            throw new InvestCorrCallException("INVALID_RESPONSE", exception);
        } catch (FeignException exception) {
            // Даже HTTP 4xx не доказывает непринятие запроса по согласованному временному
            // контракту.
            String reason =
                    exception.status() > 0 ? "HTTP_" + exception.status() : "TRANSPORT_ERROR";
            throw new InvestCorrCallException(reason, exception);
        } catch (RuntimeException exception) {
            throw new InvestCorrCallException("TRANSPORT_ERROR", exception);
        }
    }
}
