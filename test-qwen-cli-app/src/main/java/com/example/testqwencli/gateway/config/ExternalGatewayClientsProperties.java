package com.example.testqwencli.gateway.config;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация сервисов-клиентов external gateway.
 *
 * @param clients map настроек по имени clientService
 */
@ConfigurationProperties(prefix = "external-gateway")
public record ExternalGatewayClientsProperties(
		Map<String, ClientProperties> clients
) {

	public ExternalGatewayClientsProperties {
		if (clients == null) {
			clients = Map.of();
		}
		clients = Map.copyOf(clients);
	}

	/**
	 * Возвращает allow-listed callback URL для сервиса-клиента.
	 *
	 * @param clientService имя сервиса-клиента
	 * @return callback URL, если он задан
	 */
	public Optional<URI> callbackUrl(String clientService) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		ClientProperties clientProperties = clients.get(clientService);
		if (clientProperties == null || clientProperties.callbackUrl() == null) {
			return Optional.empty();
		}
		return Optional.of(clientProperties.callbackUrl());
	}

	/**
	 * Настройки одного сервиса-клиента.
	 *
	 * @param callbackUrl абсолютный URL, на который gateway доставляет callback
	 */
	public record ClientProperties(URI callbackUrl) {

		public ClientProperties {
			if (callbackUrl != null && !callbackUrl.isAbsolute()) {
				throw new IllegalArgumentException("callback-url должен быть абсолютным URI");
			}
		}
	}
}
