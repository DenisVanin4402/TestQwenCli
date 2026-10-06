/**
 * Хранение сессий: постоянная строка блокировки, репозитории и штатный JPA persister Spring
 * Statemachine со стабильной сериализацией. Жизненным циклом FSM и рабочей транзакцией управляет
 * agent-service; этот модуль не зависит от прикладных guards/actions.
 */
package ru.sberbank.pprb.agent.db;
