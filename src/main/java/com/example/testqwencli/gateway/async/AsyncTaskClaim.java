package com.example.testqwencli.gateway.async;

import java.util.Map;
import java.util.Objects;

public record AsyncTaskClaim(
		AsyncTask task,
		Map<String, Object> payload
) {

	public AsyncTaskClaim {
		Objects.requireNonNull(task, "task must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		payload = AsyncPayloads.copyMap(payload);
	}
}
