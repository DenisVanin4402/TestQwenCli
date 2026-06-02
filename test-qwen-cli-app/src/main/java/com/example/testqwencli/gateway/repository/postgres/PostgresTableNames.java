package com.example.testqwencli.gateway.repository.postgres;

import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PostgresTableNames {

	private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

	private final String schema;

	public PostgresTableNames(String schema) {
		validateIdentifier(schema);
		this.schema = schema;
	}

	public String slots() {
		return qualified("ext_slots");
	}

	public String syncWaiters() {
		return qualified("ext_sync_waiters");
	}

	public String requestQueue() {
		return qualified("ext_request_queue");
	}

	public String callbackDelivery() {
		return qualified("ext_callback_delivery");
	}

	public static void validateIdentifier(String value) {
		Objects.requireNonNull(value, "value must not be null");
		if (!IDENTIFIER.matcher(value).matches()) {
			throw new IllegalArgumentException("Недопустимое имя PostgreSQL schema: " + value);
		}
	}

	private String qualified(String table) {
		return schema + "." + table;
	}
}
