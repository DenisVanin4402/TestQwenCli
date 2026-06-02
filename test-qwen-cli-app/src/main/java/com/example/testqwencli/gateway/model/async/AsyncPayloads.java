package com.example.testqwencli.gateway.model.async;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Утилита для безопасного копирования payload/result map-структур.
 *
 * <p>Модели gateway принимают произвольный JSON как {@code Map<String, Object>}.
 * Чтобы объект запроса не менялся после валидации или записи в очередь, эта
 * утилита делает глубокую immutable-копию вложенных {@link Map} и {@link List}.</p>
 */
public final class AsyncPayloads {

	private AsyncPayloads() {
	}

	/**
	 * Создает глубокую immutable-копию JSON-подобной map-структуры.
	 *
	 * @param source исходная map, где все ключи должны быть строками
	 * @return immutable-копия с рекурсивно скопированными вложенными map/list
	 * @throws IllegalArgumentException если во вложенной map найден нестроковый ключ
	 */
	public static Map<String, Object> copyMap(Map<String, Object> source) {
		Objects.requireNonNull(source, "source must not be null");
		return copyStringObjectMap(source);
	}

	private static Map<String, Object> copyStringObjectMap(Map<?, ?> source) {
		LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalArgumentException("Ключи payload должны быть строками");
			}
			copy.put(key, copyValue(entry.getValue()));
		}
		return Collections.unmodifiableMap(copy);
	}

	private static Object copyValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return copyStringObjectMap(map);
		}
		if (value instanceof List<?> list) {
			return list.stream()
					.map(AsyncPayloads::copyValue)
					.toList();
		}
		return value;
	}
}
