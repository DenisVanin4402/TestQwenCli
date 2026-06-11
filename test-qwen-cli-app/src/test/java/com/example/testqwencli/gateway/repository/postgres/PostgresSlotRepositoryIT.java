package com.example.testqwencli.gateway.repository.postgres;

import com.example.testqwencli.gateway.repository.SlotRepository;
import com.example.testqwencli.gateway.repository.SlotRepositoryContract;
import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
		"external-gateway.repository.type=postgres",
		"external-gateway.postgres.liquibase-enabled=true",
		"external-gateway.slots.sync-acquire-wait-mode=polling",
		"external-gateway.slots.lease-reap-interval-ms=600000",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
class PostgresSlotRepositoryIT extends PostgresIntegrationTestSupport implements SlotRepositoryContract {

	@Autowired
	private SlotRepository repository;

	@BeforeEach
	void cleanBeforeTest() {
		cleanGatewayTables();
	}

	@AfterEach
	void cleanAfterTest() {
		cleanGatewayTables();
	}

	@Override
	public SlotRepository repository() {
		return repository;
	}
}
