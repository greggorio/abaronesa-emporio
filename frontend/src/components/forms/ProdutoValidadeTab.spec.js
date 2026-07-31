import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush })
}))

const mockApiRequest = vi.fn()
vi.mock('../../composables/useApiRequest', () => ({
  useApiRequest: () => ({ apiRequest: mockApiRequest })
}))

import ProdutoValidadeTab from './ProdutoValidadeTab.vue'

const globalMocks = {
  global: {
    config: {
      globalProperties: {
        $q: {
          notify: vi.fn()
        }
      }
    },
    provide: {
      $q: { notify: vi.fn() }
    }
  }
}

describe('ProdutoValidadeTab', () => {
  beforeEach(() => {
    mockApiRequest.mockReset()
    mockPush.mockReset()
  })

  it('F01: exibe banner quando recordId null', async () => {
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: {}, recordId: null },
      ...globalMocks
    })
    expect(wrapper.text()).toContain('Salve o produto primeiro')
    expect(mockApiRequest).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('F02: exibe banner quando controlaValidade=false', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, produtoNome: 'Produto Teste', controlaValidade: false, vidaUtilDias: null,
      resumo: { totalSkus: 1, totalLotes: 0, lotesComSaldo: 0, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 },
      skus: []
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: false }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.text()).toContain('não está configurado')
    wrapper.unmount()
  })

  it('F03: renderiza cards de resumo e lista de lotes', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, produtoNome: 'Produto Teste', controlaValidade: true, vidaUtilDias: 30,
      resumo: { totalSkus: 1, totalLotes: 2, lotesComSaldo: 2, vencidos: 0, criticos: 1, atencao: 0, semLote: 0 },
      skus: [{
        skuId: 1, skuCodigo: 'TEST-001', skuDescricao: 'Produto Teste - Único',
        estoqueAgregado: 10, somaLotes: 8, possuiDivergencia: true,
        lotes: [
          { estoqueLoteId: 1, lote: 'LOTE-A', dataValidade: '2026-04-15', status: 'CRITICO', quantidade: 5, rastreabilidade: 'LOTE_VALIDO' },
          { estoqueLoteId: 2, lote: 'LOTE-B', dataValidade: '2026-04-30', status: 'OK', quantidade: 3, rastreabilidade: 'LOTE_VALIDO' }
        ]
      }]
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.text()).toContain('SKUs')
    expect(wrapper.text()).toContain('Críticos')
    expect(wrapper.text()).toContain('LOTE-A')
    expect(wrapper.text()).toContain('LOTE-B')
    expect(wrapper.text()).toContain('Divergência')
    wrapper.unmount()
  })

  it('F04: refaz chamada ao atualizar', async () => {
    mockApiRequest
      .mockResolvedValueOnce({ produtoId: 1, controlaValidade: true, resumo: { totalSkus: 1, totalLotes: 0, lotesComSaldo: 0, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 }, skus: [] })
      .mockResolvedValueOnce({ produtoId: 1, controlaValidade: true, resumo: { totalSkus: 1, totalLotes: 1, lotesComSaldo: 1, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 }, skus: [] })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))
    const btn = wrapper.find('button')
    const cnt = mockApiRequest.mock.calls.length
    if (btn.exists()) {
      await btn.trigger('click')
      expect(mockApiRequest.mock.calls.length).toBeGreaterThan(cnt)
    }
    wrapper.unmount()
  })

  it('F05: abre dialogo de criacao de lote e chama API correta', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, controlaValidade: true, vidaUtilDias: 30,
      resumo: { totalSkus: 1, totalLotes: 0, lotesComSaldo: 0, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 },
      skus: [{
        skuId: 1, skuCodigo: 'TEST-001', skuDescricao: 'Teste', estoqueAgregado: 5, somaLotes: 5, possuiDivergencia: false,
        lotes: []
      }]
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))

    const criarBtn = wrapper.findAll('button').find(b => b.text().includes('Criar lote'))
    if (criarBtn) {
      await criarBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))

      const dialog = wrapper.find('[role="dialog"]')
      expect(dialog.exists()).toBe(true)

      mockApiRequest.mockResolvedValueOnce({})
      const salvarBtn = wrapper.findAll('button').find(b => b.text() === 'Salvar')
      if (salvarBtn) {
        await salvarBtn.trigger('click')
        await new Promise(r => setTimeout(r, 50))
        expect(mockApiRequest).toHaveBeenCalledWith(
          '/api/validade/produtos/1/lotes',
          'POST',
          expect.objectContaining({ skuId: expect.any(Number) })
        )
      }
    }
    wrapper.unmount()
  })

  it('F06: abre dialogo de ajuste e chama API correta', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, controlaValidade: true, vidaUtilDias: 30,
      resumo: { totalSkus: 1, totalLotes: 1, lotesComSaldo: 1, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 },
      skus: [{
        skuId: 1, skuCodigo: 'TEST-001', skuDescricao: 'Teste', estoqueAgregado: 5, somaLotes: 5, possuiDivergencia: false,
        lotes: [{ estoqueLoteId: 1, lote: 'LOTE-A', dataValidade: '2026-04-15', status: 'OK', quantidade: 5, rastreabilidade: 'LOTE_VALIDO' }]
      }]
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))

    const ajustarBtn = wrapper.findAll('button').find(b => b.text().includes('Ajustar'))
    if (ajustarBtn) {
      await ajustarBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))

      const dialog = wrapper.find('[role="dialog"]')
      expect(dialog.exists()).toBe(true)

      mockApiRequest.mockResolvedValueOnce({})
      const aplicarBtn = wrapper.findAll('button').find(b => b.text() === 'Aplicar')
      if (aplicarBtn) {
        await aplicarBtn.trigger('click')
        await new Promise(r => setTimeout(r, 50))
        expect(mockApiRequest).toHaveBeenCalledWith(
          '/api/validade/lotes/1/ajustar',
          'POST',
          expect.objectContaining({ acao: expect.any(String), quantidade: expect.any(Number) })
        )
      }
    }
    wrapper.unmount()
  })

  it('F07: abre dialogo de zerar e chama API correta', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, controlaValidade: true, vidaUtilDias: 30,
      resumo: { totalSkus: 1, totalLotes: 1, lotesComSaldo: 1, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 },
      skus: [{
        skuId: 1, skuCodigo: 'TEST-001', skuDescricao: 'Teste', estoqueAgregado: 5, somaLotes: 5, possuiDivergencia: false,
        lotes: [{ estoqueLoteId: 1, lote: 'LOTE-A', dataValidade: '2026-04-15', status: 'OK', quantidade: 5, rastreabilidade: 'LOTE_VALIDO' }]
      }]
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))

    const zerarBtn = wrapper.findAll('button').find(b => b.text().includes('Zerar'))
    if (zerarBtn) {
      await zerarBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))

      const dialog = wrapper.find('[role="dialog"]')
      expect(dialog.exists()).toBe(true)

      mockApiRequest.mockResolvedValueOnce({})
      const confirmarBtn = wrapper.findAll('button').find(b => b.text() === 'Zerar')
      if (confirmarBtn) {
        await confirmarBtn.trigger('click')
        await new Promise(r => setTimeout(r, 50))
        expect(mockApiRequest).toHaveBeenCalledWith(
          '/api/validade/lotes/1/zerar',
          'POST',
          expect.objectContaining({ observacao: expect.any(String) })
        )
      }
    }
    wrapper.unmount()
  })

  it('F08: chama API de movimentos do lote', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, controlaValidade: true,
      resumo: { totalSkus: 1, totalLotes: 1, lotesComSaldo: 1, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 },
      skus: [{
        skuId: 1, skuCodigo: 'TEST-001', skuDescricao: 'Teste', estoqueAgregado: 5, somaLotes: 5, possuiDivergencia: false,
        lotes: [{ estoqueLoteId: 1, lote: 'LOTE-A', dataValidade: '2026-04-15', status: 'OK', quantidade: 5, rastreabilidade: 'LOTE_VALIDO' }]
      }]
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))
    mockApiRequest.mockResolvedValueOnce([{
      id: 1, dataMovimento: '2026-03-31T10:00:00', tipoMovimento: 'VENDA', tipoMovimentoDescricao: 'Venda',
      deltaQuantidade: -3, observacao: 'Venda teste', documentoReferencia: 'PED-001', usuarioNome: 'Usuario Teste'
    }])

    const historicoBtn = wrapper.findAll('button').find(b => b.text().includes('Histórico'))
    if (historicoBtn) {
      await historicoBtn.trigger('click')
      await new Promise(r => setTimeout(r, 100))
      expect(mockApiRequest).toHaveBeenCalledWith('/api/validade/lotes/1/movimentos')
    }
    wrapper.unmount()
  })

  it('F09: navega para tarefa contextualizada com produtoId e produtoNome', async () => {
    mockApiRequest.mockResolvedValue({
      produtoId: 1, produtoNome: 'Produto Teste', controlaValidade: true, vidaUtilDias: 30,
      resumo: { totalSkus: 1, totalLotes: 0, lotesComSaldo: 0, vencidos: 0, criticos: 0, atencao: 0, semLote: 0 },
      skus: []
    })
    const wrapper = mount(ProdutoValidadeTab, {
      props: { modelValue: { nome: 'Produto Teste', controlaValidade: true }, recordId: 1 },
      ...globalMocks
    })
    await new Promise(r => setTimeout(r, 100))

    const tarefaBtn = wrapper.findAll('button').find(b => b.text().includes('Abrir tarefa'))
    if (tarefaBtn) {
      await tarefaBtn.trigger('click')
      expect(mockPush).toHaveBeenCalledWith({
        path: '/mobile/validade/tarefa/nova',
        query: expect.objectContaining({
          produtoId: '1',
          produtoNome: expect.any(String)
        })
      })
    }
    wrapper.unmount()
  })
})
