import { MenuCategory, MenuItem } from '@/data/menu';
import { apiConfig } from '@/config/api';

// Tipos da resposta da API do backend
interface CardapioProdutoAPI {
  id: number;
  nome: string;
  descricao: string | null;
  preco: number | null;
  imagemPrincipal: string | null;
  destaque: boolean | null;
  ordem: number;
}

interface CardapioCategoriaAPI {
  id: number;
  nome: string;
  icone: string | null;
  cover: string | null;
  ordem: number;
  produtos: CardapioProdutoAPI[];
}

// Função para converter a resposta da API no formato esperado pelo componente Menu
const converterParaMenuData = (categorias: CardapioCategoriaAPI[]): MenuCategory[] => {
  return categorias.map(categoria => ({
    title: categoria.nome,
    items: categoria.produtos.map(produto => ({
      name: produto.nome,
      description: produto.descricao || undefined,
      price: produto.preco ? `R$ ${produto.preco.toFixed(2)}` : 'Consultar',
      image: apiConfig.getMediaUrl(produto.imagemPrincipal),
      signature: produto.destaque || false,
    })),
  }));
};

export const cardapioApi = {
  // Buscar cardápio completo do backend
  async buscarCardapio(locale?: string): Promise<MenuCategory[]> {
    const response = await fetch(apiConfig.erp.cardapio, {
      headers: locale ? { "Accept-Language": locale } : undefined,
    });

    if (!response.ok) {
      throw new Error('Erro ao buscar cardápio');
    }

    const categorias: CardapioCategoriaAPI[] = await response.json();
    return converterParaMenuData(categorias);
  },

  // Buscar produtos em destaque
  async buscarDestaques(locale?: string): Promise<MenuItem[]> {
    const response = await fetch(apiConfig.erp.destaques, {
      headers: locale ? { "Accept-Language": locale } : undefined,
    });

    if (!response.ok) {
      throw new Error('Erro ao buscar destaques');
    }

    const produtos: CardapioProdutoAPI[] = await response.json();
    return produtos.map(produto => ({
      name: produto.nome,
      description: produto.descricao || undefined,
      price: produto.preco ? `R$ ${produto.preco.toFixed(2)}` : 'Consultar',
      image: apiConfig.getMediaUrl(produto.imagemPrincipal),
      signature: produto.destaque || false,
    }));
  },
};
