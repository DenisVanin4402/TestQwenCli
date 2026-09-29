# Граф команд и скриптов

`spec_review.py` читает документы, выполняет проверки и записывает результат. `review_renderer.py` получает два текста и строит представление с подсветкой; самостоятельно файлы не читает и не записывает.

```mermaid
flowchart TD
    CLI["spec_review.py --root …"] --> SOURCE["source"]
    CLI --> RENDER["render"]
    CLI --> CHECK["check"]
    CLI --> PIN["pin-intent"]
    CLI --> ACK["acknowledge"]

    SOURCE --> GIT["Прочитать файл из Git"]
    GIT --> DESC["Вывести commit + путь + checksum"]

    RENDER --> HASH{"--save-change-hash?"}
    HASH -->|Нет| VERIFY["Проверить checksum change.md"]
    HASH -->|Да| SAVE["Рассчитать новую checksum change.md"]
    VERIFY --> BUILD["Подготовить review в памяти"]
    SAVE --> BUILD

    BUILD --> READ["Прочитать review_base из Git и content"]
    READ --> RR["review_renderer.py: сравнить тексты и построить подсветку"]
    RR --> MEMORY["Вернуть представление и рассчитать fingerprint"]

    MEMORY --> WRITE["Для render: записать review/"]
    WRITE --> MANIFEST["С флагом --save-change-hash также сохранить checksum в manifest"]

    CHECK --> CHECKHASH["Проверить checksum change.md"]
    CHECKHASH --> BUILD
    MEMORY --> COMPARE["Для check: сравнить с сохранённым review/"]
    COMPARE --> MODE{"Режим check"}
    MODE --> BASIC["Без флагов: проверка завершена"]
    MODE --> READY["--ready: проверить согласование, состояние применения и неизменность master"]
    MODE --> RESULT["--result: проверить совпадение master с content"]

    PIN --> INTENT["Проверить checksum и соответствие change/content указанному commit"]
    INTENT --> FIX["Записать approved и intent_approval в manifest"]

    ACK --> ACHECK["Выполнить обычный check"]
    ACHECK --> APPROVE["При review_status=ready записать fingerprint и evidence в manifest.review_approval"]
```

`render` и `check` используют один механизм построения представления. `render` сохраняет результат, а `check` сравнивает его с уже сохранённым файлом. Ветви «Для render» и «Для check» альтернативны и выбираются по вызванной команде.

Весь набор документов разбирается до начала записи. При `render --save-change-hash` после успешного построения всего набора сначала сохраняется manifest, затем файлы review; общей атомарной транзакции этих записей нет. Флаг используют после подготовки или актуализации content: он не проверяет смысловое соответствие текста требованиям.

Fingerprint рассчитывает `spec_review.py`. Он связывает требования, manifest без поля review_approval, исходные версии, content, представление и версию генератора. Метаданные возвращаются в консоль; отдельного файла для них нет.

`pin-intent` и `acknowledge` фиксируют уже полученное согласование; сами команды не принимают решение за аналитика.

## Тесты

`test_spec_review.py` запускается отдельно:

```mermaid
flowchart LR
    TEST["test_spec_review.py"] --> FIXTURES["Создать тестовые документы и временные Git-репозитории"]
    FIXTURES --> CALL["Вызвать spec_review.py и review_renderer.py"]
    CALL --> ASSERT["Проверить результаты и ожидаемые ошибки"]
    ASSERT --> REPORT["Отчёт unittest"]
```

Запись в master и ведение `application-state.json` выполняет ИИ по скиллу — отдельной Python-команды `apply` нет.

Подробности: [протокол и команды](document-copies.md), [применение и восстановление](ai-apply.md).
