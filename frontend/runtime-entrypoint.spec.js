import { describe as nodeDescribe, it as nodeIt } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const script = join(process.cwd(), 'entrypoint.sh')

function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'frontend-runtime-'))
  const index = join(dir, 'index.html')
  const config = join(dir, 'runtime-config.js')
  writeFileSync(index, '<html><head></head><body></body></html>')
  return { dir, index, config }
}

function run(env, ...command) {
  const f = fixture()
  const result = spawnSync('/bin/sh', [script, ...command], {
    encoding: 'utf8',
    env: { ...process.env, INDEX_FILE: f.index, RUNTIME_CONFIG_FILE: f.config, ...env },
  })
  return { ...f, result }
}

nodeDescribe('frontend runtime entrypoint', () => {
  nodeIt('fails closed when VITE_BASE_API_URL is absent', () => {
    const { result } = run({ VITE_BASE_API_URL: '' }, '/bin/true')
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /VITE_BASE_API_URL/)
  })

  nodeIt('serializes a valid public URL as JavaScript-safe JSON', () => {
    const value = 'https://erp.invalid/a?x=%22quoted%22'
    const { result, config } = run({ VITE_BASE_API_URL: value }, '/bin/true')
    assert.equal(result.status, 0)
    assert.match(readFileSync(config, 'utf8'), new RegExp(JSON.stringify(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
    assert.match(readFileSync(config, 'utf8'), /"releaseControlMode":"disabled"/)
  })

  nodeIt('serializes the production deployer mode without exposing a private origin', () => {
    const { result, config } = run({
      VITE_BASE_API_URL: 'https://erp.invalid',
      RELEASE_CONTROL_MODE: 'deployer',
    }, '/bin/true')
    assert.equal(result.status, 0)
    const runtimeConfig = readFileSync(config, 'utf8')
    assert.match(runtimeConfig, /"releaseControlMode":"deployer"/)
    assert.doesNotMatch(runtimeConfig, /8121/)
  })

  for (const mode of ['publisher', 'unknown']) nodeIt(`rejects invalid production mode ${mode}`, () => {
    const { result } = run({ VITE_BASE_API_URL: 'https://erp.invalid', RELEASE_CONTROL_MODE: mode }, '/bin/true')
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /RELEASE_CONTROL_MODE/)
  })

  nodeIt('rejects malformed and script-injection values', () => {
    const { result } = run({ VITE_BASE_API_URL: 'javascript:alert(1)' }, '/bin/true')
    assert.notEqual(result.status, 0)
  })

  nodeIt('is idempotent and execs the final command', () => {
    const f = fixture()
    const env = {
      ...process.env,
      INDEX_FILE: f.index,
      RUNTIME_CONFIG_FILE: f.config,
      VITE_BASE_API_URL: 'https://erp.invalid',
    }
    execFileSync('/bin/sh', [script, '/bin/true'], { env })
    const output = execFileSync('/bin/sh', [script, '/bin/echo', 'exec-ok'], { env, encoding: 'utf8' })
    assert.match(output, /exec-ok/)
    assert.equal(readFileSync(f.index, 'utf8').match(/runtime-config\.js/g)?.length, 1)
  })
})

// The terminal contract also runs this file with node --test. The Vitest
// bridge keeps the same causal checks in the package's configured unit run
// without importing Vitest when Node executes the file directly.
if (process.env.VITEST) {
  const vitestDescribe = globalThis.describe
  const vitestExpect = globalThis.expect
  const vitestIt = globalThis.it
  vitestDescribe('frontend runtime entrypoint (Vitest bridge)', () => {
    vitestIt('fails closed when VITE_BASE_API_URL is absent', () => {
      const { result } = run({ VITE_BASE_API_URL: '' }, '/bin/true')
      vitestExpect(result.status).not.toBe(0)
      vitestExpect(result.stderr).toContain('VITE_BASE_API_URL')
    })

    vitestIt('serializes disabled and deployer runtime modes', () => {
      const disabled = run({ VITE_BASE_API_URL: 'https://erp.invalid' }, '/bin/true')
      const deployer = run(
        { VITE_BASE_API_URL: 'https://erp.invalid', RELEASE_CONTROL_MODE: 'deployer' },
        '/bin/true',
      )
      vitestExpect(disabled.result.status).toBe(0)
      vitestExpect(disabled.config && readFileSync(disabled.config, 'utf8')).toContain(
        '"releaseControlMode":"disabled"',
      )
      vitestExpect(deployer.result.status).toBe(0)
      vitestExpect(deployer.config && readFileSync(deployer.config, 'utf8')).toContain(
        '"releaseControlMode":"deployer"',
      )
    })

    for (const mode of ['publisher', 'unknown']) vitestIt(`rejects invalid mode ${mode}`, () => {
      const { result } = run({ VITE_BASE_API_URL: 'https://erp.invalid', RELEASE_CONTROL_MODE: mode }, '/bin/true')
      vitestExpect(result.status).not.toBe(0)
    })

    vitestIt('rejects malformed URL values', () => {
      const { result } = run({ VITE_BASE_API_URL: 'javascript:alert(1)' }, '/bin/true')
      vitestExpect(result.status).not.toBe(0)
    })

    vitestIt('is idempotent and execs the final command', () => {
      const f = fixture()
      const env = {
        ...process.env,
        INDEX_FILE: f.index,
        RUNTIME_CONFIG_FILE: f.config,
        VITE_BASE_API_URL: 'https://erp.invalid',
      }
      execFileSync('/bin/sh', [script, '/bin/true'], { env })
      const output = execFileSync('/bin/sh', [script, '/bin/echo', 'exec-ok'], { env, encoding: 'utf8' })
      vitestExpect(output).toContain('exec-ok')
      vitestExpect(readFileSync(f.index, 'utf8').match(/runtime-config\.js/g)).toHaveLength(1)
    })
  })
}
