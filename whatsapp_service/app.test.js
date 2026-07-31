import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { request } from 'node:http';
import { afterEach, test } from 'node:test';
import { createWhatsAppService } from './app.js';

const servers = [];
afterEach(() => {
  while (servers.length) servers.pop().close();
});

class FakeClient extends EventEmitter {
  constructor({ initializeError = null } = {}) {
    super();
    this.initializeError = initializeError;
    this.initializeCalls = 0;
    this.logoutCalls = 0;
  }
  async initialize() {
    this.initializeCalls += 1;
    if (this.initializeError) throw this.initializeError;
  }
  async logout() {
    this.logoutCalls += 1;
    this.emit('disconnected');
  }
  async sendMessage() {}
}

function serviceFor(client = new FakeClient()) {
  return {
    client,
    service: createWhatsAppService({
      clientFactory: () => client,
      createPdfMedia: () => ({}),
      sessionDir: '/nonexistent-test-session',
      executablePath: '/nonexistent-test-chromium',
      logger: { info() {}, warn() {}, error() {} },
    }),
  };
}

async function call(app, method, path, body) {
  const server = app.listen(0, '127.0.0.1');
  servers.push(server);
  await new Promise((resolve) => server.once('listening', resolve));
  const port = server.address().port;
  return new Promise((resolve, reject) => {
    const req = request(
      {
        host: '127.0.0.1',
        port,
        method,
        path,
        headers: body ? { 'content-type': 'application/json' } : {},
      },
      (res) => {
        let data = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(data) }));
      },
    );
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

test('liveness is 200 before authentication with exact sanitized payload', async () => {
  const { service } = serviceFor();
  assert.deepEqual(await call(service.app, 'GET', '/health/live'), {
    status: 200,
    body: { status: 'UP' },
  });
});

test('status distinguishes disconnected and connected state', async () => {
  const { client, service } = serviceFor();
  assert.deepEqual((await call(service.app, 'GET', '/status')).body, {
    connected: false,
    hasQr: false,
  });
  service.initialize();
  await new Promise(setImmediate);
  client.emit('ready');
  assert.deepEqual((await call(service.app, 'GET', '/status')).body, {
    connected: true,
    hasQr: false,
  });
});

test('failed initialization does not affect liveness', async () => {
  const { service } = serviceFor(new FakeClient({ initializeError: new Error('private') }));
  service.initialize();
  await new Promise(setImmediate);
  assert.equal((await call(service.app, 'GET', '/health/live')).status, 200);
});

test('concurrent initialization is blocked', async () => {
  const { client, service } = serviceFor();
  assert.equal(service.initialize(), true);
  assert.equal(service.initialize(), false);
  await new Promise(setImmediate);
  assert.equal(client.initializeCalls, 1);
});

test('connected endpoint remains unavailable while disconnected', async () => {
  const { service } = serviceFor();
  assert.equal((await call(service.app, 'GET', '/me')).status, 503);
  assert.equal(
    (await call(service.app, 'POST', '/send-pdf', { phone: '11999999999' })).status,
    503,
  );
});

test('start and disconnect preserve lifecycle with a fake client', async () => {
  const { client, service } = serviceFor();
  assert.equal((await call(service.app, 'POST', '/start')).status, 200);
  await new Promise(setImmediate);
  assert.equal(client.initializeCalls, 1);
  client.emit('ready');
  assert.equal((await call(service.app, 'POST', '/disconnect')).status, 200);
  assert.equal(client.logoutCalls, 1);
  assert.equal(service.state.connected, false);
});

test('suite uses only fake client and nonexistent Chromium/session paths', () => {
  const { service } = serviceFor();
  assert.equal(service.state.client, null);
});
