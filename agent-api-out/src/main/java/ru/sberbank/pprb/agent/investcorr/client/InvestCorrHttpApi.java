package ru.sberbank.pprb.agent.investcorr.client;

import org.springframework.cloud.openfeign.FeignClient;
import ru.sberbank.pprb.agent.investcorr.config.CorrFeignConfiguration;
import ru.sberbank.pprb.agent.investcorr.generated.api.InvestCorrFeignApi;

/**
 * Подключает сгенерированный контракт corr к Spring Cloud OpenFeign. Имя и адрес берутся из
 * конфигурации приложения; HTTP-методы и DTO остаются в OpenAPI и не дублируются вручную.
 */
@FeignClient(
        name = "${integrations.invest-corr.name}",
        url = "${integrations.invest-corr.url}",
        configuration = CorrFeignConfiguration.class)
public interface InvestCorrHttpApi extends InvestCorrFeignApi {}
