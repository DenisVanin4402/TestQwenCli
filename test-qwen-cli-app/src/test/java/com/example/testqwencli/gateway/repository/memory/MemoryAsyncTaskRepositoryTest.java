package com.example.testqwencli.gateway.repository.memory;

import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.repository.AsyncTaskRepositoryContract;

class MemoryAsyncTaskRepositoryTest implements AsyncTaskRepositoryContract {

	private final AsyncTaskRepository repository = new MemoryAsyncTaskRepository();

	@Override
	public AsyncTaskRepository repository() {
		return repository;
	}
}
