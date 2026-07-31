import whatsapp from 'whatsapp-web.js';
import { createWhatsAppService } from './app.js';

const { Client, LocalAuth, MessageMedia } = whatsapp;

function clientFactory({ sessionDir, executablePath }) {
  return new Client({
    authStrategy: new LocalAuth({ dataPath: sessionDir }),
    puppeteer: {
      executablePath,
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--no-first-run',
        '--no-zygote',
        '--disable-gpu',
      ],
    },
    takeoverOnConflict: true,
  });
}

const service = createWhatsAppService({
  clientFactory,
  createPdfMedia: (data, filename) =>
    new MessageMedia('application/pdf', data, filename),
  sessionDir: process.env.SESSION_DIR || '/data/session',
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || '/usr/bin/chromium-browser',
  baseCountryCode: process.env.BASE_COUNTRY_CODE || '55',
});

const port = Number(process.env.PORT || 3001);
const server = service.app.listen(port, '0.0.0.0', () => {
  console.log(`WhatsApp HTTP service listening on :${port}`);
});

if (process.env.WHATSAPP_INITIALIZATION_DISABLED === 'true') {
  console.log('WhatsApp initialization disabled for local health validation');
} else {
  service.initialize();
}

function shutdown() {
  server.close(() => process.exit(0));
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
