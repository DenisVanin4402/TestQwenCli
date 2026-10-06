import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TestSession } from '../../main/resources/test-ui/session.js';
const suggestion = { text: 'Запросить статус', action_code: 'status', display_mode: 'BUTTON' };
const catalog = { agentCode: 'invest-pro', integrationMode: 'stub', suggestions: [suggestion], fixtures: [{ metadata: {} }] };
const reply = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
const acl = (req, performative = 'propose', final = false) => ({ message: { in_reply_to: req.message.reply_with, conversation_id: req.message.conversation_id, performative, content: { result: 'Сводка' } }, metadata: { final_message: final }, state: [{ key: 'paymentNumber', value: '42', type: 'CONFIRMATION' }, { key: 'preparationNo', value: '1', type: 'CONFIRMATION' }], suggestions: [] });
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
  session.fetcher = async () => reply({ message: { in_reply_to: 'wrong' } });
  session.start(catalog, 'B'); await session.send(session.actions()[0]); assert.match(session.state.notice, /не связан/);
});
test('Текст отправляется через ACL, уточнение разрешает повтор, propose запрещает текст', async () => {
  const sent = [];
  const session = new TestSession(async (url, options) => {
    const req = JSON.parse(options.body); sent.push(req);
    const body = acl(req, sent.length === 1 || req.message.performative === 'reject_propose' ? 'inform' : 'propose');
    if (body.message.performative === 'inform') { body.state = []; body.suggestions = []; }
    return reply(body);
  });
  session.start(catalog, 'A');
  await session.sendText('Подтверждаю');
  assert.deepEqual(sent[0].message.content, { user_input: 'Подтверждаю' });
  assert.equal(session.canSendText(), true);
  await session.sendText('Узнай статус');
  assert.equal(session.canSendText(), false);
  await session.sendText('да'); assert.equal(sent.length, 2);
  await session.send(session.actions()[1]);
  assert.equal(session.canSendText(), true);
});
test('Текст блокируется при запросе и поздний ответ старого диалога отсекается', async () => {
  let resolve; let calls = 0;
  const session = new TestSession((url, options) => { calls++; return new Promise(r => {
    resolve = () => r(reply(acl(JSON.parse(options.body))));
  }); });
  session.start(catalog, 'A'); const pending = session.sendText('статус');
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
