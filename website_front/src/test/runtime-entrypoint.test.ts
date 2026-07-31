import { execFileSync, spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const script = join(process.cwd(), 'entrypoint.sh');

function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'website-runtime-'));
  const bin = join(dir, 'bin');
  execFileSync('mkdir', ['-p', bin]);
  const curl = join(bin, 'curl');
  writeFileSync(
    curl,
    `#!/bin/sh
printf '%s\\n' "$*" >> "$MOCK_CURL_LOG"
case "$*" in
  *website_back:8085*) exit 22 ;;
  *) printf '%s' '{"content":{"seoTitle":"Safe & <title>","seoDescription":"Public default"}}' ;;
esac
`,
  );
  chmodSync(curl, 0o755);
  const index = join(dir, 'index.html');
  const config = join(dir, 'runtime-config.js');
  const log = join(dir, 'curl.log');
  writeFileSync(index, '<html><head><title>old</title></head><body></body></html>');
  return { dir, bin, index, config, log };
}

function environment(f: ReturnType<typeof fixture>, extra = {}) {
  return {
    ...process.env,
    PATH: `${f.bin}:${process.env.PATH}`,
    INDEX_FILE: f.index,
    RUNTIME_CONFIG_FILE: f.config,
    MOCK_CURL_LOG: f.log,
    VITE_ERP_API_URL: 'https://erp.invalid',
    VITE_WEBSITE_API_URL: 'https://website.invalid',
    ...extra,
  };
}

describe('website runtime entrypoint', () => {
  it('fails closed and reports only the missing canonical name', () => {
    const f = fixture();
    const env = environment(f);
    delete env.VITE_WEBSITE_API_URL;
    const result = spawnSync('/bin/sh', [script, '/bin/true'], { env, encoding: 'utf8' });
    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain('VITE_WEBSITE_API_URL');
  });

  it('serializes both canonical URLs without legacy aliases', () => {
    const f = fixture();
    execFileSync('/bin/sh', [script, '/bin/true'], { env: environment(f) });
    const config = readFileSync(f.config, 'utf8');
    expect(config).toContain('"erpApiUrl":"https://erp.invalid"');
    expect(config).toContain('"websiteApiUrl":"https://website.invalid"');
    expect(Object.keys(JSON.parse(config.match(/= (.*);/)![1]))).toEqual([
      'erpApiUrl',
      'websiteApiUrl',
    ]);
  });

  it('uses website_back internally and website URL as bounded fallback', () => {
    const f = fixture();
    execFileSync('/bin/sh', [script, '/bin/true'], { env: environment(f) });
    const calls = readFileSync(f.log, 'utf8');
    expect(calls).toContain('http://website_back:8085/');
    expect(calls).toContain('https://website.invalid/');
    expect(calls).not.toContain('https://erp.invalid/');
  });

  it('escapes theme HTML and remains idempotent', () => {
    const f = fixture();
    const env = environment(f);
    execFileSync('/bin/sh', [script, '/bin/true'], { env });
    const output = execFileSync('/bin/sh', [script, '/bin/echo', 'exec-ok'], {
      env,
      encoding: 'utf8',
    });
    const html = readFileSync(f.index, 'utf8');
    expect(output).toContain('exec-ok');
    expect(html.match(/runtime-config\.js/g)).toHaveLength(1);
    expect(html.match(/SEO_START/g)).toHaveLength(1);
    expect(html).toContain('Safe &amp; &lt;title&gt;');
  });

  it('rejects malformed public URLs before any fetch', () => {
    const f = fixture();
    const result = spawnSync('/bin/sh', [script, '/bin/true'], {
      env: environment(f, { VITE_WEBSITE_API_URL: 'javascript:alert(1)' }),
      encoding: 'utf8',
    });
    expect(result.status).not.toBe(0);
  });
});
