import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Plus,
  Edit,
  Copy,
  Trash2,
  Calendar,
  Settings,
  Eye,
  Check,
  Download,
  Upload
} from 'lucide-react';
import { Theme } from '@/types/theme';
import { useToast } from '@/hooks/use-toast';
import { useTheme } from '@/contexts/ThemeContext';
import { ThemeGeneralForm } from '@/components/admin/themes/ThemeGeneralForm';
import { ThemeTokensForm } from '@/components/admin/themes/ThemeTokensForm';
import { ThemeAssetsForm } from '@/components/admin/themes/ThemeAssetsForm';
import { ThemeContentForm } from '@/components/admin/themes/ThemeContentForm';
import { ThemeSeoForm } from '@/components/admin/themes/ThemeSeoForm';
import { ThemeAndroidForm } from '@/components/admin/themes/ThemeAndroidForm';
import { ThemeFormTabs, ThemeFormTab } from '@/components/admin/themes/ThemeFormTabs';

export default function TemasPage() {
  const { toast } = useToast();
  const { refreshTheme } = useTheme();
  const [temas, setTemas] = useState<Theme[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTheme, setEditingTheme] = useState<Theme | null>(null);
  const [tenantId, setTenantId] = useState('baronesa'); // Padrão, pode ser alterado pelo usuário
  const [activeThemeId, setActiveThemeId] = useState<number | null>(null); // Armazena o ID do tema ativo
  const [selectedSwitcherThemeId, setSelectedSwitcherThemeId] = useState<number | ''>('');
  
  // Estados para Redeploy
  const [isRedeploying, setIsRedeploying] = useState(false);
  const [redeployStatus, setRedeployStatus] = useState('');
  
  const activeTheme = temas.find((theme) => theme.id === activeThemeId);

  const startRedeployPolling = async () => {
    setIsRedeploying(true);
    setRedeployStatus('Solicitando reinicialização...');
    
    const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
    let attempts = 0;
    const maxAttempts = 30; // 30 * 2s = 60s timeout

    const checkStatus = async () => {
      try {
        const response = await fetch(`${WEBSITE_API_URL}/api/admin/redeploy/status`, {
            headers: { 'Authorization': `Bearer ${localStorage.getItem('auth_token')}` }
        });
        
        if (response.ok) {
            const data = await response.json();
            setRedeployStatus(data.status);
            
            if (data.status === 'COMPLETED' || data.status === 'IDLE') {
                // Sucesso!
                setRedeployStatus('Concluído! Recarregando página...');
                setTimeout(() => window.location.reload(), 1000);
                return;
            }
            if (data.status === 'FAILED') {
                setRedeployStatus('Falha na reinicialização. Verifique os logs.');
                setTimeout(() => setIsRedeploying(false), 5000);
                return;
            }
        }
      } catch (e) {
        // Ignora erros de rede momentâneos (esperado durante restart)
        console.log('Polling error (expected during restart):', e);
      }

      attempts++;
      if (attempts < maxAttempts) {
        setTimeout(checkStatus, 2000);
      } else {
        setRedeployStatus('Tempo limite excedido. Tente recarregar a página manualmente.');
        setTimeout(() => setIsRedeploying(false), 5000);
      }
    };

    // Inicia polling
    setTimeout(checkStatus, 2000);
  };

  // Carregar temas do backend e tema ativo
  useEffect(() => {
    const fetchTemas = async () => {
      try {
        setLoading(true);
        setError(null);

        // Carregar temas do tenant
        const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
        const response = await fetch(`${WEBSITE_API_URL}/api/themes?tenantId=${tenantId}`);
        if (!response.ok) {
          throw new Error(`Erro ao carregar temas: ${response.status} ${response.statusText}`);
        }
        const temaData = await response.json();
        setTemas(temaData);

        // Carregar tema ativo para o tenant
        const activeResponse = await fetch(`${WEBSITE_API_URL}/api/themes/public/theme/active?tenantId=${tenantId}`);
        if (activeResponse.ok) {
          const activeThemeData = await activeResponse.json();
          setActiveThemeId(activeThemeData.id);
        } else {
          // Se não houver tema ativo, não definimos nada
          setActiveThemeId(null);
        }
      } catch (err) {
        console.error('Erro ao carregar temas:', err);
        setError(err instanceof Error ? err.message : 'Erro desconhecido ao carregar temas');
      } finally {
        setLoading(false);
      }
    };

    fetchTemas();
  }, [tenantId]);

  useEffect(() => {
    setSelectedSwitcherThemeId(activeThemeId ?? '');
  }, [activeThemeId]);

  const handleCreateNew = () => {
    setEditingTheme({
      id: 0, // Indica novo tema
      name: '',
      status: 'DRAFT',
      tokens: {},
      assets: {},
      content: {},
      tenantId: tenantId,
      baseThemeId: undefined,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    } as Theme);
    setIsModalOpen(true);
  };

  const handleEdit = (theme: Theme) => {
    setEditingTheme(theme);
    setIsModalOpen(true);
  };

  /**
   * Função para definir o tema ativo (agendando com datas apropriadas)
   */
  const handleSetActive = async (themeId: number, skipConfirm = false) => {
    if (!skipConfirm && !confirm('Tem certeza que deseja tornar este tema ativo?')) return;

    try {
      // Agendar o tema para ativação imediata (sem data de término)
      const scheduleDTO = {
        validFrom: new Date().toISOString(), // Ativa imediatamente
        validTo: null, // Não expira (ou seja, ativo indefinidamente até outro ser definido)
        priority: 10 // Alta prioridade para o tema ativo
      };

      const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
      const response = await fetch(`${WEBSITE_API_URL}/api/themes/${themeId}/schedule`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('auth_token')}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(scheduleDTO)
      });

      if (!response.ok) {
        throw new Error(`Erro ao definir tema ativo: ${response.status} ${response.statusText}`);
      }

      // Atualizar o tema ativo localmente
      localStorage.setItem('currentTenantId', tenantId);
      await refreshTheme(tenantId);
      setActiveThemeId(themeId);
      setSelectedSwitcherThemeId(themeId);

      toast({
        title: 'Sucesso',
        description: 'Tema definido como ativo com sucesso!',
      });
    } catch (err) {
      console.error('Erro ao definir tema ativo:', err);
      toast({
        title: 'Erro',
        description: 'Falha ao definir tema como ativo. Tente novamente.',
        variant: 'destructive',
      });
    }
  };

  const selectedSwitcherTheme = typeof selectedSwitcherThemeId === 'number'
    ? temas.find((theme) => theme.id === selectedSwitcherThemeId)
    : undefined;
  const canApplySwitcher = Boolean(selectedSwitcherTheme) && selectedSwitcherTheme.status === 'PUBLISHED';

  const handleSwitcherApply = () => {
    if (!canApplySwitcher || typeof selectedSwitcherThemeId !== 'number') return;
    handleSetActive(selectedSwitcherThemeId, true);
  };

  const handleDuplicate = async (themeId: number) => {
    try {
      const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
      const response = await fetch(`${WEBSITE_API_URL}/api/themes/${themeId}/duplicate?newName=${encodeURIComponent('Cópia de ' + temas.find(t => t.id === themeId)?.name || 'tema')}&newTenantId=${tenantId}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('auth_token')}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error(`Erro ao duplicar tema: ${response.status} ${response.statusText}`);
      }

      // Recarregar temas
      const fetchTemas = async () => {
        const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
        const response = await fetch(`${WEBSITE_API_URL}/api/themes?tenantId=${tenantId}`);
        const data = await response.json();
        setTemas(data);
      };
      fetchTemas();
    } catch (err) {
      console.error('Erro ao duplicar tema:', err);
      alert('Erro ao duplicar tema: ' + (err instanceof Error ? err.message : 'Erro desconhecido'));
    }
  };

  const handleDelete = async (themeId: number) => {
    if (!window.confirm('Tem certeza que deseja excluir este tema?')) return;

    try {
      const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
      const response = await fetch(`${WEBSITE_API_URL}/api/themes/${themeId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('auth_token')}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error(`Erro ao excluir tema: ${response.status} ${response.statusText}`);
      }

      // Remover da lista local
      setTemas(temas.filter(t => t.id !== themeId));
    } catch (err) {
      console.error('Erro ao excluir tema:', err);
      alert('Erro ao excluir tema: ' + (err instanceof Error ? err.message : 'Erro desconhecido'));
    }
  };

  const handleSave = async (themeData: Theme, restart = false) => {
    try {
      const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
      const method = themeData.id === 0 ? 'POST' : 'PUT';
      let url = themeData.id === 0
        ? `${WEBSITE_API_URL}/api/themes`
        : `${WEBSITE_API_URL}/api/themes/${themeData.id}`;
      
      if (restart) {
        url += `?restart=true`;
      }

      const response = await fetch(url, {
        method,
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('auth_token')}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(themeData)
      });

      if (!response.ok) {
        throw new Error(`Erro ao ${method === 'POST' ? 'criar' : 'atualizar'} tema: ${response.status} ${response.statusText}`);
      }

      if (restart) {
        // Se pediu restart, iniciar polling
        setIsModalOpen(false); // Fecha modal de edição
        startRedeployPolling();
        return; 
      }

      // Recarregar temas normal
      const fetchTemas = async () => {
        const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;
        const response = await fetch(`${WEBSITE_API_URL}/api/themes?tenantId=${tenantId}`);
        const data = await response.json();
        setTemas(data);
      };
      fetchTemas();

      setIsModalOpen(false);
    } catch (err) {
      console.error(`Erro ao ${themeData.id === 0 ? 'criar' : 'atualizar'} tema:`, err);
      alert(`Erro ao ${themeData.id === 0 ? 'criar' : 'atualizar'} tema: ${err instanceof Error ? err.message : 'Erro desconhecido'}`);
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'DRAFT': return 'Rascunho';
      case 'PUBLISHED': return 'Publicado';
      case 'ARCHIVED': return 'Arquivado';
      default: return status;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'DRAFT': return 'bg-muted text-muted-foreground';
      case 'PUBLISHED': return 'bg-primary/20 text-primary';
      case 'ARCHIVED': return 'bg-accent/20 text-accent-foreground';
      default: return 'bg-muted text-muted-foreground';
    }
  };

  if (error) {
    return (
      <div className="p-6">
        <Card className="bg-white border-[#8B7355]/30">
          <CardHeader>
            <CardTitle className="text-red-600">Erro</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-[#2A1F1B]">{error}</p>
            <Button onClick={() => window.location.reload()} className="mt-4 bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B]">
              Tentar novamente
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#2A1F1B]">Gerenciamento de Temas</h1>
          <p className="text-[#8B7355]/70">
            Crie e gerencie temas para personalizar a aparência do sistema
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-3">
              <Label htmlFor="tenant-select" className="text-sm text-[#2A1F1B]">Tenant:</Label>
              <select
                id="tenant-select"
                value={tenantId}
                onChange={(e) => setTenantId(e.target.value)}
                className="rounded-md border border-[#8B7355]/30 bg-white px-3 py-2 text-[#2A1F1B] focus:outline-none focus:ring-2 focus:ring-[#D7B899] focus:border-transparent"
              >
                <option value="baronesa">Empório A Baronesa</option>
              </select>
            </div>
            <div className="text-sm text-[#8B7355]/70">
              Tema ativo:
              <span className="ml-1 font-semibold text-[#2A1F1B]">{activeTheme?.name || 'Nenhum tema ativo'}</span>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-medium uppercase tracking-widest text-[#8B7355]/70">Switch</span>
              <select
                value={typeof selectedSwitcherThemeId === 'number' ? selectedSwitcherThemeId : ''}
                onChange={(e) => {
                  setSelectedSwitcherThemeId(e.target.value === '' ? '' : Number(e.target.value));
                }}
                className="rounded-md border border-[#8B7355]/30 bg-white px-3 py-1 text-[#2A1F1B] text-xs focus:outline-none focus:ring-2 focus:ring-[#D7B899] focus:border-transparent"
              >
                <option value="">Selecione um tema</option>
                {temas.map((theme) => (
                  <option
                    key={theme.id}
                    value={theme.id}
                    disabled={theme.status !== 'PUBLISHED'}
                  >
                    {theme.name} {theme.status !== 'PUBLISHED' ? `(${getStatusLabel(theme.status)})` : ''}
                  </option>
                ))}
              </select>
              <Button
                size="sm"
                variant="outline"
                onClick={handleSwitcherApply}
                disabled={!canApplySwitcher}
                className="border-[#8B7355]/40 text-[#2A1F1B] hover:bg-[#D7B899]/10 hover:text-[#D7B899]"
              >
                Aplicar
              </Button>
            </div>
          </div>
        </div>
        <Button onClick={handleCreateNew} className="bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B]">
          <Plus className="h-4 w-4 mr-2" />
          Novo Tema
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#D7B899]"></div>
        </div>
      ) : (
        <Card className="bg-white border-[#8B7355]/30">
          <CardHeader>
            <CardTitle className="text-[#2A1F1B]">Temas Disponíveis</CardTitle>
          </CardHeader>
          <CardContent>
            {temas.length === 0 ? (
              <div className="text-center py-10">
                <Settings className="h-12 w-12 mx-auto text-[#8B7355]/70" />
                <h3 className="mt-4 font-semibold text-[#2A1F1B]">Nenhum tema encontrado</h3>
                <p className="text-[#8B7355]/70 mt-2">
                  Crie seu primeiro tema para começar a personalizar o sistema
                </p>
                <Button onClick={handleCreateNew} className="mt-4 bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B]">
                  <Plus className="h-4 w-4 mr-2" />
                  Criar Primeiro Tema
                </Button>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="border-[#8B7355]/30">
                    <TableHead className="text-[#2A1F1B]">Nome</TableHead>
                    <TableHead className="text-[#2A1F1B]">Status</TableHead>
                    <TableHead className="text-[#2A1F1B]">Tenant</TableHead>
                    <TableHead className="text-[#2A1F1B]">Criado em</TableHead>
                    <TableHead className="text-[#2A1F1B]">Atualizado em</TableHead>
                    <TableHead className="text-right text-[#2A1F1B]">Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {temas.map((theme) => (
                    <TableRow key={theme.id} className="border-[#8B7355]/30">
                      <TableCell className="font-medium text-[#2A1F1B]">{theme.name}</TableCell>
                      <TableCell>
                        <span className={`px-2 py-1 rounded-full text-xs ${getStatusColor(theme.status)}`}>
                          {getStatusLabel(theme.status)}
                        </span>
                      </TableCell>
                      <TableCell className="text-[#2A1F1B]">{theme.tenantId}</TableCell>
                      <TableCell className="text-[#2A1F1B]">{new Date(theme.createdAt).toLocaleDateString()}</TableCell>
                      <TableCell className="text-[#2A1F1B]">{new Date(theme.updatedAt).toLocaleDateString()}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          {theme.status === 'PUBLISHED' && activeThemeId !== theme.id && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleSetActive(theme.id)}
                              title="Definir como ativo"
                              className="border-green-600/40 text-green-600 hover:bg-green-600/10 font-medium"
                            >
                              <Eye className="h-4 w-4" />
                            </Button>
                          )}
                          {activeThemeId === theme.id && (
                            <Button
                              variant="outline"
                              size="sm"
                              disabled
                              title="Tema ativo"
                              className="border-green-600/40 text-green-600 bg-green-600/10 font-medium"
                            >
                              <Check className="h-4 w-4" />
                            </Button>
                          )}
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleEdit(theme)}
                            title="Editar tema"
                            className="border-[#8B7355]/40 text-[#2A1F1B] hover:bg-[#D7B899]/10 hover:text-[#D7B899]"
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleDuplicate(theme.id)}
                            title="Duplicar tema"
                            className="border-[#8B7355]/40 text-[#2A1F1B] hover:bg-[#D7B899]/10 hover:text-[#D7B899]"
                          >
                            <Copy className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleDelete(theme.id)}
                            title="Excluir tema"
                            className="border-[#8B7355]/40 text-[#2A1F1B] hover:bg-red-500/10 hover:text-red-600"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}

      {/* Modal de Edição/Criação de Tema */}
      {isModalOpen && (
        <EditThemeModal
          theme={editingTheme}
          onClose={() => setIsModalOpen(false)}
          onSave={handleSave}
        />
      )}

      {/* Overlay de Redeploy */}
      {isRedeploying && (
        <div className="fixed inset-0 bg-black/50 z-[10000] flex items-center justify-center backdrop-blur-sm">
          <Card className="w-full max-w-md bg-white border-[#D7B899] shadow-xl">
            <CardHeader>
              <CardTitle className="text-xl text-[#2A1F1B] flex items-center gap-2">
                <Settings className="h-6 w-6 animate-spin text-[#D7B899]" />
                Reiniciando Sistema
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-[#2A1F1B]/80 text-center">
                Aplicando alterações de SEO e Favicon...
              </p>
              <div className="w-full bg-gray-200 rounded-full h-2.5 dark:bg-gray-700 overflow-hidden">
                <div className="bg-[#D7B899] h-2.5 rounded-full animate-progress-indeterminate"></div>
              </div>
              <p className="text-sm text-center font-mono text-muted-foreground bg-muted p-2 rounded">
                Status: {redeployStatus}
              </p>
              <p className="text-xs text-center text-muted-foreground">
                O sistema ficará indisponível por alguns segundos. <br/>
                A página será recarregada automaticamente.
              </p>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

// Componente do modal de edição
const EditThemeModal = ({ theme, onClose, onSave }: {
  theme: Theme | null;
  onClose: () => void;
  onSave: (theme: Theme, restart?: boolean) => void
}) => {
  const [name, setName] = useState(theme?.name || '');
  const [status, setStatus] = useState(theme?.status || 'DRAFT');
  const [tenantId, setTenantId] = useState(theme?.tenantId || 'baronesa');
  const [tokensStr, setTokensStr] = useState(JSON.stringify(theme?.tokens || {}, null, 2));
  const [tokens, setTokens] = useState(theme?.tokens || {});
  const [assetsStr, setAssetsStr] = useState(JSON.stringify(theme?.assets || {}, null, 2));
  const [assets, setAssets] = useState(theme?.assets || {});
  const [contentStr, setContentStr] = useState(JSON.stringify(theme?.content || {}, null, 2));
  const [content, setContent] = useState(theme?.content || {});
  const [activeTab, setActiveTab] = useState('general');
  
  const [showRestartConfirm, setShowRestartConfirm] = useState(false);
  const [pendingTheme, setPendingTheme] = useState<Theme | null>(null);

  const checkCriticalChanges = (original: Theme | null, updated: Theme) => {
    if (!original) return false; // Novo tema não precisa de restart (ou talvez sim se for logo ativo?) -> Geralmente criação não ativa imediatamente.
    
    // Check Favicon
    if (original.assets?.faviconUrl !== updated.assets?.faviconUrl) return true;

    // Campos críticos de SEO
    const criticalContentFields = [
      'seoTitle', 'seoDescription', 'seoAuthor', 'seoUrl', 'seoSiteName', 
      'seoLocale', 'seoThemeColor', 'seoRobots'
    ];
    
    const originalContent = original.content || {};
    const updatedContent = updated.content || {};
    
    for (const field of criticalContentFields) {
        if (originalContent[field] !== updatedContent[field]) return true;
    }
    
    // Campos OG e Twitter
    const allKeys = new Set([...Object.keys(originalContent), ...Object.keys(updatedContent)]);
    for (const key of allKeys) {
        if ((key.startsWith('seoOg') || key.startsWith('seoTwitter')) && originalContent[key] !== updatedContent[key]) return true;
    }
    
    return false;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    try {
      const tokensParsed = JSON.parse(tokensStr);
      const assetsParsed = JSON.parse(assetsStr);
      const contentParsed = JSON.parse(contentStr);

      const updatedTheme = {
        ...theme,
        name,
        status,
        tenantId,
        tokens: tokensParsed,
        assets: assetsParsed,
        content: contentParsed
      } as Theme;

      if (checkCriticalChanges(theme, updatedTheme)) {
        setPendingTheme(updatedTheme);
        setShowRestartConfirm(true);
      } else {
        onSave(updatedTheme, false);
      }
    } catch (error) {
      alert('Erro ao processar JSON: ' + (error instanceof Error ? error.message : 'Formato inválido'));
    }
  };

  const handleConfirmRestart = (restart: boolean) => {
    if (pendingTheme) {
      onSave(pendingTheme, restart);
      setShowRestartConfirm(false);
    }
  };

  return (
    <>
      <Dialog open={!!theme} onOpenChange={(open) => !open && onClose()}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto bg-white border-[#8B7355]/30" style={{ zIndex: 9998 }}>
          <DialogHeader>
            <DialogTitle className="text-2xl font-display tracking-wider text-[#2A1F1B]">
              {theme?.id ? 'Editar Tema' : 'Criar Novo Tema'}
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-6 mt-4">
            <ThemeFormTabs activeTab={activeTab} setActiveTab={setActiveTab}>
              <ThemeFormTab id="general" label="Geral" activeTab={activeTab}>
                <ThemeGeneralForm
                  theme={theme}
                  name={name}
                  setName={setName}
                  status={status}
                  setStatus={setStatus}
                  tenantId={tenantId}
                  setTenantId={setTenantId}
                />
              </ThemeFormTab>

              <ThemeFormTab id="tokens" label="Cores e Estilos" activeTab={activeTab}>
                <ThemeTokensForm
                  tokens={tokens}
                  setTokens={setTokens}
                  tokensStr={tokensStr}
                  setTokensStr={setTokensStr}
                />
              </ThemeFormTab>

              <ThemeFormTab id="assets" label="Imagens e Arquivos" activeTab={activeTab}>
                <ThemeAssetsForm
                  assets={assets}
                  setAssets={setAssets}
                  assetsStr={assetsStr}
                  setAssetsStr={setAssetsStr}
                  themeId={theme?.id}
                  content={content}
                  setContent={setContent}
                  contentStr={contentStr}
                  setContentStr={setContentStr}
                />
              </ThemeFormTab>

              <ThemeFormTab id="content" label="Conteúdo" activeTab={activeTab}>
                <ThemeContentForm
                  content={content}
                  setContent={setContent}
                  contentStr={contentStr}
                  setContentStr={setContentStr}
                />
              </ThemeFormTab>

              <ThemeFormTab id="seo" label="SEO e Social" activeTab={activeTab}>
                <ThemeSeoForm
                  content={content}
                  setContent={setContent}
                  contentStr={contentStr}
                  setContentStr={setContentStr}
                />
              </ThemeFormTab>

              <ThemeFormTab id="android" label="Android" activeTab={activeTab}>
                <ThemeAndroidForm
                  assets={assets}
                  setAssets={setAssets}
                  assetsStr={assetsStr}
                  setAssetsStr={setAssetsStr}
                  content={content}
                  setContent={setContent}
                  contentStr={contentStr}
                  setContentStr={setContentStr}
                />
              </ThemeFormTab>
            </ThemeFormTabs>

            <div className="flex justify-end gap-3 pt-4 border-t border-[#8B7355]/20">
              <Button
                type="button"
                variant="outline"
                onClick={onClose}
                className="bg-white border-[#8B7355]/40 text-[#2A1F1B] hover:bg-[#D7B899]/10 font-medium"
              >
                Cancelar
              </Button>
              <Button
                type="submit"
                className="bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B] font-medium border-none"
              >
                {theme?.id ? 'Atualizar' : 'Criar'} Tema
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Dialog de Confirmação de Restart */}
      <Dialog open={showRestartConfirm} onOpenChange={setShowRestartConfirm}>
        <DialogContent className="bg-white border-[#D7B899] sm:max-w-md" style={{ zIndex: 9999 }}>
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B] flex items-center gap-2">
              <Settings className="h-5 w-5 text-orange-500" />
              Reinicialização Necessária
            </DialogTitle>
          </DialogHeader>
          <div className="py-4 space-y-3 text-[#2A1F1B]/80">
            <p>
              Você alterou configurações críticas (SEO/Favicon) que requerem a reinicialização do sistema para serem aplicadas publicamente.
            </p>
            <p className="text-sm bg-orange-50 p-3 rounded border border-orange-200">
              <strong>Atenção:</strong> O site ficará indisponível por cerca de 15 segundos durante o processo.
            </p>
          </div>
          <div className="flex flex-col sm:flex-row justify-end gap-2 mt-2">
            <Button
              variant="outline"
              onClick={() => handleConfirmRestart(false)}
              className="text-[#2A1F1B] border-[#8B7355]/30 hover:bg-[#8B7355]/10"
            >
              Salvar sem reiniciar
            </Button>
            <Button
              onClick={() => handleConfirmRestart(true)}
              className="bg-[#D7B899] hover:bg-[#D7B899]/90 text-[#2A1F1B]"
            >
              Salvar e Reiniciar Agora
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
};
