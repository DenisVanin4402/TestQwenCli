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

  /** Принимает только явные действия; старый саджест с кодом остаётся request. */
  suggestions(items = []) {
    if (!Array.isArray(items)) return [];
    return items.filter(item => item && typeof item.text === 'string' && item.text.trim())
      .map(item => ({ ...item, performative: item.performative ?? 'request' }))
      .filter(item => item.performative === 'request'
        ? typeof item.action_code === 'string' && item.action_code.trim()
        : ['accept_propose', 'reject_propose'].includes(item.performative) && item.action_code == null);
  }

  actions() {
    return this.state.busy || this.state.final ? [] : this.state.actions;
  }

  /** Вопрос доступен и на подтверждении; текст не заменяет специальную кнопку согласия. */
  canSendText() {
    return !!this.state && !this.state.busy && !this.state.final;
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
      state.final = state.final || body.metadata?.final_message === true;
      const entry = { role: 'agent', text, error: !response.ok || message.performative === 'failure' };
      const view = message.content?.confirmation_view;
      const nonempty = value => typeof value === 'string' && value.trim();
      if (response.ok && message.performative === 'propose' && view
        && nonempty(view.title) && nonempty(view.question) && Array.isArray(view.fields)
        && view.fields.length && view.fields.every(field => field && nonempty(field.label) && nonempty(field.value))) {
        entry.confirmationView = view;
      }
      state.messages.push(entry);
      state.actions = []; state.aclState = []; state.awaitingConfirmation = false;
      if (!state.final) {
        const proposal = response.ok && message.performative === 'propose'
          && Array.isArray(body.state) && body.state.some(item => item?.type === 'CONFIRMATION');
        if (proposal) {
          state.aclState = body.state;
          state.awaitingConfirmation = true;
        }
        state.actions = this.suggestions(body.suggestions)
          .filter(item => item.performative === 'request' || proposal);
      }
      return response.ok && message.performative !== 'failure';
    } catch (error) {
      state.actions = []; state.aclState = []; state.awaitingConfirmation = false;
      if (state === this.state) state.notice = error instanceof TypeError
        ? 'Результат обработки не получен из-за сети. Запрос мог быть выполнен. Автоматической повторной отправки нет.'
        : error.message;
    } finally {
      if (state === this.state) { state.busy = false; this.changed(); }
    }
  }
}
