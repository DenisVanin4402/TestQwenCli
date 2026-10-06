package ru.sberbank.pprb.agent.investcorr.exception;

/** Сообщает, что попытка не дала достоверного ответа; не означает гарантированного отказа corr. */
public class InvestCorrCallException extends RuntimeException {
    /**
     * Сохраняет контролируемый технический код причины без произвольного текста удалённого сервиса.
     */
    public InvestCorrCallException(String reason) {
        super(reason);
    }

    /**
     * Оставляет исходную техническую причину для диагностики, сохраняя безопасный код для журнала.
     */
    public InvestCorrCallException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
