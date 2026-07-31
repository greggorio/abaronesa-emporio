// Tipos relacionados à área do cliente

export interface ClienteStats {
  totalVisitas: number;
  totalGasto: number;
  pontosFidelidade: number;
  eventosParticipados: number;
}

export type ReservaStatus = 'CONFIRMADA' | 'PENDENTE' | 'CANCELADA' | 'FINALIZADA';

export interface Reserva {
  id: string;
  dataHora: Date;
  mesa: string;
  numeroPessoas: number;
  status: ReservaStatus;
  observacoes?: string;
}

export interface ItemPedido {
  id: string;
  nome: string;
  categoria: string;
  preco: number;
  quantidade: number;
  imagem?: string;
}

export interface PedidoFavorito {
  id: string;
  data: Date;
  itens: ItemPedido[];
  total: number;
  vezesRepetido: number;
}

export interface ComandaAberta {
  id: string;
  mesa: string;
  aberturaEm: Date;
  itens: ItemPedido[];
  total: number;
}

// Tipos relacionados à gamificação
export interface Recompensa {
  id: number;
  nome: string;
  descricao: string;
  tipo: string;
  pontosNecessarios: number;
  imagemUrl?: string | null;
  podeResgatar: boolean;
  produtoId?: number | null;
  descontoPercentual?: number | null;
  descontoValor?: number | null;
  descontoValorMaximo?: number | null;
  validadeInicio?: string | null;
  validadeFim?: string | null;
  disponivel: boolean;
  faltamPontos: number;
}

export interface CatalogoRecompensasResponse {
  saldo: number;
  recompensas: Recompensa[];
}

export interface ExtratoGamificacao {
  dataHora: string;
  tipo: string;
  origem: string;
  pontos: number;
  saldoApos: number;
  referenciaTipo: string;
  referenciaId: number;
  observacao: string;
}

export interface GamificacaoResponse {
  saldo: number;
  extrato: ExtratoGamificacao[];
}

// Mock data
export const mockClienteStats: ClienteStats = {
  totalVisitas: 12,
  totalGasto: 1850.00,
  pontosFidelidade: 5,
  eventosParticipados: 8
};

export const mockReservas: Reserva[] = [
  {
    id: '1',
    dataHora: new Date('2025-10-18T20:00:00'),
    mesa: '12',
    numeroPessoas: 4,
    status: 'CONFIRMADA',
    observacoes: 'Próximo ao palco'
  },
  {
    id: '2',
    dataHora: new Date('2025-10-25T19:30:00'),
    mesa: '8',
    numeroPessoas: 2,
    status: 'CONFIRMADA'
  },
  {
    id: '3',
    dataHora: new Date('2025-10-12T21:00:00'),
    mesa: '5',
    numeroPessoas: 6,
    status: 'FINALIZADA',
    observacoes: 'Show de Rock'
  }
];

export const mockPedidosFavoritos: PedidoFavorito[] = [
  {
    id: '1',
    data: new Date('2025-10-12T21:30:00'),
    itens: [
      { id: '1', nome: 'X-Bacon Viking', categoria: 'Lanches', preco: 32.00, quantidade: 1 },
      { id: '2', nome: 'Chopp Pilsen 500ml', categoria: 'Bebidas', preco: 15.00, quantidade: 2 }
    ],
    total: 62.00,
    vezesRepetido: 5
  },
  {
    id: '2',
    data: new Date('2025-10-05T20:15:00'),
    itens: [
      { id: '3', nome: 'Porção de Fritas com Cheddar', categoria: 'Porções', preco: 28.00, quantidade: 1 },
      { id: '4', nome: 'Heineken Long Neck', categoria: 'Bebidas', preco: 12.00, quantidade: 4 }
    ],
    total: 76.00,
    vezesRepetido: 3
  },
  {
    id: '3',
    data: new Date('2025-09-28T22:00:00'),
    itens: [
      { id: '5', nome: 'Costela BBQ', categoria: 'Pratos', preco: 45.00, quantidade: 1 },
      { id: '6', nome: 'Cerveja Artesanal IPA', categoria: 'Bebidas', preco: 18.00, quantidade: 2 }
    ],
    total: 81.00,
    vezesRepetido: 2
  }
];

export const mockComandaAberta: ComandaAberta | null = {
  id: 'cmd-001',
  mesa: '5',
  aberturaEm: new Date('2025-10-17T19:45:00'),
  itens: [
    { id: '1', nome: 'Porção de Calabresa', categoria: 'Porções', preco: 32.00, quantidade: 1 },
    { id: '2', nome: 'Chopp Pilsen 500ml', categoria: 'Bebidas', preco: 15.00, quantidade: 2 },
    { id: '3', nome: 'X-Bacon Viking', categoria: 'Lanches', preco: 32.00, quantidade: 1 }
  ],
  total: 94.00
};

// Helpers
export const getReservaStatusColor = (status: ReservaStatus): string => {
  switch (status) {
    case 'CONFIRMADA':
      return 'text-green-400 bg-green-950/30 border-green-800';
    case 'PENDENTE':
      return 'text-yellow-400 bg-yellow-950/30 border-yellow-800';
    case 'CANCELADA':
      return 'text-red-400 bg-red-950/30 border-red-800';
    case 'FINALIZADA':
      return 'text-gray-400 bg-gray-900/30 border-gray-700';
    default:
      return 'text-gray-400 bg-gray-900/30 border-gray-700';
  }
};

export const getReservaStatusLabel = (status: ReservaStatus): string => {
  switch (status) {
    case 'CONFIRMADA':
      return 'Confirmada';
    case 'PENDENTE':
      return 'Pendente';
    case 'CANCELADA':
      return 'Cancelada';
    case 'FINALIZADA':
      return 'Finalizada';
    default:
      return status;
  }
};
