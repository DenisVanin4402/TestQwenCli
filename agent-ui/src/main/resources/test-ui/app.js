import { TestSession } from './session.js';
const byId = id => document.getElementById(id);
let catalog;

/** Ответы и реквизиты выводятся как текст; HTML и шаблонные выражения не исполняются. */
function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text != null) node.textContent = String(text).replace(/\{\{[^}]*\}\}/g, '');
  if (className) node.className = className;
  return node;
}

function render() {
  const state = session.state;
  byId('mode').textContent = catalog.integrationMode === 'stub' ? 'Тестовый режим · stub' : 'Реальное подключение · real';
  byId('status').textContent = state.final ? 'Диалог завершён' : state.busy ? 'Готовит ответ…' : 'На связи';
  byId('notice').textContent = state.notice;
  byId('notice').hidden = !state.notice;
  byId('progress').textContent = state.busy ? 'Помощник обрабатывает запрос…' : '';
  byId('new').disabled = false;
  byId('message').disabled = !session.canSendText();
  byId('send').disabled = !session.canSendText() || !byId('message').value.trim();
  const conversation = byId('conversation');
  const nearBottom = conversation.scrollHeight - conversation.scrollTop - conversation.clientHeight < 100;
  const messages = state.messages.map((message, index) => {
    const row = element('article', null, 'message ' + message.role + (message.error ? ' error' : ''));
    row.append(element('p', message.role === 'user' ? 'Вы' : 'Помощник', 'author'), element('p', message.text, 'message-text'));
    if (index === state.messages.length - 1 && message.role === 'agent') {
      const actions = element('div', null, 'actions');
      for (const action of session.actions()) {
        const button = element('button', action.text, action.performative === 'reject_propose' ? 'reject' : '');
        button.type = 'button';
        button.addEventListener('click', () => session.send(action));
        actions.append(button);
      }
      row.append(actions);
    }
    return row;
  });
  conversation.replaceChildren(...messages);
  if (nearBottom) conversation.scrollTop = conversation.scrollHeight;
  byId('diagnostics').textContent = 'Сессия: ' + state.id + '\nЗапрос: ' + state.requestId + '\nHTTP: ' + state.http;
}

const session = new TestSession(globalThis.fetch.bind(globalThis), render);
byId('new').addEventListener('click', () => { byId('message').value = ''; session.start(catalog); });
byId('message').addEventListener('input', () => { byId('send').disabled = !session.canSendText() || !byId('message').value.trim(); });
byId('composer').addEventListener('submit', event => { event.preventDefault(); session.sendText(byId('message').value); });
byId('message').addEventListener('keydown', event => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    session.sendText(byId('message').value);
  }
});

try {
  const response = await fetch('/local-api/v1/fixtures');
  if (!response.ok) throw new Error('Не удалось загрузить тестовый платёж (HTTP ' + response.status + ').');
  catalog = await response.json();
  if (!catalog.fixtures?.length || !catalog.agentCode) throw new Error('Сервер не предоставил тестовый платёж.');
  const payment = catalog.fixtures[0].payment;
  const details = element('dl');
  for (const [key, label] of Object.entries({ organizationName: 'Организация', paymentNumber: 'Номер', paymentDate: 'Дата', recipientName: 'Получатель' })) {
    if (payment[key] != null) details.append(element('dt', label), element('dd', payment[key]));
  }
  const amount = payment.amount == null ? '' : new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(payment.amount);
  byId('payment').replaceChildren(element('p', (amount + ' ' + (payment.currency ?? '')).trim(), 'amount'), details);
  session.start(catalog);
} catch (error) {
  byId('mode').textContent = 'Нет подключения';
  byId('notice').textContent = error.message;
  byId('notice').hidden = false;
}
