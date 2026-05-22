package com.example.testqwencli.gateway.callback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

	public Optional<URI> callbackUrl(String clientService) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		ClientProperties clientProperties = clients.get(clientService);
		if (clientProperties == null || clientProperties.callbackUrl() == null) {
			return Optional.empty();
		}
		return Optional.of(clientProperties.callbackUrl());
	}

	public record ClientProperties(URI callbackUrl) {

		public ClientProperties {
			if (callbackUrl != null && !callbackUrl.isAbsolute()) {
				throw new IllegalArgumentException("callback-url должен быть абсолютным URI");
			}
		}
	}
}
