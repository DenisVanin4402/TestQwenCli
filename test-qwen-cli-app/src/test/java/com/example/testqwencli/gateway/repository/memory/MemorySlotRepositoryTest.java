package com.example.testqwencli.gateway.repository.memory;

import com.example.testqwencli.gateway.repository.SlotRepository;
import com.example.testqwencli.gateway.repository.SlotRepositoryContract;

class MemorySlotRepositoryTest implements SlotRepositoryContract {

	private final MemorySlotRepository repository = new MemorySlotRepository(
			SlotRepositoryContract.contractSlotProperties());

	@Override
	public SlotRepository repository() {
		return repository;
	}
}
