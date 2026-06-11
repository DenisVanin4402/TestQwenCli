package com.example.testqwencli.gateway.repository.memory;

import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.repository.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.CallbackDeliveryRepositoryContract;

class MemoryCallbackDeliveryRepositoryTest implements CallbackDeliveryRepositoryContract {

	private final CallbackDeliveryRepository repository = new MemoryCallbackDeliveryRepository();

	private final AsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();

	@Override
	public CallbackDeliveryRepository repository() {
		return repository;
	}

	@Override
	public AsyncTaskRepository taskRepository() {
		return taskRepository;
	}
}
