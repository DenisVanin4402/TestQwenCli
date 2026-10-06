package ru.sberbank.pprb.agent.investcorr.config;

import feign.Retryer;
import java.util.function.Function;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Bean;

/**
 * Настраивает только именованный corr-клиент. Подключается из @FeignClient и не сканируется как
 * общая конфигурация: остальные будущие исходящие клиенты не наследуют политику POC.
 */
public class CorrFeignConfiguration {
    /**
     * Проверяет эффективные свойства клиента и закрепляет отсутствие повторов после применения
     * настроек Feign. Проверка относится к подключению и чтению, а не к фабрике Retryer.
     */
    @Bean
    FeignBuilderCustomizer corrClientSettings(
            FeignClientProperties properties, InvestCorrProperties corr) {
        FeignClientProperties.FeignClientConfiguration defaults =
                properties.getConfig().get(properties.getDefaultConfig());
        FeignClientProperties.FeignClientConfiguration client =
                properties.getConfig().get(corr.getName());
        validateTimeout(
                effective(
                        defaults,
                        client,
                        FeignClientProperties.FeignClientConfiguration::getConnectTimeout));
        validateTimeout(
                effective(
                        defaults,
                        client,
                        FeignClientProperties.FeignClientConfiguration::getReadTimeout));
        if (effective(defaults, client, FeignClientProperties.FeignClientConfiguration::getRetryer)
                != null) {
            throw new IllegalArgumentException(
                    "invest-corr не допускает настройку retryer: разрешён один вызов");
        }
        return builder -> builder.retryer(Retryer.NEVER_RETRY);
    }

    /** Возвращает переопределение конкретного клиента либо унаследованное общее значение. */
    private <T> T effective(
            FeignClientProperties.FeignClientConfiguration defaults,
            FeignClientProperties.FeignClientConfiguration client,
            Function<FeignClientProperties.FeignClientConfiguration, T> getter) {
        T override = client == null ? null : getter.apply(client);
        return override != null ? override : defaults == null ? null : getter.apply(defaults);
    }

    /**
     * Отсутствующая настройка оставляет конечный стандартный тайм-аут Feign; явно бесконечная
     * запрещена.
     */
    private void validateTimeout(Integer timeout) {
        if (timeout != null && timeout <= 0) {
            throw new IllegalArgumentException(
                    "connectTimeout/readTimeout invest-corr должны быть положительными");
        }
    }
}
