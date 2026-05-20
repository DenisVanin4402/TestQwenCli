# Состояние задач: REST API для управления задачами

## Текущая фаза

Implementation — начало работы, задачи в процессе выполнения.

## Прогресс

| Задача | Статус | Примечания |
|--------|--------|------------|
| TSK-1 | pending | Task.java + TaskStatus.java |
| TSK-2 | pending | TaskRepository.java |
| TSK-3 | pending | DTOs + ErrorEnvelope |
| TSK-4 | pending | POST + GET list |
| TSK-5 | pending | GET by ID, PUT, DELETE |
| TSK-6 | pending | UUID validation + GlobalExceptionHandler |
| TSK-7 | pending | Integration tests |

## Блокеры

- None

## Запущенные команды

- TBD

## Матрица трассировки

| AC | Описание | Статус |
|----|----------|--------|
| AC-1 | POST 201 с UUID, TODO, timestamps | pending |
| AC-2..4 | POST validation → 400 | pending |
| AC-5 | GET list → 200, пустой массив | pending |
| AC-6,7 | GET by ID → 200 / 404 | pending |
| AC-8,9 | PUT → 200 / 404 | pending |
| AC-10,11 | DELETE → 200 / 404 | pending |
| AC-12 | Non-UUID → 400 | pending |
| AC-13,14 | Content-Type + ErrorEnvelope | pending |
