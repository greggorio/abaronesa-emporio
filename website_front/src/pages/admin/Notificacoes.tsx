import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Bell, Send, Clock, Users } from 'lucide-react';
import { notificationService, NotificationHistory } from '@/services/notificationService';

const Notificacoes = () => {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [history, setHistory] = useState<NotificationHistory[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(true);

  useEffect(() => {
    loadHistory();
  }, []);

  const loadHistory = async () => {
    try {
      setLoadingHistory(true);
      const data = await notificationService.getHistory();
      setHistory(data);
    } catch (err: any) {
      console.error('Erro ao carregar historico:', err);
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleSendNotification = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!title.trim() || !body.trim()) {
      setError('Titulo e mensagem sao obrigatorios');
      return;
    }

    setIsLoading(true);
    try {
      const result = await notificationService.sendNotification({
        title,
        body,
        imageUrl: imageUrl || undefined
      });

      setSuccess(`Notificacao enviada com sucesso para ${result.recipientsCount} dispositivo(s)!`);
      setTitle('');
      setBody('');
      setImageUrl('');

      loadHistory();
    } catch (err: any) {
      setError(err.message || 'Erro ao enviar notificacao');
    } finally {
      setIsLoading(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-display text-forest-dark mb-2">Notificacoes</h1>
        <p className="text-sm text-forest-green/70">Envie notificacoes para usuarios do aplicativo</p>
      </div>

      <Card className="border-coral-accent/20 bg-white shadow-sm">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Send className="w-5 h-5 text-coral-accent" />
            <CardTitle className="text-forest-dark font-display">Enviar Notificacao</CardTitle>
          </div>
          <CardDescription className="text-forest-green/70">
            Preencha os campos abaixo para enviar uma notificacao push
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSendNotification} className="space-y-4">
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {success && (
              <Alert className="border-green-500 bg-green-50 text-green-800">
                <AlertDescription>{success}</AlertDescription>
              </Alert>
            )}

            <div className="space-y-2">
              <Label htmlFor="title" className="text-forest-dark font-semibold">
                Titulo
              </Label>
              <Input
                id="title"
                type="text"
                placeholder="Ex: Nova Promocao!"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="border-forest-green/20 focus:border-coral-accent focus:ring-coral-accent"
                required
                disabled={isLoading}
                maxLength={100}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="body" className="text-forest-dark font-semibold">
                Mensagem
              </Label>
              <Textarea
                id="body"
                placeholder="Ex: Aproveite 20% de desconto em todos os servicos nesta semana!"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                className="border-forest-green/20 focus:border-coral-accent focus:ring-coral-accent min-h-[100px]"
                required
                disabled={isLoading}
                maxLength={500}
              />
              <p className="text-xs text-forest-green/60">{body.length}/500 caracteres</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="imageUrl" className="text-forest-dark font-semibold">
                URL da Imagem (opcional)
              </Label>
              <Input
                id="imageUrl"
                type="url"
                placeholder="https://exemplo.com/imagem.jpg"
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                className="border-forest-green/20 focus:border-coral-accent focus:ring-coral-accent"
                disabled={isLoading}
              />
            </div>

            <Button
              type="submit"
              className="w-full bg-coral-accent hover:bg-coral-accent/90 text-forest-dark font-semibold"
              disabled={isLoading}
            >
              <Bell className="w-4 h-4 mr-2" />
              {isLoading ? 'Enviando...' : 'Enviar Notificacao'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="border-coral-accent/20 bg-white shadow-sm">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Clock className="w-5 h-5 text-coral-accent" />
            <CardTitle className="text-forest-dark font-display">Historico de Envios</CardTitle>
          </div>
          <CardDescription className="text-forest-green/70">
            Ultimas notificacoes enviadas
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loadingHistory ? (
            <div className="text-center py-8 text-forest-green/60">
              Carregando historico...
            </div>
          ) : history.length === 0 ? (
            <div className="text-center py-8 text-forest-green/60">
              Nenhuma notificacao enviada ainda
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-forest-dark">Data/Hora</TableHead>
                    <TableHead className="text-forest-dark">Titulo</TableHead>
                    <TableHead className="text-forest-dark">Mensagem</TableHead>
                    <TableHead className="text-forest-dark text-center">
                      <Users className="w-4 h-4 inline mr-1" />
                      Destinatarios
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {history.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="text-forest-green/70 text-sm whitespace-nowrap">
                        {formatDate(item.sentAt)}
                      </TableCell>
                      <TableCell className="text-forest-dark font-medium">
                        {item.title}
                      </TableCell>
                      <TableCell className="text-forest-green/70 max-w-md truncate">
                        {item.body}
                      </TableCell>
                      <TableCell className="text-center text-forest-dark font-semibold">
                        {item.recipientsCount}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default Notificacoes;
