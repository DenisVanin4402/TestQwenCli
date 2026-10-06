package ru.sberbank.pprb.agent.investcorr.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.sberbank.pprb.agent.investcorr.client.FeignInvestCorrClient;
import ru.sberbank.pprb.agent.investcorr.client.InvestCorrHttpApi;
import ru.sberbank.pprb.agent.investcorr.client.StubInvestCorrClient;

/**
 * Создаёт ровно одну реализацию клиента по настройке; в режиме заглушки Feign вообще не
 * регистрируется.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvestCorrProperties.class)
public class InvestCorrConfiguration {

    /** Создаёт локальный стенд без HTTP-клиента и без сетевых соединений. */
    @Bean
    @ConditionalOnProperty(
            prefix = "integrations.invest-corr",
            name = "stub",
            havingValue = "enabled")
    StubInvestCorrClient stubInvestCorrClient(InvestCorrProperties properties) {
        return new StubInvestCorrClient(properties.getStubScenario());
    }

    /** Регистрирует Feign-клиент со сгенерированным контрактом только в реальном режиме. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "integrations.invest-corr",
            name = "stub",
            havingValue = "disabled")
    @EnableFeignClients(clients = InvestCorrHttpApi.class)
    public static class RealClientConfiguration {

        /**
         * Создаёт обёртку сгенерированного API, которая выполняет один HTTP-вызов и преобразует
         * ошибки транспорта и ответа в общий тип ошибки corr-клиента.
         */
        @Bean
        FeignInvestCorrClient feignInvestCorrClient(InvestCorrHttpApi api) {
            return new FeignInvestCorrClient(api);
        }
    }
}
