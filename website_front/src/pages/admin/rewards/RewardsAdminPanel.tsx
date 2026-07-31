import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';
import apiClient from '@/lib/api-client';
import { useAuth } from '@/hooks/useAuth';
import { useNavigate } from 'react-router-dom';

interface ClienteRef {
  id: number;
  nome: string;
  email: string;
}

interface Reward {
  id: number;
  userId: number;
  customerName: string;
  title: string;
  description: string;
  imageUrl: string;
  validUntil: string;
  status: string;
  createdAt: string;
  redeemedAt: string | null;
  notificationHistoryId: number | null;
}

export default function RewardsAdminPanel() {
  const { auth } = useAuth();
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState<ClienteRef[]>([]);
  const [selectedUser, setSelectedUser] = useState<ClienteRef | null>(null);
  const [userRewards, setUserRewards] = useState<Reward[]>([]);
  const [allRewards, setAllRewards] = useState<Reward[]>([]);
  const [showSearchResults, setShowSearchResults] = useState(false);

  // Carregar todas as recompensas para KPIs e histórico
  useEffect(() => {
    loadAllRewards();
  }, []);

  const loadAllRewards = async () => {
    try {
      // Usar o novo endpoint que retorna recompensas com nomes dos clientes
      const response = await apiClient.get('/api/rewards/with-customer-names');
      setAllRewards(response.data);
    } catch (error) {
      console.error('Erro ao carregar recompensas:', error);
      toast.error('Erro ao carregar recompensas');
    }
  };

  // Busca de clientes com debounce
  useEffect(() => {
    const debounceTimer = setTimeout(() => {
      if (searchTerm.trim() && searchTerm.trim().length >= 2) {
        searchClients(searchTerm);
      } else {
        setSearchResults([]);
        setShowSearchResults(false);
      }
    }, 300);

    return () => clearTimeout(debounceTimer);
  }, [searchTerm]);

  const searchClients = async (query: string) => {
    try {
      const response = await apiClient.get(`/api/clientes-ref/search?q=${encodeURIComponent(query)}`);
      setSearchResults(response.data);
      setShowSearchResults(true);
    } catch (error) {
      console.error('Erro ao buscar clientes:', error);
      toast.error('Erro ao buscar clientes');
    }
  };

  const selectUser = (user: ClienteRef) => {
    setSelectedUser(user);
    setSearchTerm(user.nome); // Preenche o campo com o nome do usuário selecionado
    setShowSearchResults(false);
    
    // Carregar recompensas do usuário
    loadUserRewards(user.id);
  };

  const loadUserRewards = async (userId: number) => {
    try {
      const response = await apiClient.get(`/api/rewards/user/${userId}`);
      setUserRewards(response.data);
    } catch (error) {
      console.error('Erro ao carregar recompensas do usuário:', error);
      toast.error('Erro ao carregar recompensas do usuário');
    }
  };

  const handleRedeemReward = async (rewardId: number) => {
    try {
      const response = await apiClient.post(`/api/rewards/${rewardId}/redeem`);
      if (response.status === 200) {
        toast.success('Recompensa marcada como resgatada!');
        
        // Atualizar o status da recompensa na lista do usuário
        setUserRewards(prevRewards => 
          prevRewards.map(reward => 
            reward.id === rewardId 
              ? { ...reward, status: 'REDEEMED', redeemedAt: new Date().toISOString() } 
              : reward
          )
        );
        
        // Atualizar também na lista geral
        setAllRewards(prevRewards => 
          prevRewards.map(reward => 
            reward.id === rewardId 
              ? { ...reward, status: 'REDEEMED', redeemedAt: new Date().toISOString() } 
              : reward
          )
        );
      }
    } catch (error: any) {
      console.error('Erro ao resgatar recompensa:', error);
      const errorMessage = error.response?.data || error.message || 'Erro ao resgatar recompensa';
      toast.error(`Erro ao resgatar: ${errorMessage}`);
    }
  };

  // Calcular KPIs
  const totalRewards = allRewards.length;
  const availableRewards = allRewards.filter(r => r.status === 'AVAILABLE').length;
  const redeemedRewards = allRewards.filter(r => r.status === 'REDEEMED').length;

  return (
    <div className="container mx-auto py-6 px-4" style={{ backgroundColor: 'white' }}>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-8">
        <h1 className="text-3xl font-bold text-[#2A1F1B]">Painel de Recompensas</h1>
        <Button
          onClick={() => navigate('/admin/rewards/draw')}
          className="bg-[#D7B899] hover:bg-[#C8A57A] text-[#2A1F1B] font-semibold"
        >
          Realizar Sorteio
        </Button>
      </div>
      
      {/* Seção de KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <Card style={{ border: '1px solid #D7B899' }}>
          <CardContent className="p-6">
            <div className="text-center">
              <p className="text-3xl font-bold text-[#D7B899]">{totalRewards}</p>
              <p className="text-[#2A1F1B]">Total Recompensas</p>
            </div>
          </CardContent>
        </Card>
        
        <Card style={{ border: '1px solid #D7B899' }}>
          <CardContent className="p-6">
            <div className="text-center">
              <p className="text-3xl font-bold text-[#D7B899]">{availableRewards}</p>
              <p className="text-[#2A1F1B]">Disponíveis</p>
            </div>
          </CardContent>
        </Card>
        
        <Card style={{ border: '1px solid #D7B899' }}>
          <CardContent className="p-6">
            <div className="text-center">
              <p className="text-3xl font-bold text-[#D7B899]">{redeemedRewards}</p>
              <p className="text-[#2A1F1B]">Resgatadas</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Seção de Retirada de Prêmio */}
        <Card style={{ border: '1px solid #D7B899' }}>
          <CardHeader>
            <CardTitle className="text-[#2A1F1B]">Retirada de Prêmio</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="relative">
                <Input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Buscar cliente por nome ou email"
                  className="pr-10"
                  style={{ borderColor: '#D7B899', color: '#2A1F1B' }}
                />
                
                {showSearchResults && searchResults.length > 0 && (
                  <div 
                    className="absolute z-10 w-full mt-1 bg-white border border-[#D7B899] rounded-md shadow-lg max-h-60 overflow-auto"
                    style={{ backgroundColor: 'white', border: '1px solid #D7B899' }}
                  >
                    {searchResults.map((user) => (
                      <div
                        key={user.id}
                        className="p-2 hover:bg-[#D7B899]/20 cursor-pointer border-b border-[#D7B899]/20 last:border-b-0"
                        style={{ color: '#2A1F1B' }}
                        onClick={() => selectUser(user)}
                      >
                        <div className="font-medium">{user.nome}</div>
                        <div className="text-sm opacity-80">{user.email}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {selectedUser && (
                <div className="space-y-4">
                  <Card style={{ backgroundColor: 'rgba(215, 184, 153, 0.1)', border: '1px solid #D7B899' }}>
                    <CardContent className="p-4">
                      <h3 className="font-bold text-lg text-[#2A1F1B]">{selectedUser.nome}</h3>
                      <p className="text-[#2A1F1B]">{selectedUser.email}</p>
                    </CardContent>
                  </Card>

                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead>
                        <tr className="border-b" style={{ borderColor: '#D7B899' }}>
                          <th className="text-left py-2 text-[#2A1F1B]">Recompensa</th>
                          <th className="text-left py-2 text-[#2A1F1B]">Validade</th>
                          <th className="text-left py-2 text-[#2A1F1B]">Status</th>
                          <th className="text-left py-2 text-[#2A1F1B]">Ação</th>
                        </tr>
                      </thead>
                      <tbody>
                        {userRewards.length > 0 ? (
                          userRewards.map((reward) => (
                            <tr key={reward.id} className="border-b" style={{ borderColor: '#D7B899' }}>
                              <td className="py-2 text-[#2A1F1B]">{reward.title}</td>
                              <td className="py-2 text-[#2A1F1B]">{new Date(reward.validUntil).toLocaleDateString()}</td>
                              <td className="py-2 text-[#2A1F1B]">
                                {reward.status === 'AVAILABLE' ? (
                                  <Badge style={{ backgroundColor: '#D7B899', color: '#2A1F1B' }}>
                                    Disponível
                                  </Badge>
                                ) : (
                                  <Badge variant="outline" style={{ borderColor: '#8B7355', color: '#8B7355' }}>
                                    Resgatada
                                  </Badge>
                                )}
                              </td>
                              <td className="py-2">
                                {reward.status === 'AVAILABLE' && (
                                  <Button
                                    size="sm"
                                    onClick={() => handleRedeemReward(reward.id)}
                                    style={{ backgroundColor: '#D7B899', color: '#2A1F1B' }}
                                  >
                                    Marcar retirada
                                  </Button>
                                )}
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={4} className="py-4 text-center text-[#2A1F1B]">Nenhuma recompensa encontrada</td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Histórico Geral */}
        <Card style={{ border: '1px solid #D7B899' }}>
          <CardHeader>
            <CardTitle className="text-[#2A1F1B]">Histórico Geral</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b" style={{ borderColor: '#D7B899' }}>
                    <th className="text-left py-2 text-[#2A1F1B]">Data</th>
                    <th className="text-left py-2 text-[#2A1F1B]">Cliente</th>
                    <th className="text-left py-2 text-[#2A1F1B]">Recompensa</th>
                    <th className="text-left py-2 text-[#2A1F1B]">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {allRewards.slice(0, 10).map((reward) => (
                    <tr key={reward.id} className="border-b" style={{ borderColor: '#D7B899' }}>
                      <td className="py-2 text-[#2A1F1B]">{new Date(reward.createdAt).toLocaleDateString()}</td>
                      <td className="py-2 text-[#2A1F1B]">{reward.customerName}</td>
                      <td className="py-2 text-[#2A1F1B]">{reward.title}</td>
                      <td className="py-2 text-[#2A1F1B]">
                        {reward.status === 'AVAILABLE' ? (
                          <Badge style={{ backgroundColor: '#D7B899', color: '#2A1F1B' }}>
                            Disponível
                          </Badge>
                        ) : (
                          <Badge variant="outline" style={{ borderColor: '#8B7355', color: '#8B7355' }}>
                            Resgatada
                          </Badge>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
