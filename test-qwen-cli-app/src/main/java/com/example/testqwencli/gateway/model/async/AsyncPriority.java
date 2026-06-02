package com.example.testqwencli.gateway.model.async;

/**
 * Приоритет async-задачи при выборе dispatcher-ом.
 *
 * <p>Числовой вес используется в сортировке очереди: чем больше {@link #weight()},
 * тем раньше задача будет выбрана среди доступных {@code PENDING} задач.</p>
 */
public enum AsyncPriority {
	/**
	 * Повышенный приоритет для задач, которые должны обгонять обычную очередь.
	 */
	HIGH(100),
	/**
	 * Обычный приоритет фоновой async-обработки.
	 */
	LOW(10);

	private final int weight;

	AsyncPriority(int weight) {
		this.weight = weight;
	}

	/**
	 * @return числовой вес приоритета для {@code ORDER BY} в очереди
	 */
	public int weight() {
		return weight;
	}
}
