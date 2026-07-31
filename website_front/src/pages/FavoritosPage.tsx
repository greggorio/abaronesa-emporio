import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Heart, ShoppingBag, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { apiConfig } from '@/config/api';

// Dados mockados de produtos favoritos
const MOCK_FAVORITES = [
  {
    id: 1,
    nome: 'Burger Viking',
    descricao: 'Hambúrguer artesanal 200g, queijo cheddar, bacon crocante, cebola caramelizada',
    preco: 35.00,
    imagemPrincipal: '/produtos/burger-viking.jpg',
  },
  {
    id: 2,
    nome: 'Cerveja Heineken 600ml',
    descricao: 'Cerveja premium holandesa, garrafa 600ml gelada',
    preco: 15.00,
    imagemPrincipal: '/produtos/heineken.jpg',
  },
  {
    id: 3,
    nome: 'Batata Rústica',
    descricao: 'Batata rústica frita, com alho e ervas, acompanha molho especial',
    preco: 25.00,
    imagemPrincipal: '/produtos/batata-rustica.jpg',
  },
];

export default function FavoritosPage() {
  const navigate = useNavigate();
  const [favorites, setFavorites] = useState(MOCK_FAVORITES);

  const handleRemove = (id: number) => {
    setFavorites(prev => prev.filter(item => item.id !== id));
  };

  return (
    <div className="min-h-screen bg-forest-dark text-cream">
      {/* Header */}
      <div className="bg-forest-dark/95 border-b border-coral-accent/20 sticky top-0 z-10">
        <div className="max-w-md mx-auto px-4 py-4">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate(-1)}
              className="p-2 hover:bg-coral-accent/10 rounded-lg transition"
            >
              <ArrowLeft className="w-5 h-5 text-cream" />
            </button>
            <div>
              <h1 className="text-xl font-display tracking-wider">Meus Favoritos</h1>
              <p className="text-xs text-cream/70">
                {favorites.length} {favorites.length === 1 ? 'produto' : 'produtos'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-md mx-auto px-4 py-6">
        {favorites.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <Heart className="w-16 h-16 text-coral-accent/30 mb-4" />
            <h2 className="text-lg font-medium text-cream mb-2">
              Nenhum favorito ainda
            </h2>
            <p className="text-sm text-cream/70 mb-6">
              Adicione produtos aos favoritos para acesso rápido
            </p>
            <Button
              onClick={() => navigate('/')}
              className="bg-coral-accent text-forest-dark hover:bg-coral-accent/90"
            >
              Explorar Cardápio
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            {favorites.map((product) => (
              <div
                key={product.id}
                className="border border-coral-accent/20 rounded-lg p-4 bg-forest-dark/50 hover:border-coral-accent/40 transition"
              >
                <div className="flex gap-4">
                  {/* Imagem do Produto */}
                  <div className="flex-shrink-0">
                    <div className="w-20 h-20 bg-coral-accent/10 rounded-lg flex items-center justify-center overflow-hidden">
                      {product.imagemPrincipal ? (
                        <img
                          src={apiConfig.getMediaUrl(product.imagemPrincipal)}
                          alt={product.nome}
                          className="w-full h-full object-cover"
                          onError={(e) => {
                            (e.target as HTMLImageElement).style.display = 'none';
                          }}
                        />
                      ) : (
                        <ShoppingBag className="w-8 h-8 text-coral-accent/50" />
                      )}
                    </div>
                  </div>

                  {/* Info do Produto */}
                  <div className="flex-1 min-w-0">
                    <h3 className="font-medium text-cream mb-1">{product.nome}</h3>
                    {product.descricao && (
                      <p className="text-xs text-cream/70 line-clamp-2 mb-2">
                        {product.descricao}
                      </p>
                    )}
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-lg font-display text-coral-accent">
                        R$ {product.preco.toFixed(2)}
                      </span>
                      <button
                        onClick={() => handleRemove(product.id)}
                        className="p-2 hover:bg-red-500/10 rounded-lg transition text-red-400 hover:text-red-300"
                        title="Remover dos favoritos"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                    <Button
                      onClick={() => {
                        // TODO: Adicionar ao carrinho
                        alert(`Produto "${product.nome}" adicionado ao carrinho!`);
                      }}
                      className="w-full bg-coral-accent text-forest-dark hover:bg-coral-accent/90 h-9 text-sm"
                    >
                      <ShoppingBag className="w-4 h-4 mr-2" />
                      Adicionar
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Aviso de dados mockados */}
        <div className="mt-8 p-4 bg-coral-accent/10 border border-coral-accent/30 rounded-lg">
          <p className="text-xs text-cream/80 text-center">
            ℹ️ Esta é uma prévia da funcionalidade. Os dados exibidos são fictícios e serão substituídos por seus favoritos reais em breve.
          </p>
        </div>
      </div>
    </div>
  );
}
