import express from 'express';
import QR from 'qrcode';

export function createWhatsAppService({
  clientFactory,
  createPdfMedia,
  sessionDir,
  executablePath,
  baseCountryCode = '55',
  logger = console,
}) {
  const app = express();
  app.use(express.json({ limit: '20mb' }));

  const state = {
    connected: false,
    qr: null,
    initializing: false,
    client: null,
  };

  function attachEvents(client) {
    client.on('qr', (qr) => {
      state.qr = qr;
      state.connected = false;
      logger.info('WhatsApp QR is available');
    });
    client.on('ready', () => {
      state.connected = true;
      state.qr = null;
      logger.info('WhatsApp client is ready');
    });
    client.on('authenticated', () => {
      state.connected = true;
      state.qr = null;
      logger.info('WhatsApp client authenticated');
    });
    client.on('auth_failure', () => {
      state.connected = false;
      logger.error('WhatsApp authentication failed');
    });
    client.on('disconnected', () => {
      state.connected = false;
      state.qr = null;
      logger.warn('WhatsApp client disconnected');
    });
  }

  function getClient() {
    if (!state.client) {
      state.client = clientFactory({ sessionDir, executablePath });
      attachEvents(state.client);
    }
    return state.client;
  }

  function initialize() {
    if (state.initializing || state.connected) return false;
    state.initializing = true;
    state.qr = null;
    Promise.resolve()
      .then(() => getClient().initialize())
      .catch(() => {
        state.connected = false;
        state.qr = null;
        logger.error('WhatsApp initialization failed');
      })
      .finally(() => {
        state.initializing = false;
      });
    return true;
  }

  function normalizePhone(phone) {
    if (!phone) return null;
    const digits = String(phone).replace(/\D/g, '');
    if (digits.length < 10) return null;
    return digits.startsWith(baseCountryCode) ? digits : baseCountryCode + digits;
  }

  app.get('/health/live', (_req, res) => {
    res.status(200).json({ status: 'UP' });
  });

  app.get('/status', (_req, res) => {
    res.json({ connected: state.connected, hasQr: Boolean(state.qr) });
  });

  app.post('/start', (_req, res) => {
    if (!state.connected && !state.qr) initialize();
    res.json({ success: true, connected: state.connected });
  });

  app.get('/qr', async (_req, res) => {
    if (!state.qr) {
      return res.status(404).json({ success: false, message: 'No QR available' });
    }
    const png = await QR.toDataURL(state.qr, { margin: 2, scale: 6 });
    return res.json({ success: true, png });
  });

  app.post('/disconnect', async (_req, res) => {
    try {
      if (state.client) await state.client.logout();
      state.connected = false;
      state.qr = null;
      return res.json({ success: true });
    } catch {
      logger.error('WhatsApp disconnect failed');
      return res.status(500).json({ success: false, message: 'Disconnect failed' });
    }
  });

  app.get('/me', (_req, res) => {
    if (!state.connected) {
      return res.status(503).json({ success: false, message: 'Not connected' });
    }
    const info = state.client?.info || {};
    return res.json({
      success: true,
      wid: info.wid?._serialized || null,
      pushname: info.pushname || null,
    });
  });

  app.post('/send-pdf', async (req, res) => {
    if (!state.connected) {
      return res.status(503).json({ success: false, message: 'WhatsApp not connected' });
    }
    const { phone, filename, caption, pdfBase64 } = req.body || {};
    const normalized = normalizePhone(phone);
    if (!normalized) {
      return res.status(400).json({ success: false, message: 'Invalid phone' });
    }
    if (!pdfBase64) {
      return res.status(400).json({ success: false, message: 'Missing pdfBase64' });
    }
    try {
      const media = createPdfMedia(pdfBase64, filename || 'comprovante.pdf');
      await state.client.sendMessage(`${normalized}@c.us`, media, {
        caption: caption || 'Comprovante',
        sendSeen: false,
      });
      return res.json({ success: true });
    } catch {
      logger.error('WhatsApp PDF delivery failed');
      return res.status(500).json({ success: false, message: 'Delivery failed' });
    }
  });

  return { app, initialize, state };
}
