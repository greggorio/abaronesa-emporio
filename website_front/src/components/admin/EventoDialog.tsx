import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import DatePicker, { registerLocale } from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import ptBR from 'date-fns/locale/pt-BR';
import { format, parseISO } from 'date-fns';
import type { Evento } from '@/pages/admin/EventosAdmin';

registerLocale('pt-BR', ptBR);

interface EventoDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  evento: Evento | null;
  onSave: (evento: Partial<Evento>) => Promise<void>;
  loading: boolean;
}

const GENEROS = [
  { value: 'ROCK', label: 'Rock' },
  { value: 'METAL', label: 'Metal' },
  { value: 'ACUSTICO', label: 'Acústico' },
  { value: 'SERTANEJO', label: 'Sertanejo' },
  { value: 'MPB', label: 'MPB' },
  { value: 'BLUES', label: 'Blues' },
  { value: 'JAZZ', label: 'Jazz' },
  { value: 'OUTRO', label: 'Outro' },
];

const STATUS = [
  { value: 'AGENDADO', label: 'Agendado' },
  { value: 'REALIZADO', label: 'Realizado' },
  { value: 'CANCELADO', label: 'Cancelado' },
];

export default function EventoDialog({
  open,
  onOpenChange,
  evento,
  onSave,
  loading,
}: EventoDialogProps) {
  const [formData, setFormData] = useState({
    titulo: '',
    descricao: '',
    dataEvento: null as Date | null,
    dataHoraFim: null as Date | null,
    preco: undefined as number | undefined,
    gratuito: false,
    banda: '',
    genero: 'ROCK',
    generoDescricao: 'Rock',
    imagemUrl: '',
    ativo: true,
    status: 'AGENDADO',
    statusDescricao: 'Agendado',
  });

  useEffect(() => {
    if (evento) {
      const dataInicio = evento.dataEvento ? parseISO(evento.dataEvento) : null;
      const dataFim = evento.dataHoraFim ? parseISO(evento.dataHoraFim) : null;
      setFormData({
        titulo: evento.titulo,
        descricao: evento.descricao,
        dataEvento: dataInicio,
        dataHoraFim: dataFim,
        preco: evento.preco,
        gratuito: evento.gratuito,
        banda: evento.banda,
        genero: evento.genero,
        generoDescricao: evento.generoDescricao,
        imagemUrl: evento.imagemUrl || '',
        ativo: evento.ativo,
        status: evento.status,
        statusDescricao: evento.statusDescricao,
      });
    } else {
      // Reset form
      setFormData({
        titulo: '',
        descricao: '',
        dataEvento: null,
        dataHoraFim: null,
        preco: undefined,
        gratuito: false,
        banda: '',
        genero: 'ROCK',
        generoDescricao: 'Rock',
        imagemUrl: '',
        ativo: true,
        status: 'AGENDADO',
        statusDescricao: 'Agendado',
      });
    }
  }, [evento, open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validações
    if (!formData.titulo || !formData.banda || !formData.dataEvento) {
      alert('Por favor, preencha todos os campos obrigatórios');
      return;
    }

    // Validar preço se o evento não for gratuito
    if (!formData.gratuito && (formData.preco === undefined || formData.preco < 0)) {
      alert('Por favor, informe um preço válido para eventos pagos');
      return;
    }

    // Converter datas de volta para ISO
    const dataISO = format(formData.dataEvento, "yyyy-MM-dd'T'HH:mm:00");
    const dataHoraFimISO = formData.dataHoraFim
      ? format(formData.dataHoraFim, "yyyy-MM-dd'T'HH:mm:00")
      : undefined;

    await onSave({
      ...formData,
      dataEvento: dataISO,
      dataHoraFim: dataHoraFimISO,
    });
  };

  const handleGeneroChange = (value: string) => {
    const genero = GENEROS.find((g) => g.value === value);
    setFormData({
      ...formData,
      genero: value,
      generoDescricao: genero?.label || value,
    });
  };

  const handleStatusChange = (value: string) => {
    const status = STATUS.find((s) => s.value === value);
    setFormData({
      ...formData,
      status: value,
      statusDescricao: status?.label || value,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto bg-card border-border/30">
        <DialogHeader>
          <DialogTitle className="text-2xl font-display tracking-wider text-foreground flex items-center justify-between">
            {evento ? 'Editar Evento' : 'Novo Evento'}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onOpenChange(false)}
              className="h-8 w-8 p-0 text-foreground hover:text-primary hover:bg-primary/10"
            >
              <X className="h-4 w-4" />
            </Button>
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-6 mt-4">
          {/* Título */}
          <div className="space-y-2">
            <Label htmlFor="titulo" className="text-sm font-medium text-foreground">
              Título *
            </Label>
            <Input
              id="titulo"
              value={formData.titulo}
              onChange={(e) => setFormData({ ...formData, titulo: e.target.value })}
              placeholder="Nome do evento"
              required
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>

          {/* Descrição */}
          <div className="space-y-2">
            <Label htmlFor="descricao" className="text-sm font-medium text-foreground">
              Descrição
            </Label>
            <Textarea
              id="descricao"
              value={formData.descricao}
              onChange={(e) => setFormData({ ...formData, descricao: e.target.value })}
              placeholder="Descrição detalhada do evento"
              rows={3}
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent resize-none bg-input text-foreground"
            />
          </div>

          {/* Banda */}
          <div className="space-y-2">
            <Label htmlFor="banda" className="text-sm font-medium text-foreground">
              Banda/Artista *
            </Label>
            <Input
              id="banda"
              value={formData.banda}
              onChange={(e) => setFormData({ ...formData, banda: e.target.value })}
              placeholder="Nome da banda ou artista"
              required
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>

          {/* Data e Hora */}
          <div className="space-y-2">
            <Label htmlFor="dataEvento" className="text-sm font-medium text-foreground">
              Início: 
            </Label>
            <DatePicker
              id="dataEvento"
              selected={formData.dataEvento}
              onChange={(date) => setFormData({ ...formData, dataEvento: date })}
              showTimeSelect
              timeFormat="HH:mm"
              timeIntervals={15}
              timeCaption="Hora"
              dateFormat="dd/MM/yyyy HH:mm"
              locale="pt-BR"
              placeholderText="dd/mm/aaaa hh:mm"
              required
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>

          {/* Data e Hora Fim */}
          <div className="space-y-2">
            <Label htmlFor="dataHoraFim" className="text-sm font-medium text-foreground">
              Fim: 
            </Label>
            <DatePicker
              id="dataHoraFim"
              selected={formData.dataHoraFim}
              onChange={(date) => setFormData({ ...formData, dataHoraFim: date })}
              showTimeSelect
              timeFormat="HH:mm"
              timeIntervals={15}
              timeCaption="Hora"
              dateFormat="dd/MM/yyyy HH:mm"
              locale="pt-BR"
              placeholderText="dd/mm/aaaa hh:mm"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>

          {/* Grid com 2 colunas */}
          <div className="grid grid-cols-2 gap-4">
            {/* Gênero */}
            <div className="space-y-2">
              <Label htmlFor="genero" className="text-sm font-medium text-foreground">
                Gênero *
              </Label>
              <Select value={formData.genero} onValueChange={handleGeneroChange}>
                <SelectTrigger className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="bg-popover border-border/30">
                  {GENEROS.map((genero) => (
                    <SelectItem key={genero.value} value={genero.value} className="text-popover-foreground">
                      {genero.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Status */}
            <div className="space-y-2">
              <Label htmlFor="status" className="text-sm font-medium text-foreground">
                Status *
              </Label>
              <Select value={formData.status} onValueChange={handleStatusChange}>
                <SelectTrigger className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="bg-popover border-border/30">
                  {STATUS.map((status) => (
                    <SelectItem key={status.value} value={status.value} className="text-popover-foreground">
                      {status.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Grid para preço e gratuito */}
          <div className="grid grid-cols-2 gap-4">
            {/* Gratuito */}
            <div className="space-y-2">
              <Label htmlFor="gratuito" className="text-sm font-medium text-foreground">
                Gratuito
              </Label>
              <div className="flex items-center gap-2">
                <input
                  id="gratuito"
                  type="checkbox"
                  checked={formData.gratuito}
                  onChange={(e) => setFormData({ ...formData, gratuito: e.target.checked })}
                  className="h-4 w-4 rounded border-border/30 text-primary focus:ring-primary"
                />
                <span className="text-sm text-foreground">Evento gratuito</span>
              </div>
            </div>

            {/* Preço */}
            <div className="space-y-2">
              <Label htmlFor="preco" className="text-sm font-medium text-foreground">
                Preço (R$)
              </Label>
              <Input
                id="preco"
                type="number"
                step="0.01"
                min="0"
                value={formData.preco || ''}
                onChange={(e) => setFormData({ ...formData, preco: e.target.value ? parseFloat(e.target.value) : undefined })}
                disabled={formData.gratuito}
                placeholder="0.00"
                className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
              />
            </div>
          </div>

          {/* URL da Imagem */}
          <div className="space-y-2">
            <Label htmlFor="imagemUrl" className="text-sm font-medium text-foreground">
              URL da Imagem
            </Label>
            <Input
              id="imagemUrl"
              value={formData.imagemUrl}
              onChange={(e) => setFormData({ ...formData, imagemUrl: e.target.value })}
              placeholder="https://exemplo.com/imagem.jpg"
              type="url"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>

          {/* Botões de ação */}
          <div className="flex justify-end gap-3 pt-4 border-t border-border/20">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={loading}
              className="bg-secondary border-border/40 text-secondary-foreground hover:bg-primary/10 font-medium"
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              className="bg-primary hover:bg-primary/90 text-primary-foreground font-medium border-none"
              disabled={loading}
            >
              {loading ? 'Salvando...' : evento ? 'Atualizar' : 'Criar Evento'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
