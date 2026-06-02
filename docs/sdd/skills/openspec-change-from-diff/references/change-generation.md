# Генерация diff-based change.md

Этот reference описывает, как превращать changed files и context files в аналитический `change.md`.

## 1. Header

Diff-based change всегда содержит:

```md
> **Мастер-спецификация**: openspec/<service>/
>
> **Manifest**: openspec/<service>/_sdd/manifest.yaml
>
> **Spec update mode**: branch-diff
>
> **Base ref**: <base_ref>
>
> **Analyst ref**: <analyst_ref>
>
> **Diff mode**: three-dot
>
> **Merge base**: <sha>
>
> **Diff command**: git diff --find-renames --find-copies <base_ref>...<analyst_ref> -- openspec/<service>/
```

## 2. Status policy

| Input | Статус |
|---|---|
| `approval=draft` | `На согласовании` |
| approval не указан | `На согласовании` |
| `approval=approved` | `Согласовано` |

Не ставь `Согласовано` без явного `approval=approved`.

## 3. Section 0

Раздел `## 0. Источники изменения` содержит:

- таблицу Git refs;
- таблицу changed files;
- таблицу context files.

Changed files должны иметь роль в изменении:

| Status | Роль |
|---|---|
| `added` | `ADDED requirement source` |
| `modified` | `MODIFIED requirement source` |
| `deleted` | `REMOVED requirement source` |
| `renamed` | `RENAMED requirement source` |
| `copied` | `COPIED requirement source` |
| `typechanged` | `TYPECHANGED requirement source` |

## 4. Mapping статусов

- `added` files превращаются в ADDED требования.
- `modified` files превращаются в MODIFIED требования.
- `deleted` files превращаются в REMOVED требования.
- `renamed` files анализируются как rename плюс возможные content changes.
- `copied` files анализируются как новый источник требований с lineage.
- `typechanged` files требуют отдельного предупреждения и проверки readable/binary статуса.

## 5. Разделы без изменений

Если область не затронута, пиши ровно:

```text
Нет изменений.
```

Не используй варианты:

- `не применимо`;
- `n/a`;
- пустой раздел;
- длинное объяснение отсутствия изменений.

## 6. Не копировать patch

Patch можно использовать как источник, но итоговый `change.md` должен быть аналитическим:

- что изменилось в требованиях;
- зачем это нужно, если мотивация выводится из документов;
- какие правила, контракты, модели, FSM, ошибки, НФТ затронуты;
- какие acceptance criteria проверяют результат;
- какие вопросы остались открытыми.

## 7. Acceptance criteria

Критерии приемки строятся из:

- changed files;
- context files;
- FSM transitions;
- business invariants;
- API/integration contracts;
- error handling;
- configuration and migration implications.

Формат:

```md
| # | КОГДА | ТОГДА |
|---|-------|-------|
```

Каждый критерий должен быть проверяемым через тест, запрос, diff review или наблюдение.

## 8. Confidence

`Высокая`, если:

- manifest найден в analyst ref;
- diff небольшой или средний;
- нет binary-only изменений;
- changed/context files прочитаны успешно;
- нет противоречий.

`Средняя`, если:

- manifest найден только в base ref;
- есть stale manifest warnings;
- diff большой;
- часть контекста прочитана summary-only;
- есть open questions.

`Низкая`, если:

- manifest отсутствует;
- изменения преимущественно binary-only;
- много файлов не удалось прочитать;
- diff содержит удаление master-spec root;
- есть серьезные противоречия.

## 9. Open questions

Добавляй вопрос в раздел 17, если:

- мотивация не выводится надежно из документов;
- binary artifact важен, но нет текстовой сводки;
- manifest stale;
- deleted file был связан через `depends_on`/`related_files`;
- API/data/security последствия неоднозначны;
- analyst ref изменился после генерации artifacts.

## 10. Downstream compatibility

`openspec-design` и `openspec-implement` должны видеть:

- `Spec update mode: branch-diff`;
- `Base ref`;
- `Analyst ref`;
- `Diff mode`;
- `Merge base`;
- `.spec-diff/changed-files.yaml`, если artifacts записаны.

Финальный `apply-change` для branch-diff не мержит документы по умолчанию. Он выполняет verify source-of-truth docs.
