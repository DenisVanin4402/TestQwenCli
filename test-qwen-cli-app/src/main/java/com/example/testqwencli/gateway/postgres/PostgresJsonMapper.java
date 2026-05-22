package com.example.testqwencli.gateway.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PostgresJsonMapper {

	private static final TypeReference<LinkedHashMap<String, Object>> STRING_OBJECT_MAP =
			new TypeReference<>() {
			};

	private final ObjectMapper objectMapper;

	public PostgresJsonMapper(ObjectMapper objectMapper) {
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
	}

	public String write(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Не удалось сериализовать JSON для PostgreSQL", exception);
		}
	}

	public Map<String, Object> readMap(String json) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, STRING_OBJECT_MAP);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Не удалось прочитать JSON из PostgreSQL", exception);
		}
	}

	public <T> T read(String json, Class<T> type) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, type);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Не удалось прочитать JSON из PostgreSQL", exception);
		}
	}
}
