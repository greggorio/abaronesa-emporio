import { useState, useEffect } from 'react';
import { Calendar, Plus, Pencil, Trash2, Eye, Search, Filter, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import EventoDialog from '@/components/admin/EventoDialog';
import ConfirmDialog from '@/components/admin/ConfirmDialog';
import { eventoService, EventoResponseDTO, EventoDTO } from '@/services/eventoService';
import { useToast } from '@/components/ui/use-toast';
import axios from 'axios';

export interface Evento {
  id: number;
  titulo: string;
  descricao: string;
  dataEvento: string;
  dataHoraFim?: string;
  preco?: number;
  gratuito: boolean;
  banda: string;
  genero: string;
  generoDescricao: string;
  imagemUrl?: string;
  ativo: boolean;
  status: string;
  statusDescricao: string;
  criadoEm: string;
  atualizadoEm: string;
}

export default function EventosAdmin() {
  const { toast } = useToast();
  const [eventos, setEventos] = useState<Evento[]>([]);
  const [selectedEvento, setSelectedEvento] = useState<Evento | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [eventoToDelete, setEventoToDelete] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const itemsPerPage = 10;

  // Carregar eventos da API
  const loadEventos = async () => {
    try {
      setLoading(true);
      const response = await eventoService.listarComPaginacao(
        currentPage,
        itemsPerPage,
        searchTerm || undefined
      );

      setEventos(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
    } catch (error) {
      console.error('Erro ao carregar eventos:', error);
      toast({
        variant: 'destructive',
        title: 'Erro ao carregar eventos',
        description: 'Verifique sua conexão e autenticação.',
      });
    } finally {
      setLoading(false);
    }
  };

  // Carregar eventos ao montar e quando dependências mudarem
  useEffect(() => {
    loadEventos();
  }, [currentPage, searchTerm]);

  const handleCreate = () => {
    setSelectedEvento(null);
    setDialogOpen(true);
  };

  const handleEdit = (evento: Evento) => {
    setSelectedEvento(evento);
    setDialogOpen(true);
  };

  const handleDeleteClick = (id: number) => {
    setEventoToDelete(id);
    setConfirmOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!eventoToDelete) return;

    try {
      setLoading(true);
      await eventoService.excluir(eventoToDelete);
      toast({
        title: 'Evento excluído',
        description: 'O evento foi removido com sucesso.',
      });
      await loadEventos();
    } catch (error) {
      console.error('Erro ao excluir evento:', error);
      toast({
        variant: 'destructive',
        title: 'Erro ao excluir',
        description: 'Verifique sua autenticação e permissões.',
      });
    } finally {
      setLoading(false);
      setEventoToDelete(null);
    }
  };

  const handleSave = async (evento: Partial<Evento>) => {
    try {
      setLoading(true);
      const dto: EventoDTO = {
        titulo: evento.titulo!,
        descricao: evento.descricao!,
        dataEvento: evento.dataEvento!,
        dataHoraFim: evento.dataHoraFim,
        preco: evento.preco,
        gratuito: evento.gratuito!,
        banda: evento.banda!,
        genero: evento.genero as any,
        imagemUrl: evento.imagemUrl,
        ativo: evento.ativo,
        status: evento.status as any,
      };

      if (selectedEvento) {
        await eventoService.atualizar(selectedEvento.id, dto);
        toast({
          title: 'Evento atualizado',
          description: 'As alterações foram salvas com sucesso.',
        });
      } else {
        await eventoService.criar(dto);
        toast({
          title: 'Evento criado',
          description: 'O novo evento foi cadastrado com sucesso.',
        });
      }

      setDialogOpen(false);
      await loadEventos();
    } catch (error) {
      console.error('Erro ao salvar evento:', error);

      let title = 'Erro ao salvar';
      let description = 'Verifique os dados e sua autenticação.';

      if (axios.isAxiosError(error)) {
        const status = error.response?.status;
        const rawData = error.response?.data as any;
        const apiMessage =
          (rawData && typeof rawData === 'object' ? rawData.message || rawData.error : undefined) ||
          (typeof rawData === 'string' ? rawData : undefined);

        if (status === 409) {
          title = 'Conflito de horário';
          description = apiMessage || 'Já existe um evento nesse intervalo de data/hora.';
        } else if (status === 400 && apiMessage) {
          description = apiMessage;
        } else if (apiMessage) {
          description = apiMessage;
        }
      }

      toast({
        variant: 'destructive',
        title,
        description,
      });
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadgeClass = (status: string) => {
    switch (status) {
      case 'AGENDADO':
        return 'bg-[#8B7355]/20 text-[#8B7355] border-[#8B7355]/40';
      case 'REALIZADO':
        return 'bg-blue-500/20 text-blue-600 border-blue-500/40';
      case 'CANCELADO':
        return 'bg-red-500/20 text-red-600 border-red-500/40';
      default:
        return 'bg-[#D7B899]/20 text-[#D7B899] border-[#D7B899]/40';
    }
  };

  const formatDate = (startString: string, endString?: string) => {
    const start = new Date(startString);
    const end = endString ? new Date(endString) : null;

    const pad = (value: number) => String(value).padStart(2, '0');
    const formatHour = (date: Date) => {
      const minutes = date.getMinutes();
      const hours = pad(date.getHours());
      return minutes === 0 ? `${hours}hrs` : `${hours}:${pad(minutes)}hrs`;
    };

    const datePart = `${pad(start.getDate())}/${pad(start.getMonth() + 1)}`;
    const startPart = formatHour(start);
    const endPart = end ? formatHour(end) : null;

    return endPart ? `${datePart} ${startPart} às ${endPart}` : `${datePart} ${startPart}`;
  };

  return (
    <div className="min-h-screen bg-[#FAFAFA] p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header com botões de ação */}
        <div className="mb-6">
          <h1 className="text-4xl font-display text-[#2A1F1B] mb-6">
            Gerenciar Eventos
          </h1>

          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <Button onClick={handleCreate} className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 font-medium">
              <Plus className="w-4 h-4 mr-2" />
              Novo Evento
            </Button>

            <div className="flex items-center gap-3 w-full sm:w-auto">
              <div className="relative flex-1 sm:flex-initial">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-[#8B7355]/40" size={16} />
                <input
                  type="text"
                  placeholder="Buscar eventos..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value);
                    setCurrentPage(0);
                  }}
                  className="w-full sm:w-64 pl-10 pr-4 py-2 bg-white border border-[#D7B899]/30 rounded-lg focus:ring-2 focus:ring-[#D7B899] focus:border-transparent outline-none transition text-[#2A1F1B] placeholder:text-[#8B7355]/40"
                />
              </div>
              <Button variant="ghost" size="sm" className="bg-white border border-[#D7B899]/30 text-[#2A1F1B] hover:bg-[#D7B899]/10">
                <Filter size={16} />
              </Button>
            </div>
          </div>
        </div>

        {/* Tabela de eventos */}
        <div className="bg-white rounded-2xl shadow-xl border border-[#D7B899]/20 overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5">
                <TableHead className="w-[50px] text-[#2A1F1B] font-medium">ID</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Título</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Banda</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Gênero</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Data/Hora</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Gratuito</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Status</TableHead>
                <TableHead className="text-right text-[#2A1F1B] font-medium">Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-10">
                    <div className="flex flex-col items-center gap-2">
                      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#D7B899]"></div>
                      <p className="text-sm text-[#8B7355]/70">Carregando eventos...</p>
                    </div>
                  </TableCell>
                </TableRow>
              ) : eventos.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-10">
                    <div className="flex flex-col items-center gap-2">
                      <Calendar className="w-12 h-12 text-[#D7B899]/30" />
                      <p className="text-lg font-semibold text-[#2A1F1B]">
                        {searchTerm ? 'Nenhum evento encontrado' : 'Nenhum evento cadastrado'}
                      </p>
                      <p className="text-sm text-[#8B7355]/70">
                        {searchTerm ? 'Tente ajustar sua busca' : 'Clique em "Novo Evento" para começar'}
                      </p>
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                eventos.map((evento) => (
                  <TableRow
                    key={evento.id}
                    className="hover:bg-[#D7B899]/5 cursor-pointer transition-colors border-b border-[#D7B899]/10"
                    onDoubleClick={() => handleEdit(evento)}
                  >
                    <TableCell className="font-medium">
                      <Badge className="bg-[#D7B899]/20 text-[#D7B899] border-[#D7B899]/40 font-mono">
                        {String(evento.id).padStart(6, '0')}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div>
                        <div className="font-semibold text-[#2A1F1B]">{evento.titulo}</div>
                        <div className="text-sm text-[#8B7355]/70 truncate max-w-xs">
                          {evento.descricao}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-[#2A1F1B]">{evento.banda}</TableCell>
                    <TableCell>
                      <Badge className="bg-[#8B7355]/15 text-[#8B7355] border-[#8B7355]/30">
                        {evento.generoDescricao}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-[#2A1F1B]">{formatDate(evento.dataEvento, evento.dataHoraFim)}</TableCell>
                    <TableCell>
                      {evento.gratuito ? (
                        <Badge className="bg-green-100 text-green-800 border-green-300">Sim</Badge>
                      ) : (
                        <Badge className="bg-red-100 text-red-800 border-red-300">Não</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge className={getStatusBadgeClass(evento.status)}>
                        {evento.statusDescricao}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleEdit(evento);
                          }}
                          title="Ver detalhes"
                          className="hover:bg-[#8B7355]/10"
                        >
                          <Eye className="w-4 h-4 text-[#8B7355]" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleEdit(evento);
                          }}
                          title="Editar"
                          className="hover:bg-[#D7B899]/10"
                        >
                          <Pencil className="w-4 h-4 text-[#D7B899]" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteClick(evento.id);
                          }}
                          title="Excluir"
                          className="hover:bg-red-500/10"
                        >
                          <Trash2 className="w-4 h-4 text-[#D65A31]" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          {/* Rodapé com paginação */}
          {!loading && eventos.length > 0 && (
            <div className="flex items-center justify-between px-6 py-4 border-t border-[#D7B899]/20 bg-[#D7B899]/5">
              <div className="text-sm text-[#8B7355]/70">
                Página {currentPage + 1} de {totalPages} ({totalElements} {totalElements === 1 ? 'item' : 'itens'})
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setCurrentPage(0)}
                  disabled={currentPage === 0}
                  className="text-[#2A1F1B] hover:bg-[#D7B899]/10 disabled:opacity-50"
                >
                  <ChevronsLeft size={16} />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                  disabled={currentPage === 0}
                  className="text-[#2A1F1B] hover:bg-[#D7B899]/10 disabled:opacity-50"
                >
                  <ChevronLeft size={16} />
                </Button>
                <span className="px-3 py-1 text-sm font-medium text-[#2A1F1B]">{currentPage + 1}</span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
                  disabled={currentPage === totalPages - 1}
                  className="text-[#2A1F1B] hover:bg-[#D7B899]/10 disabled:opacity-50"
                >
                  <ChevronRight size={16} />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setCurrentPage(totalPages - 1)}
                  disabled={currentPage === totalPages - 1}
                  className="text-[#2A1F1B] hover:bg-[#D7B899]/10 disabled:opacity-50"
                >
                  <ChevronsRight size={16} />
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Dialog de formulário */}
        <EventoDialog
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          evento={selectedEvento}
          onSave={handleSave}
          loading={loading}
        />

        {/* Dialog de confirmação de exclusão */}
        <ConfirmDialog
          open={confirmOpen}
          onOpenChange={setConfirmOpen}
          onConfirm={handleDeleteConfirm}
          title="Excluir evento"
          description="Tem certeza que deseja excluir este evento? Esta ação não pode ser desfeita."
          confirmText="Excluir"
          cancelText="Cancelar"
          variant="destructive"
          theme="viking"
        />
      </div>
    </div>
  );
}
