/** Доставка сообщений чата по ACL. Бизнес-состояние остаётся на сервере. */
export class TestSession {
  constructor(fetcher = globalThis.fetch.bind(globalThis), changed = () => {}) {
    this.fetcher = fetcher;
    this.changed = changed;
  }

  /** Новый диалог не отменяет предыдущую обработку и не переотправляет старые сообщения. */
  start(catalog, id = crypto.randomUUID()) {
    this.catalog = catalog;
    this.state = { id, busy: false, final: false, notice: '', requestId: '', http: '',
      messages: [{ role: 'agent', text: 'Выберите действие для тестового платежа.' }],
      actions: this.suggestions(catalog.suggestions), aclState: [], awaitingConfirmation: false };
    this.changed();
  }

  /** Саджесты приходят от агента; клиент добавляет только тип входящего ACL-сообщения. */
  suggestions(items = []) {
    return items.filter(item => item.action_code && typeof item.text === 'string')
      .map(item => ({ ...item, performative: 'request' }));
  }

  actions() {
    return this.state.busy || this.state.final ? [] : this.state.actions;
  }

  /** На предложении требуется специальное подтверждение; текст доступен на выборе. */
  canSendText() {
    return !!this.state && !this.state.busy && !this.state.final && !this.state.awaitingConfirmation;
  }

  async sendText(text) {
    if (!this.canSendText() || !text.trim()) return;
    return this.deliver({ performative: 'request', text }, { user_input: text });
  }

  /** Отправляет только актуальную кнопку и читает один ACL-ответ, без GET и повторов POST. */
  async send(action) {
    if (!this.actions().includes(action)) return;
    return this.deliver(action, action.performative === 'request' ? { action_code: action.action_code } : {});
  }

  /** Общая доставка кнопки или текста сохраняет корреляцию и не повторяет запрос. */
  async deliver(action, content) {
    const state = this.state;
    state.busy = true;
    state.notice = '';
    state.actions = [];
    state.requestId = crypto.randomUUID();
    if (action.is_posted_to_chat !== false) {
      state.messages.push({ role: 'user', text: action.message_text || action.text });
    }
    const request = { message: { version: '1.6', sender: 'LOCAL_TEST', receiver: this.catalog.agentCode,
      conversation_id: state.id, reply_with: state.requestId, performative: action.performative,
      content },
      metadata: this.catalog.fixtures[0].metadata };
    if (action.performative !== 'request') request.state = state.aclState;
    this.changed();
    try {
      const response = await this.fetcher('/local-api/api/v1/ai/agents/' + encodeURIComponent(this.catalog.agentCode), {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Request-Id': state.requestId, 'Gigachat-Session-Id': state.id },
        body: JSON.stringify(request)
      });
      let body;
      try { body = await response.json(); } catch { body = null; }
      if (state !== this.state) return;
      state.http = 'POST ' + response.status;
      const message = body?.message;
      if (!message) throw new Error(body?.error || ('Ответ ACL не получен (HTTP ' + response.status + ').'));
      if (message.in_reply_to !== state.requestId || message.conversation_id !== state.id) {
        throw new Error('Ответ не связан с отправленным сообщением. Результат обработки не получен.');
      }
      const text = message.content?.reason || message.content?.result;
      if (typeof text !== 'string' || !text.trim()) throw new Error('Ответ не содержит результата обработки.');
      state.final = body.metadata?.final_message === true;
      state.aclState = body.state ?? [];
      state.messages.push({ role: 'agent', text, error: !response.ok || message.performative === 'failure' });
      if (!response.ok || message.performative === 'failure') {
        state.notice = text;
      } else if (!state.final) {
        state.awaitingConfirmation = message.performative === 'propose';
        if (message.performative === 'propose') {
          state.actions = [
            { text: 'Подтвердить', performative: 'accept_propose', display_mode: 'BUTTON' },
            { text: 'Отклонить', performative: 'reject_propose', display_mode: 'BUTTON' }
          ];
        } else {
          state.actions = this.suggestions(body.suggestions);
        }
      }
    } catch (error) {
      if (state === this.state) state.notice = error instanceof TypeError
        ? 'Результат обработки не получен из-за сети. Запрос мог быть выполнен. Автоматической повторной отправки нет.'
        : error.message;
    } finally {
      if (state === this.state) { state.busy = false; this.changed(); }
    }
  }
}
