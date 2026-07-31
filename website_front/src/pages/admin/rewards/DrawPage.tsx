import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { toast } from 'sonner';
import apiClient from '@/lib/api-client';
import { useAuth } from '@/hooks/useAuth';

interface RewardDrawRequest {
  title: string;
  description: string;
  imageUrl: string;
  validUntil: string;
}

interface RewardResponse {
  id: number;
  userId: number;
  title: string;
  description: string;
  imageUrl: string;
  validUntil: string;
  status: string;
  createdAt: string;
  redeemedAt: string | null;
  notificationHistoryId: number | null;
}

export default function RewardsDrawPage() {
  const { auth } = useAuth();
  const [formData, setFormData] = useState<RewardDrawRequest>({
    title: '',
    description: '',
    imageUrl: '',
    validUntil: '',
  });
  const [isLoading, setIsLoading] = useState(false);
  const [rewardResult, setRewardResult] = useState<RewardResponse | null>(null);
  const [rewards, setRewards] = useState<RewardResponse[]>([]);
  const [isRewardsLoading, setIsRewardsLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;

    // Normalizar o valor de validUntil se necessário
    let normalizedValue = value;
    if (name === 'validUntil' && value && !value.endsWith(':00')) {
      // Se o formato for "YYYY-MM-DDTHH:mm", adicionar ":00" para segundos
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
        normalizedValue = value + ':00';
      }
    }

    setFormData(prev => ({
      ...prev,
      [name]: normalizedValue
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setRewardResult(null);

    try {
      const response = await apiClient.post<RewardResponse>('/api/rewards/draw', formData);
      const reward = response.data;

      setRewardResult(reward);
      toast.success('Sorteio realizado com sucesso!');
      console.log('Recompensa criada:', reward);

      // Carregar recompensas do usuário sorteado
      await loadRewardsForUser(reward.userId);
    } catch (error: any) {
      console.error('Erro ao realizar sorteio:', error);
      const errorMessage = error.response?.data || error.message || 'Erro ao realizar sorteio';
      toast.error(`Erro: ${errorMessage}`);
    } finally {
      setIsLoading(false);
    }
  };

  const loadRewardsForUser = async (userId: number) => {
    if (!userId) return;

    setIsRewardsLoading(true);
    try {
      const response = await apiClient.get<RewardResponse[]>(`/api/rewards/user/${userId}`);
      setRewards(response.data);
    } catch (error: any) {
      console.error('Erro ao carregar recompensas do usuário:', error);
      const errorMessage = error.response?.data || error.message || 'Erro ao carregar recompensas';
      toast.error(`Erro ao carregar recompensas: ${errorMessage}`);
      setRewards([]);
    } finally {
      setIsRewardsLoading(false);
    }
  };

  const handleRedeemReward = async (rewardId: number) => {
    try {
      const response = await apiClient.post(`/api/rewards/${rewardId}/redeem`);
      if (response.status === 200) {
        toast.success('Recompensa resgatada com sucesso!');
        // Atualizar a lista de recompensas
        if (rewardResult) {
          await loadRewardsForUser(rewardResult.userId);
        }
      }
    } catch (error: any) {
      console.error('Erro ao resgatar recompensa:', error);
      const errorMessage = error.response?.data || error.message || 'Erro ao resgatar recompensa';
      toast.error(`Erro ao resgatar: ${errorMessage}`);
    }
  };

  return (
    <div className="container mx-auto py-6 px-4">
      <h1 className="text-3xl font-bold mb-6">Sorteio de Recompensas</h1>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div>
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Formulário de Sorteio</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="title">Título da Recompensa *</Label>
                  <Input
                    id="title"
                    name="title"
                    value={formData.title}
                    onChange={handleChange}
                    required
                    placeholder="Ex: Você ganhou 1 chopp grátis!"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="description">Descrição</Label>
                  <Textarea
                    id="description"
                    name="description"
                    value={formData.description}
                    onChange={handleChange}
                    placeholder="Ex: Apresente este voucher no balcão"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="imageUrl">Imagem (URL)</Label>
                  <Input
                    id="imageUrl"
                    name="imageUrl"
                    type="url"
                    value={formData.imageUrl}
                    onChange={handleChange}
                    placeholder="https://example.com/image.jpg"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="validUntil">Validade *</Label>
                  <Input
                    id="validUntil"
                    name="validUntil"
                    type="datetime-local"
                    value={formData.validUntil}
                    onChange={handleChange}
                    required
                  />
                </div>

                <Button type="submit" disabled={isLoading}>
                  {isLoading ? 'Realizando Sorteio...' : 'Realizar Sorteio'}
                </Button>
              </form>
            </CardContent>
          </Card>

          {rewardResult && (
            <Card>
              <CardHeader>
                <CardTitle>Resultado do Sorteio</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <p><strong>ID da Recompensa:</strong> {rewardResult.id}</p>
                  <p><strong>Usuário Sorteado:</strong> {rewardResult.userId}</p>
                  <p><strong>Título:</strong> {rewardResult.title}</p>
                  <p><strong>Validade:</strong> {new Date(rewardResult.validUntil).toLocaleString()}</p>
                  <p><strong>Status:</strong> {rewardResult.status}</p>
                  {rewardResult.notificationHistoryId && (
                    <p><strong>ID do Histórico de Notificação:</strong> {rewardResult.notificationHistoryId}</p>
                  )}
                </div>
                <div className="mt-4">
                  <Button onClick={() => loadRewardsForUser(rewardResult.userId)}>
                    Ver recompensas desse usuário
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}
        </div>

        <div>
          <Card>
            <CardHeader>
              <CardTitle>Recompensas do Usuário</CardTitle>
            </CardHeader>
            <CardContent>
              {isRewardsLoading ? (
                <p>Carregando recompensas...</p>
              ) : rewards.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b">
                        <th className="text-left py-2">ID</th>
                        <th className="text-left py-2">Título</th>
                        <th className="text-left py-2">Status</th>
                        <th className="text-left py-2">Validade</th>
                        <th className="text-left py-2">Criado Em</th>
                        <th className="text-left py-2">Resgatado Em</th>
                        <th className="text-left py-2">Histórico Notif.</th>
                        <th className="text-left py-2">Ações</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rewards.map((reward) => (
                        <tr key={reward.id} className="border-b">
                          <td className="py-2">{reward.id}</td>
                          <td className="py-2">{reward.title}</td>
                          <td className="py-2">{reward.status}</td>
                          <td className="py-2">{new Date(reward.validUntil).toLocaleString()}</td>
                          <td className="py-2">{new Date(reward.createdAt).toLocaleString()}</td>
                          <td className="py-2">{reward.redeemedAt ? new Date(reward.redeemedAt).toLocaleString() : '-'}</td>
                          <td className="py-2">{reward.notificationHistoryId || '-'}</td>
                          <td className="py-2">
                            {reward.status === 'AVAILABLE' && (
                              <Button
                                size="sm"
                                onClick={() => handleRedeemReward(reward.id)}
                              >
                                Marcar como resgatada
                              </Button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p>Nenhuma recompensa encontrada para este usuário.</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}