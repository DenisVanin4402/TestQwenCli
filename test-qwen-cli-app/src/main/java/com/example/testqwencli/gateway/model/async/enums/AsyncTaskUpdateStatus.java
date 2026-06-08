package com.example.testqwencli.gateway.model.async.enums;

/**
 * Итог команды изменения async-задачи.
 */
public enum AsyncTaskUpdateStatus {
	/**
	 * Команда применена, задача обновлена.
	 */
	UPDATED,
	/**
	 * Задача не найдена.
	 */
	NOT_FOUND,
	/**
	 * Задача найдена, но ее текущее состояние запрещает команду.
	 */
	CONFLICT
}
