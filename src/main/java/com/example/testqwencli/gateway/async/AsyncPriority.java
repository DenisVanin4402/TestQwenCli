package com.example.testqwencli.gateway.async;

public enum AsyncPriority {
	HIGH(100),
	LOW(10);

	private final int weight;

	AsyncPriority(int weight) {
		this.weight = weight;
	}

	public int weight() {
		return weight;
	}
}
