import { useState, useEffect } from 'react';
import { apiConfig } from '@/config/api';
import axios from '@/lib/axios';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { ScrollArea } from '@/components/ui/scroll-area';
import { X, Plus } from 'lucide-react';

interface Pessoa {
  sessaoConvidadoId: number;
  nome: string;
  subtotalCentavos: number;
  pagoCentavos: number;
  devidoCentavos: number;
}

interface ContaMesa {
  totalMesaCentavos: number;
  taxaServicoCentavos: number;
  descontosCentavos: number;
  pagoCentavos: number;
  devidoCentavos: number;
  pessoas: Pessoa[];
}

interface PagamentoHistorico {
  id: number;
  beneficiario: string;
  beneficiarioId: number | null;
  pagante: string | null;
  paganteId: number | null;
  valorCentavos: number;
  metodo: string;
  status: string;
  criadoEm: string;
  pagoEm: string | null;
}

interface PagamentoFormItem {
  id: string;
  beneficiarioId: number | null; // null = mesa toda
  paganteId: number | null;
  valorCentavos: string;
  metodo: 'pix' | 'card' | 'cash' | 'voucher';
}

interface PagamentoDialogProps {
  open: boolean;
  onClose: () => void;
  sessaoMesaId: number;
  mesaSlug: string;
}

export function PagamentoDialog({
  open,
  onClose,
  sessaoMesaId,
  mesaSlug,
}: PagamentoDialogProps) {
  const [conta, setConta] = useState<ContaMesa | null>(null);
  const [historico, setHistorico] = useState<PagamentoHistorico[]>([]);
  const [pagamentos, setPagamentos] = useState<PagamentoFormItem[]>([
    {
      id: '1',
      beneficiarioId: null,
      paganteId: null,
      valorCentavos: '',
      metodo: 'cash',
    },
  ]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      loadData();
    }
  }, [open, sessaoMesaId]);

  const loadData = async () => {
    setLoading(true);
    try {
      // Buscar conta da mesa
      const contaRes = await axios.get<ContaMesa>(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/conta`
      );
      setConta(contaRes.data);

      // Buscar histórico de pagamentos
      const historicoRes = await axios.get<{ pagamentos: PagamentoHistorico[] }>(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/pagamentos`
      );
      setHistorico(historicoRes.data.pagamentos || []);
    } catch (error) {
      console.error('Erro ao carregar dados:', error);
    } finally {
      setLoading(false);
    }
  };

  const adicionarPagamento = () => {
    const newId = String(Date.now());
    setPagamentos([
      ...pagamentos,
      {
        id: newId,
        beneficiarioId: null,
        paganteId: null,
        valorCentavos: '',
        metodo: 'cash',
      },
    ]);
  };

  const removerPagamento = (id: string) => {
    if (pagamentos.length === 1) return;
    setPagamentos(pagamentos.filter((p) => p.id !== id));
  };

  const atualizarPagamento = (
    id: string,
    field: keyof PagamentoFormItem,
    value: any
  ) => {
    setPagamentos(
      pagamentos.map((p) =>
        p.id === id ? { ...p, [field]: value } : p
      )
    );
  };

  const quitarTotal = (id: string, beneficiarioId: number | null) => {
    if (beneficiarioId === null) {
      // Quitar mesa toda
      const devido = conta?.devidoCentavos || 0;
      atualizarPagamento(id, 'valorCentavos', String(devido));
    } else {
      // Quitar pessoa específica
      const pessoa = conta?.pessoas.find(
        (p) => p.sessaoConvidadoId === beneficiarioId
      );
      if (pessoa) {
        atualizarPagamento(id, 'valorCentavos', String(pessoa.devidoCentavos));
      }
    }
  };

  const handleSubmit = async () => {
    // Validações
    const pagamentosValidos = pagamentos.filter(
      (p) => p.valorCentavos && Number(p.valorCentavos) > 0
    );

    if (pagamentosValidos.length === 0) {
      alert('Informe ao menos um pagamento com valor válido');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        pagamentos: pagamentosValidos.map((p) => ({
          sessaoConvidadoId: p.beneficiarioId,
          paganteId: p.paganteId,
          valorCentavos: Number(p.valorCentavos),
          metodo: p.metodo,
        })),
      };

      await axios.post(
        `${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${sessaoMesaId}/pagamentos/multiplo`,
        payload
      );

      // Recarregar dados
      await loadData();

      // Resetar formulário
      setPagamentos([
        {
          id: String(Date.now()),
          beneficiarioId: null,
          paganteId: null,
          valorCentavos: '',
          metodo: 'cash',
        },
      ]);

      alert('Pagamento(s) registrado(s) com sucesso!');
    } catch (error: any) {
      console.error('Erro ao registrar pagamentos:', error);
      const msg =
        error.response?.data?.error?.message || 'Erro ao registrar pagamentos';
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const formatCurrency = (centavos: number) => {
    return `R$ ${(centavos / 100).toFixed(2)}`;
  };

  const formatDateTime = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  };

  const metodosLabel = {
    pix: 'PIX',
    card: 'Cartão',
    cash: 'Dinheiro',
    voucher: 'Voucher',
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>Gerenciar Pagamentos - Mesa {mesaSlug}</DialogTitle>
        </DialogHeader>

        <ScrollArea className="flex-1 pr-4">
          {loading ? (
            <div className="text-center py-8 text-gray-500">Carregando...</div>
          ) : (
            <div className="space-y-6">
              {/* Resumo da Conta */}
              <div className="border rounded-lg p-4 bg-gray-50">
                <h3 className="font-semibold mb-3">📊 Resumo da Conta</h3>
                <div className="grid grid-cols-3 gap-3 text-sm">
                  <div>
                    <div className="text-gray-600">Total</div>
                    <div className="font-medium text-lg">
                      {formatCurrency(conta?.totalMesaCentavos || 0)}
                    </div>
                  </div>
                  <div>
                    <div className="text-gray-600">Pago</div>
                    <div className="font-medium text-lg text-green-600">
                      {formatCurrency(conta?.pagoCentavos || 0)}
                    </div>
                  </div>
                  <div>
                    <div className="text-gray-600">Devido</div>
                    <div className="font-medium text-lg text-red-600">
                      {formatCurrency(conta?.devidoCentavos || 0)}
                    </div>
                  </div>
                </div>
              </div>

              {/* Pessoas na Mesa */}
              <div>
                <h3 className="font-semibold mb-2">👥 Pessoas na Mesa</h3>
                <div className="space-y-2">
                  {conta?.pessoas.map((pessoa) => (
                    <div
                      key={pessoa.sessaoConvidadoId}
                      className="border rounded p-2 flex justify-between items-center text-sm"
                    >
                      <span className="font-medium">{pessoa.nome}</span>
                      <span className="text-gray-600">
                        Devido: {formatCurrency(pessoa.devidoCentavos)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Formulário de Pagamentos */}
              <div>
                <h3 className="font-semibold mb-3">💵 Registrar Pagamentos</h3>
                <div className="space-y-4">
                  {pagamentos.map((pag, index) => (
                    <div
                      key={pag.id}
                      className="border rounded-lg p-4 bg-white relative"
                    >
                      <div className="flex justify-between items-start mb-3">
                        <h4 className="font-medium">Pagamento {index + 1}</h4>
                        {pagamentos.length > 1 && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removerPagamento(pag.id)}
                          >
                            <X className="h-4 w-4" />
                          </Button>
                        )}
                      </div>

                      <div className="grid gap-3">
                        {/* Beneficiário */}
                        <div>
                          <Label>Pagar por</Label>
                          <select
                            className="w-full border rounded px-3 py-2 mt-1"
                            value={pag.beneficiarioId || ''}
                            onChange={(e) =>
                              atualizarPagamento(
                                pag.id,
                                'beneficiarioId',
                                e.target.value ? Number(e.target.value) : null
                              )
                            }
                          >
                            <option value="">Mesa toda</option>
                            {conta?.pessoas.map((p) => (
                              <option
                                key={p.sessaoConvidadoId}
                                value={p.sessaoConvidadoId}
                              >
                                {p.nome}
                              </option>
                            ))}
                          </select>
                        </div>

                        {/* Pagante (opcional) */}
                        <div>
                          <Label>Quem paga (opcional)</Label>
                          <select
                            className="w-full border rounded px-3 py-2 mt-1"
                            value={pag.paganteId || ''}
                            onChange={(e) =>
                              atualizarPagamento(
                                pag.id,
                                'paganteId',
                                e.target.value ? Number(e.target.value) : null
                              )
                            }
                          >
                            <option value="">Não especificado</option>
                            {conta?.pessoas.map((p) => (
                              <option
                                key={p.sessaoConvidadoId}
                                value={p.sessaoConvidadoId}
                              >
                                {p.nome}
                              </option>
                            ))}
                          </select>
                        </div>

                        {/* Valor */}
                        <div>
                          <Label>Valor (centavos)</Label>
                          <div className="flex gap-2 mt-1">
                            <Input
                              type="number"
                              value={pag.valorCentavos}
                              onChange={(e) =>
                                atualizarPagamento(
                                  pag.id,
                                  'valorCentavos',
                                  e.target.value
                                )
                              }
                              placeholder="0"
                              className="flex-1"
                            />
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                quitarTotal(pag.id, pag.beneficiarioId)
                              }
                            >
                              Quitar Total
                            </Button>
                          </div>
                          {pag.valorCentavos && (
                            <div className="text-xs text-gray-600 mt-1">
                              = {formatCurrency(Number(pag.valorCentavos))}
                            </div>
                          )}
                        </div>

                        {/* Método */}
                        <div>
                          <Label>Método de Pagamento</Label>
                          <RadioGroup
                            value={pag.metodo}
                            onValueChange={(value: any) =>
                              atualizarPagamento(pag.id, 'metodo', value)
                            }
                            className="flex gap-4 mt-2"
                          >
                            <div className="flex items-center space-x-2">
                              <RadioGroupItem value="pix" id={`pix-${pag.id}`} />
                              <Label htmlFor={`pix-${pag.id}`}>PIX</Label>
                            </div>
                            <div className="flex items-center space-x-2">
                              <RadioGroupItem value="card" id={`card-${pag.id}`} />
                              <Label htmlFor={`card-${pag.id}`}>Cartão</Label>
                            </div>
                            <div className="flex items-center space-x-2">
                              <RadioGroupItem value="cash" id={`cash-${pag.id}`} />
                              <Label htmlFor={`cash-${pag.id}`}>Dinheiro</Label>
                            </div>
                            <div className="flex items-center space-x-2">
                              <RadioGroupItem value="voucher" id={`voucher-${pag.id}`} />
                              <Label htmlFor={`voucher-${pag.id}`}>Voucher</Label>
                            </div>
                          </RadioGroup>
                        </div>
                      </div>
                    </div>
                  ))}

                  <Button
                    type="button"
                    variant="outline"
                    onClick={adicionarPagamento}
                    className="w-full"
                  >
                    <Plus className="h-4 w-4 mr-2" />
                    Adicionar Pagamento
                  </Button>
                </div>
              </div>

              {/* Histórico */}
              {historico.length > 0 && (
                <div>
                  <h3 className="font-semibold mb-2">📜 Histórico de Pagamentos</h3>
                  <ScrollArea className="h-48 border rounded">
                    <div className="p-3 space-y-2">
                      {historico.map((hist) => (
                        <div
                          key={hist.id}
                          className="border-b pb-2 last:border-0 text-sm"
                        >
                          <div className="flex justify-between">
                            <span className="font-medium">
                              {metodosLabel[hist.metodo as keyof typeof metodosLabel] || hist.metodo.toUpperCase()}
                            </span>
                            <span className="font-medium">
                              {formatCurrency(hist.valorCentavos)}
                            </span>
                          </div>
                          <div className="text-gray-600 text-xs mt-1">
                            {hist.pagante && <>{hist.pagante} → </>}
                            {hist.beneficiario}
                          </div>
                          <div className="text-gray-400 text-xs">
                            {formatDateTime(hist.criadoEm)}
                          </div>
                        </div>
                      ))}
                    </div>
                  </ScrollArea>
                </div>
              )}
            </div>
          )}
        </ScrollArea>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={handleSubmit} disabled={submitting || loading}>
            {submitting ? 'Registrando...' : 'Registrar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
