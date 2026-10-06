# Sequence — тестовый чат и локальное ACL-зеркало

UI уже присутствует в текущем дереве, хотя его требования ведутся в этапе 2. Бизнес-проверки остаются на сервере.

```mermaid
sequenceDiagram
    autonumber
    actor U as Пользователь
    participant B as Браузер / TestSession
    participant H as LocalHelperController
    participant L as LocalAclController
    participant A as Общий AclInputAdapter
    participant P as POC use case

    U->>B: Открыть /test-ui/
    B->>H: GET /local-api/v1/fixtures
    H-->>B: Синтетические metadata, agentCode, suggestions
    B->>B: Новый sessionId, показать начальные действия
    U->>B: Нажать suggestion status
    B->>B: Новый Request-Id, busy=true, убрать кнопки
    B->>L: Один POST request/status с metadata
    L->>A: handle напрямую
    A->>P: Нормализованный ход
    P-->>A: Предложение после commit
    A-->>L: ACL propose, state, final_message=false
    L-->>B: HTTP-ответ
    B->>B: Проверить conversation_id/in_reply_to, сохранить state
    B-->>U: Показать предложение и две кнопки
    U->>B: Подтвердить
    B->>L: accept_propose с новым Request-Id и эхо state
    L->>A: Тот же адаптер
    A->>P: Подтверждение
    alt Получен успешный финальный ответ
        P-->>A: Результат после commit
        A-->>L: 200 inform, final_message=true
        L-->>B: ACL-ответ
        B-->>U: Завершение, без активных действий
    else Failure либо потерян ответ
        Note over B,P: Сервер мог выполнить операцию
        B-->>U: Сообщение об ошибке / неизвестном исходе
        Note over B,L: Нет GET сессии и автоматического повторного POST
    end
    opt Новый диалог или перезагрузка страницы
        B->>B: Новый локальный sessionId, без восстановления старого чата
        Note over B,P: Это не отменяет прежнюю обработку или внешний эффект
    end
```

Клиент игнорирует поздний ответ, если уже начат другой локальный диалог. Только актуальные кнопки могут отправить POST; свободный текст не интерпретируется как команда. Реализация — [session.js](../../../agent-ui/src/main/resources/test-ui/session.js).
