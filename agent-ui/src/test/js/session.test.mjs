import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TestSession } from '../../main/resources/test-ui/session.js';

const view = { title: 'Запрос статуса платежа', fields: [
  { label: 'Номер платежа', value: '00042' }, { label: 'Организация', value: '<b>Организация</b>' },
  { label: 'Дата платежа', value: '03.10.2026' }, { label: 'Получатель', value: 'Получатель' }
], question: 'Отправить запрос статуса этого платежа?' };

test('Карточка принадлежит успешному propose; следующий ответ не наследует её или согласие', async () => {
  const sent = [];
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push(req);
    const body = acl(req, sent.length === 1 ? 'propose' : 'inform');
    body.message.content.confirmation_view = view;
    if (sent.length > 1) { body.state = []; body.suggestions = navigation; }
    return reply(body);
  });
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  assert.deepEqual(session.state.messages.at(-1).confirmationView, view);
  await session.sendText('Комиссия?');
  assert.equal(session.state.messages.at(-1).confirmationView, undefined);
  assert.deepEqual(session.state.messages.at(-3).confirmationView, view);
  assert.deepEqual(session.state.aclState, []);
  await session.send(session.actions()[0]);
  assert.equal(sent[2].state, undefined);
});

test('Отсутствующая или дефектная карточка сохраняет полный result и исходное согласие', async () => {
  for (const invalid of [undefined, null, {}, { ...view, title: ' ' }, { ...view, question: '' },
    { ...view, fields: [] }, { ...view, fields: [{ label: 'Номер', value: 42 }] },
    { ...view, fields: [null] }, { ...view, fields: [{ label: '', value: '42' }] }]) {
    const session = new TestSession(async (url, options) => {
      const body = acl(JSON.parse(options.body)); body.message.content.confirmation_view = invalid;
      return reply(body);
    });
    session.start(catalog, 'A'); await session.send(session.actions()[0]);
    assert.equal(session.state.messages.at(-1).confirmationView, undefined);
    assert.equal(session.state.messages.at(-1).text, 'Сводка');
    assert.equal(session.state.aclState.length, 2);
    assert.equal(session.actions()[0].performative, 'accept_propose');
  }
});
const suggestion = { text: 'Запросить статус', action_code: 'status', display_mode: 'BUTTON' };
const confirmation = [{ text: 'Подтвердить', performative: 'accept_propose' }, { text: 'Отклонить', performative: 'reject_propose' }];
const navigation = [{ text: 'Да', action_code: 'resume_operation', performative: 'request' }, { text: 'Нет', action_code: 'reset_operation', performative: 'request' }];
const catalog = { agentCode: 'invest-pro', integrationMode: 'stub', suggestions: [suggestion], fixtures: [{ metadata: {} }] };
const reply = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
const acl = (req, performative = 'propose', final = false) => ({ message: { in_reply_to: req.message.reply_with, conversation_id: req.message.conversation_id, performative, content: { result: 'Сводка' } }, metadata: { final_message: final }, state: [{ key: 'paymentNumber', value: '42', type: 'CONFIRMATION' }, { key: 'preparationNo', value: '1', type: 'CONFIRMATION' }], suggestions: performative === 'propose' ? confirmation : [] });
// Проверяем доставку клиента без дублирования серверной FSM.
test('Саджест и подтверждение передаются только через ACL', async () => {
  const sent = [];
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push({ url, options, req });
    return reply(acl(req, sent.length === 1 ? 'propose' : 'inform', sent.length === 2));
  });
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  assert.equal(sent[0].req.message.content.action_code, 'status');
  assert.deepEqual(session.actions().map(a => a.performative), ['accept_propose', 'reject_propose']);
  const old = session.actions()[0]; await session.send(old);
  assert.equal(sent[1].req.message.performative, 'accept_propose');
  assert.deepEqual(sent[1].req.state, acl(sent[0].req).state);
  assert.deepEqual(session.actions(), []); await session.send(old);
  assert.equal(sent.length, 2);
  assert.ok(sent.every(x => x.options.method === 'POST' && x.url.startsWith('/local-api/api/')));
});
test('inform не создаёт кнопок согласия', async () => {
  const session = new TestSession(async (url, options) => reply({ ...acl(JSON.parse(options.body), 'inform'), suggestions: [suggestion] }));
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  assert.deepEqual(session.actions().map(a => a.action_code), ['status']);
});
test('Двойной клик и поздний ответ старого диалога', async () => {
  let resolve; let count = 0;
  const session = new TestSession((url, options) => { count++; return new Promise(r => { resolve = () => r(reply(acl(JSON.parse(options.body)))); }); });
  session.start(catalog, 'A'); const action = session.actions()[0]; const pending = session.send(action);
  await session.send(action); assert.equal(count, 1); session.start(catalog, 'B'); resolve(); await pending;
  assert.equal(session.state.id, 'B'); assert.equal(session.state.busy, false);
  assert.equal(session.state.messages.length, 1); await session.send(action); assert.equal(count, 1);
});
test('Сетевая ошибка без повторов и GET', async () => {
  let calls = 0;
  const session = new TestSession(async () => { calls++; throw new TypeError('network'); });
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  assert.equal(calls, 1); assert.deepEqual(session.actions(), []); assert.match(session.state.notice, /не получен/);
});
test('Тело ошибки и корреляция ответа', async () => {
  const session = new TestSession(async (url, options) => {
    const body = acl(JSON.parse(options.body), 'failure'); body.message.content = { reason: 'Исход неизвестен' }; return reply(body, 500);
  });
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  assert.equal(session.state.messages.at(-1).text, 'Исход неизвестен'); assert.deepEqual(session.actions(), []);
  assert.equal(session.state.messages.at(-1).error, true); assert.equal(session.state.notice, '');
  session.fetcher = async () => reply({ message: { in_reply_to: 'wrong' } });
  session.start(catalog, 'B'); await session.send(session.actions()[0]); assert.match(session.state.notice, /не связан/);
});
test('Текст отправляется через ACL и доступен на confirmation', async () => {
  const sent = [];
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push(req);
    const body = acl(req, sent.length === 1 || req.message.performative === 'reject_propose' ? 'inform' : 'propose');
    if (body.message.performative === 'inform') { body.state = []; body.suggestions = []; }
    return reply(body);
  });
  session.start(catalog, 'A');
  assert.equal(await session.sendText('Подтверждаю'), true);
  assert.deepEqual(sent[0].message.content, { user_input: 'Подтверждаю' });
  assert.equal(session.canSendText(), true);
  await session.sendText('Узнай статус');
  assert.equal(session.canSendText(), true);
  await session.send(session.actions()[1]);
  assert.equal(session.canSendText(), true);
});

test('Справка и её техническая ошибка удаляют прежние кнопки и параметры', async () => {
  const sent = [];
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push(req);
    const body = acl(req, sent.length === 1 ? 'propose' : 'inform');
    if (sent.length > 1) {
      body.state = []; body.metadata.additional_info = [{ key: 'response_mode', value: 'information' }];
      if (sent.length === 3) { body.message.performative = 'failure'; body.message.content = { reason: 'Справка недоступна' }; }
    }
    return reply(body, sent.length === 3 ? 500 : 200);
  });
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  const actions = session.actions(); const preparation = session.state.aclState;
  assert.equal(await session.sendText('Комиссия?'), true);
  assert.equal(await session.sendText('Сроки?'), false);
  assert.deepEqual(session.state.messages.at(-1), { role: 'agent', text: 'Справка недоступна', error: true });
  assert.equal(session.state.notice, '');
  assert.equal(sent.length, 3); assert.deepEqual(session.actions(), []);
  assert.deepEqual(session.state.aclState, []); assert.equal(session.state.awaitingConfirmation, false);
  assert.notEqual(sent[1].message.reply_with, sent[2].message.reply_with);
  await session.send(actions[0]); assert.equal(sent.length, 3);
});

test('Пустые или отсутствующие suggestions всегда очищают действия, даже на propose', async () => {
  for (const performative of ['propose', 'inform', 'failure']) for (const missing of [true, false]) {
    const session = new TestSession(async (url, options) => {
      const body = acl(JSON.parse(options.body), performative);
      body.metadata.additional_info = [{ key: 'response_mode', value: 'information' }];
      if (missing) delete body.suggestions; else body.suggestions = [];
      return reply(body, performative === 'failure' ? 400 : 200);
    });
    session.start(catalog, 'A'); await session.send(session.actions()[0]);
    assert.deepEqual(session.actions(), []);
  }
});

test('400 показывает Да/Нет; продолжение получает новое согласие и не отправляет старое', async () => {
  const sent = [];
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push(req);
    const body = acl(req);
    if (sent.length === 2) { body.message.performative = 'failure'; body.suggestions = navigation; body.state = []; return reply(body, 400); }
    if (sent.length === 3) body.state[1].value = '7';
    return reply(body);
  });
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  const obsolete = session.actions()[0];
  await session.sendText('Непонятно');
  assert.deepEqual(session.actions().map(a => a.text), ['Да', 'Нет']);
  assert.deepEqual(session.state.aclState, []);
  await session.send(obsolete); assert.equal(sent.length, 2);
  await session.send(session.actions()[0]);
  assert.deepEqual(sent[2].message.content, { action_code: 'resume_operation' });
  assert.equal(sent[2].state, undefined);
  await session.send(session.actions()[0]);
  assert.equal(sent[3].message.performative, 'accept_propose');
  assert.deepEqual(sent[3].message.content, {});
  assert.equal(sent[3].state[1].value, '7');
});

test('Некорректные саджесты не создают действий; согласие требует текущий propose и state', async () => {
  const session = new TestSession(async (url, options) => reply({ ...acl(JSON.parse(options.body), 'failure'), suggestions: confirmation }, 400));
  assert.deepEqual(session.suggestions([null, {}, { text: 'x', performative: 'other' }, { text: 'x', performative: 'request' }, { text: 'x', action_code: 'status', performative: 'accept_propose' }]), []);
  session.start(catalog, 'A'); await session.send(session.actions()[0]);
  assert.deepEqual(session.actions(), []);
});

test('Поздняя справка не возвращает снятые кнопки и не сбрасывает final', async () => {
  let resolve;
  const session = new TestSession((url, options) => new Promise(r => {
    const body = acl(JSON.parse(options.body), 'inform');
    body.metadata.additional_info = [{ key: 'response_mode', value: 'information' }];
    resolve = () => r(reply(body));
  }));
  session.start(catalog, 'A'); const pending = session.sendText('Комиссия?');
  session.state.actions = []; session.state.aclState = []; session.state.awaitingConfirmation = false;
  session.state.final = true; resolve(); await pending;
  assert.equal(session.state.final, true); assert.deepEqual(session.actions(), []);
  assert.deepEqual(session.state.aclState, []); assert.equal(session.canSendText(), false);
});

test('Потеря или неверная корреляция справки снимает согласие без повторов', async () => {
  for (const broken of ['network', 'json', 'correlation']) {
    let calls = 0;
    const session = new TestSession(async (url, options) => {
      const body = acl(JSON.parse(options.body));
      if (++calls === 1) return reply(body);
      if (broken === 'network') throw new TypeError('network');
      if (broken === 'json') return { status: 200, json: async () => { throw new Error('json'); } };
      body.message.in_reply_to = 'wrong'; return reply(body);
    });
    session.start(catalog, 'A'); await session.send(session.actions()[0]);
    await session.sendText('Сроки?'); assert.equal(calls, 2);
    assert.deepEqual(session.actions(), []); assert.deepEqual(session.state.aclState, []);
    assert.equal(session.state.awaitingConfirmation, false);
  }
});
test('Текст блокируется при запросе и поздний ответ старого диалога отсекается', async () => {
  let resolve; let calls = 0;
  const session = new TestSession((url, options) => { calls++; return new Promise(r => {
    resolve = () => r(reply(acl(JSON.parse(options.body))));
  }); });
  session.start(catalog, 'A'); const pending = session.sendText('статус');
  assert.equal(session.canSendText(), false);
  await session.sendText('статус'); assert.equal(calls, 1);
  session.start(catalog, 'B'); resolve(); await pending;
  assert.equal(session.state.id, 'B'); assert.equal(session.state.messages.length, 1);
});

test('После отказа подтверждение возвращает только параметры нового предложения', async () => {
  const sent = [];
  let preparation = 0;
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push(req);
    const choosing = req.message.performative === 'request';
    const body = acl(req, choosing ? 'propose' : 'inform');
    if (choosing) body.state[1].value = String(++preparation);
    else { body.state = []; body.suggestions = [suggestion]; }
    return reply(body);
  });
  session.start(catalog, 'A');
  await session.send(session.actions()[0]);
  const obsolete = session.actions()[0];
  await session.send(session.actions()[1]);
  await session.send(session.actions()[0]);
  await session.send(obsolete);
  assert.equal(sent.length, 3);
  await session.send(session.actions()[0]);
  assert.deepEqual(sent[3].state.map(p => [p.key, p.value]), [['paymentNumber', '42'], ['preparationNo', '2']]);
});
