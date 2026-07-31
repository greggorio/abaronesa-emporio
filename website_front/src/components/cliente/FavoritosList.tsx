import { Heart, Coffee } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { apiConfig } from '@/config/api';

interface ProdutoFrequenteDTO {
  produtoId: number;
  nome: string;
  imagem: string;
  quantidadeTotal: number;
}

interface FavoritosListProps {
  favoritos: ProdutoFrequenteDTO[];
}

export default function FavoritosList({ favoritos }: FavoritosListProps) {
  if (favoritos.length === 0) return null;

  return (
    <div className="space-y-3">
      <h2 className="text-lg font-display text-mesa-text flex items-center gap-2 px-1">
        <Heart className="w-5 h-5 text-[hsl(var(--accent))]" />
        Seus Favoritos
      </h2>
      <div className="flex gap-3 overflow-x-auto pb-4 px-1 -mx-4 md:mx-0 px-4 md:px-0 snap-x no-scrollbar">
        {favoritos.map(prod => (
          <Card 
            key={prod.produtoId} 
            className="snap-center shrink-0 w-32 md:w-40 border border-[hsl(var(--accent)/0.2)] bg-white overflow-hidden group cursor-pointer hover:shadow-md transition-all"
          >
            <div className="aspect-square bg-gray-50 relative overflow-hidden">
              {prod.imagem ? (
                <img 
                  src={apiConfig.getMediaUrl(prod.imagem) || ''} 
                  alt={prod.nome} 
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  onError={(e) => {
                    e.currentTarget.style.display = 'none';
                    e.currentTarget.parentElement?.querySelector('.placeholder')?.classList.remove('hidden');
                  }}
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-mesa-text/30">
                  <Coffee className="w-8 h-8" />
                </div>
              )}
              {/* Fallback oculto que aparece no onError */}
              <div className="placeholder hidden w-full h-full absolute inset-0 flex items-center justify-center text-mesa-text/30 bg-gray-50">
                 <Coffee className="w-8 h-8" />
              </div>
            </div>
            <div className="p-2.5">
              <h4 className="font-medium text-mesa-text text-xs line-clamp-2 min-h-[2.5em]" title={prod.nome}>
                {prod.nome}
              </h4>
              <p className="text-[10px] text-mesa-text/70 mt-1">
                {prod.quantidadeTotal} pedidos
              </p>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
