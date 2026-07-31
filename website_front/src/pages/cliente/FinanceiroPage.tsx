import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { apiConfig } from '@/config/api';
import axios from '@/lib/axios';
import { formatCurrency } from '@/utils/format';
import { Loader2, Receipt, CheckCircle, Clock, AlertCircle } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

interface ParcelaDTO {
  numeroParcela: number;
  dataVencimento: string;
  valor: number;
  recebida: boolean;
}

interface ContaReceberDTO {
  id: number;
  descricao: string;
  valorTotal: number;
  valorPendente: number;
  quitada: boolean;
  parcelas: ParcelaDTO[];
  dataCadastro: string;
}

interface MinhasContasResponse {
  clienteNome: string;
  resumo: {
    totalAberto: number;
  };
  faturasAbertas: ContaReceberDTO[];
  historico: ContaReceberDTO[];
}

export default function FinanceiroPage() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [dados, setDados] = useState<MinhasContasResponse | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const res = await axios.get<MinhasContasResponse>(`${apiConfig.erpBaseUrl}/api/contas-receber/me`);
      setDados(res.data);
    } catch (error) {
      console.error('Erro ao carregar contas:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-';
    if (dateStr.length === 10) dateStr += 'T12:00:00';
    return new Date(dateStr).toLocaleDateString('pt-BR');
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-soft-white flex flex-col">
        <Header />
        <div className="flex-1 flex items-center justify-center text-forest-dark/60">
          <Loader2 className="w-8 h-8 animate-spin" />
        </div>
      </div>
    );
  }

  const totalAberto = dados?.resumo.totalAberto || 0;

  return (
    <div className="min-h-screen bg-soft-white font-sans text-forest-dark flex flex-col">
      <Header />

      <main className="flex-1 container mx-auto px-4 py-8 md:py-12 max-w-4xl space-y-8">
        {/* Saudação */}
        <div>
          <h1 className="text-3xl font-display text-forest-dark mb-2">
            Financeiro
          </h1>
          <p className="text-forest-dark/70">
            Acompanhe suas faturas e histórico de pagamentos.
          </p>
        </div>

        {/* Resumo Financeiro */}
        <section>
          {totalAberto > 0 ? (
            <Card className="border-coral-accent/20 bg-white shadow-lg overflow-hidden">
              <div className="h-1.5 bg-coral-accent w-full" />
              <CardContent className="p-6 md:p-8 flex flex-col md:flex-row items-center justify-between gap-6">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-full bg-coral-accent/10 flex items-center justify-center text-coral-accent">
                    <AlertCircle className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="text-lg font-medium text-forest-dark">Fatura em Aberto</h3>
                    <p className="text-forest-dark/60 text-sm">Total acumulado a vencer</p>
                  </div>
                </div>
                <div className="text-center md:text-right">
                  <div className="text-3xl font-bold text-forest-dark">
                    {formatCurrency(totalAberto * 100)}
                  </div>
                  <p className="text-xs text-forest-dark/50 mt-1">
                    Verifique os vencimentos abaixo
                  </p>
                </div>
              </CardContent>
            </Card>
          ) : (
             <Card className="border-emerald-200 bg-emerald-50/50 shadow-sm overflow-hidden">
              <CardContent className="p-6 md:p-8 flex items-center gap-4">
                <div className="w-12 h-12 rounded-full bg-emerald-100 flex items-center justify-center text-emerald-600">
                  <CheckCircle className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-emerald-900">Tudo em dia!</h3>
                  <p className="text-emerald-700 text-sm">Você não possui faturas pendentes no momento.</p>
                </div>
              </CardContent>
            </Card>
          )}
        </section>

        {/* Detalhamento de Faturas Abertas */}
        {dados?.faturasAbertas && dados.faturasAbertas.length > 0 && (
          <section className="space-y-4">
            <h2 className="text-xl font-display text-forest-dark flex items-center gap-2">
              <Receipt className="w-5 h-5 text-coral-accent" />
              Detalhamento de Pendências
            </h2>
            <div className="grid gap-4">
              {dados.faturasAbertas.map((conta) => {
                const vencimento = conta.parcelas?.find(p => !p.recebida)?.dataVencimento;
                
                return (
                  <Card key={conta.id} className="border-forest-green/10 bg-white hover:border-forest-green/30 transition-colors">
                    <CardContent className="p-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-forest-dark">{conta.descricao}</span>
                          <Badge variant="outline" className="border-coral-accent/40 text-coral-accent bg-coral-accent/5">
                            Aberto
                          </Badge>
                        </div>
                        <div className="text-sm text-forest-dark/60 flex items-center gap-2">
                          <Clock className="w-3.5 h-3.5" />
                          Vencimento: {formatDate(vencimento || '')}
                        </div>
                      </div>
                      <div className="flex flex-col items-end">
                         <span className="text-lg font-semibold text-forest-dark">
                           {formatCurrency(conta.valorPendente * 100)}
                         </span>
                         <span className="text-xs text-forest-dark/40">
                           Total original: {formatCurrency(conta.valorTotal * 100)}
                         </span>
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </section>
        )}

        {/* Histórico Recente */}
        {dados?.historico && dados.historico.length > 0 && (
          <section className="space-y-4 pt-4 border-t border-forest-green/10">
            <h2 className="text-lg font-medium text-forest-dark/80">Histórico de Faturas Fechadas</h2>
            <div className="bg-white rounded-xl border border-forest-green/10 overflow-hidden">
               <div className="divide-y divide-forest-green/5">
                 {dados.historico.map((conta) => (
                   <div key={conta.id} className="p-4 flex items-center justify-between hover:bg-forest-green/5 transition-colors">
                      <div className="space-y-0.5">
                        <div className="text-sm font-medium text-forest-dark">{conta.descricao}</div>
                        <div className="text-xs text-forest-dark/50">
                          {formatDate(conta.dataCadastro)}
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                         <span className="text-sm font-medium text-forest-dark/70">
                           {formatCurrency(conta.valorTotal * 100)}
                         </span>
                         <Badge variant="secondary" className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">
                           Pago
                         </Badge>
                      </div>
                   </div>
                 ))}
               </div>
            </div>
          </section>
        )}

      </main>
      <Footer />
    </div>
  );
}
