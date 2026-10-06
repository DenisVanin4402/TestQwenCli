package ru.sberbank.pprb.agent.investcorr.config;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки выбора единственного клиента corr; неправильный режим или адрес запрещает запуск
 * приложения.
 */
@Getter
@Setter
@ConfigurationProperties("integrations.invest-corr")
public class InvestCorrProperties implements InitializingBean {
    /** Имя Feign-клиента и ключ его индивидуальных транспортных настроек. */
    private String name;

    /**
     * enabled включает стенд без сети, disabled — настоящий Feign; режим задаётся явно при запуске.
     */
    private String stub;

    /** Базовый HTTP(S)-адрес corr, обязательный только при отключённой заглушке. */
    private String url;

    /**
     * Серверный сценарий стенда: принятие, отказ либо моделирование потерянного ответа; из ACL не
     * изменяется.
     */
    private StubScenario stubScenario;

    /** Проверяет режим и относящиеся к нему настройки до допуска клиентских вызовов. */
    @Override
    public void afterPropertiesSet() {
        if (!"enabled".equals(stub) && !"disabled".equals(stub)) {
            throw new IllegalArgumentException(
                    "integrations.invest-corr.stub: требуется enabled или disabled");
        }
        if ("disabled".equals(stub)) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "integrations.invest-corr.name обязателен при disabled");
            }
            validateUrl();
        } else if (stubScenario == null) {
            throw new IllegalArgumentException(
                    "Неизвестный integrations.invest-corr.stub-scenario");
        }
    }

    /** Проверяет адрес до создания реального клиента; ошибочная настройка не включает заглушку. */
    private void validateUrl() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "integrations.invest-corr.url обязателен при disabled");
        }
        URI uri = URI.create(url);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getFragment() != null
                || uri.getQuery() != null) {
            throw new IllegalArgumentException(
                    "integrations.invest-corr.url должен быть абсолютным HTTP(S)-адресом");
        }
    }
}
