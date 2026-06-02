---
name: openspec-new-spec
description: Deprecated. Старый skill создания одной полной Markdown-спецификации больше не используется в текущем SDD framework. Не запускай для новых сервисов и не создавай single-file specification. Вместо этого предложи пользователю разместить документы в `openspec/<service-name>/` и запустить `openspec-init-master-spec`.
license: MIT
compatibility: Deprecated compatibility stub.
metadata:
  author: openspec-distillate
  version: "5.0-deprecated"
---

# Deprecated: `openspec-new-spec`

Этот skill оставлен только как стоп-сигнал для старого имени. Активный framework больше не создает один большой Markdown-файл как master specification.

## Что делать вместо этого

1. Попроси пользователя разместить существующую или новую документацию сервиса в `openspec/<service-name>/`.
2. Запусти `openspec-init-master-spec`, чтобы создать `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md` и при необходимости `_sdd/stale-files.md`.
3. Для изменения требований используй `openspec-propose`.

## Guardrails

- Не создавай single-file master specification.
- Не используй старые шаблоны полного документа.
- Не направляй пользователя в старый layout.
- Если пользователь просит "задокументировать сервис", объясни folder-based путь и предложи `openspec-init-master-spec`.
