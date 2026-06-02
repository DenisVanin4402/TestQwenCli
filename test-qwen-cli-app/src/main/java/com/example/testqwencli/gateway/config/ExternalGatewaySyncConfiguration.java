package com.example.testqwencli.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
		ExternalGatewaySyncProperties.class,
		ExternalGatewayUpstreamProperties.class
})
public class ExternalGatewaySyncConfiguration {
}
