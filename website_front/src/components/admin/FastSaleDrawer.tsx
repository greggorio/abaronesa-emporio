import { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sheet, SheetContent, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Loader2, Plus, Minus, Trash2, ShoppingCart, Search, User, X, ArrowLeft } from "lucide-react";
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';
import { resolvePrice, resolvePromoPrice } from '@/utils/cardapio';
import { toast } from "@/components/ui/use-toast";

// Tipos adaptados do MesasGrid para consistência
type ProductOption = {
  key: string;
  produtoId: number;
  skuId?: number | null;
  nome: string;
  variacao?: string | null;
  categoria?: string | null;
  preco: number;
  precoPromocional?: number | null;
  disponivel: boolean;
};

type CartItem = ProductOption & {
  quantity: number;
  observacoes?: string;
  tempId: string; // Para identificar unicamente no carrinho (caso tenhamos obs diferentes para mesmo produto)
};

type BuyerOption = {
  id: number;
  nome: string;
  email?: string;
};

interface FastSaleDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
  balcaoSessaoId: number | null; // ID da sessão se já existir, null se for criar nova (Modo Balcão)
  mesaSlug?: string; // Se informado e != 'BALCAO', ativa modo Mesa
  sessaoMesaId?: number | null; // ID da sessão da mesa específica
}

const normalizeToSearch = (value?: string) =>
  (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();

export function FastSaleDrawer({ open, onOpenChange, onSuccess, balcaoSessaoId, mesaSlug, sessaoMesaId }: FastSaleDrawerProps) {
  const navigate = useNavigate();
  const [isMobileCartOpen, setIsMobileCartOpen] = useState(false);
  
  // Determina modo de operação
  const isTableMode = mesaSlug && mesaSlug.toUpperCase() !== 'BALCAO';
  const activeSessaoId = isTableMode ? sessaoMesaId : balcaoSessaoId;
  const drawerTitle = isTableMode ? `Pedido - Mesa ${mesaSlug}` : 'Balcão Expresso';
  
  // Estado do Catálogo
  const [searchTerm, setSearchTerm] = useState('');
  const [allProducts, setAllProducts] = useState<ProductOption[]>([]); // Todos os produtos carregados
  const [loadingProducts, setLoadingProducts] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Produtos filtrados localmente
  const filteredProducts = useMemo(() => {
    const term = normalizeToSearch(searchTerm);
    if (!term) return allProducts;
    return allProducts.filter(p => {
      const nome = normalizeToSearch(p.nome);
      const categoria = normalizeToSearch(p.categoria || '');
      const variacao = normalizeToSearch(p.variacao || '');
      return nome.includes(term) || categoria.includes(term) || variacao.includes(term);
    });
  }, [allProducts, searchTerm]);

  // Estado do Carrinho
  const [cart, setCart] = useState<CartItem[]>([]);
  const [buyerId, setBuyerId] = useState<string | null>(null);
  const [buyerOptions, setBuyerOptions] = useState<BuyerOption[]>([]);
  const [loadingBuyers, setLoadingBuyers] = useState(false);
  const [buyerSearch, setBuyerSearch] = useState('');
  const [buyerSelectOpen, setBuyerSelectOpen] = useState(false);
  const buyerSearchInputRef = useRef<HTMLInputElement>(null);
  const confirmButtonRef = useRef<HTMLButtonElement>(null);
  
  // Estado de Finalização
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [generalObs, setGeneralObs] = useState('');

  // Foco automático na busca ao abrir
  useEffect(() => {
    if (open) {
      setTimeout(() => {
        searchInputRef.current?.focus();
      }, 100);
      fetchProducts();
      loadBuyerOptions();
    }
  }, [open, activeSessaoId, isTableMode]); // Recarrega options se mudar a sessão alvo

  // Reset do estado mobile ao fechar/abrir
  useEffect(() => {
    if (open) {
      setIsMobileCartOpen(false);
      setBuyerId(null); // Reset buyer on open
    }
  }, [open]);

  const loadBuyerOptions = async () => {
    setLoadingBuyers(true);
    try {
      if (isTableMode && activeSessaoId) {
         // Modo Mesa: Carregar convidados da sessão
         const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${activeSessaoId}/convidados`);
         const guests = data.convidados || [];
         setBuyerOptions(guests.map((g: any) => ({
            id: g.sessaoConvidadoId,
            nome: g.nome + (g.host ? ' (Anfitrião)' : ''),
         })));
         
         // Se tiver apenas 1 convidado (ou host), pode pré-selecionar? 
         // Melhor deixar explícito para evitar erros, ou selecionar o primeiro se for único.
         if (guests.length === 1) {
             setBuyerId(String(guests[0].sessaoConvidadoId));
         }
      } else {
         // Modo Balcão: Carregar usuários do sistema (para 'compradorId')
         const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/usuarios/options`);
         const opts = data?.data || [];
         setBuyerOptions(opts.map((u: any) => ({
            id: u.id ?? u.value,
            nome: u.nome ?? u.label ?? '',
            email: u.email,
         })).filter((u: any) => u.id && u.nome));
      }
    } catch (e) {
      console.error("Erro ao carregar compradores", e);
      // Fallback vazio
      setBuyerOptions([]);
    } finally {
      setLoadingBuyers(false);
    }
  };

  const filteredBuyerOptions = useMemo(() => {
    const term = normalizeToSearch(buyerSearch);
    if (!term) return buyerOptions;
    return buyerOptions.filter((b) => normalizeToSearch(b.nome).includes(term));
  }, [buyerOptions, buyerSearch]);

  useEffect(() => {
    if (buyerSelectOpen) {
      setTimeout(() => {
        buyerSearchInputRef.current?.focus();
      }, 0);
    } else {
      setBuyerSearch('');
    }
  }, [buyerSelectOpen]);

  useEffect(() => {
    if (!open) {
      setBuyerSelectOpen(false);
    }
  }, [open]);

  useEffect(() => {
    if (!isTableMode && buyerId) {
      confirmButtonRef.current?.focus();
    }
  }, [buyerId, isTableMode]);

  const mapProducts = (content: any[]): ProductOption[] => {
    const options: ProductOption[] = [];
    (content || []).forEach((p: any) => {
      const basePreco = resolvePrice(p);
      const promo = resolvePromoPrice(p);
      const skus = p?.skus || [];
      
      if (skus.length === 0) {
        options.push({
          key: `p-${p.id}`,
          produtoId: p.id,
          skuId: null,
          nome: p.nome,
          variacao: null,
          categoria: p?.categoriaNome || null,
          preco: Number(basePreco) || 0,
          precoPromocional: typeof promo === 'number' ? promo : null,
          disponivel: p?.produto_disponivel !== false,
        });
      } else {
        skus.forEach((s: any) => {
          const precoSku = typeof s?.preco === 'number' ? s.preco : (typeof s?.precoVenda === 'number' ? s.precoVenda : basePreco);
          const promoSku = typeof s?.precoPromocional === 'number' ? s.precoPromocional : (typeof s?.preco_promocional === 'number' ? s.preco_promocional : null);
          
          let variacaoLabel = s?.variacao || null;
          if (variacaoLabel && ['único', 'unico'].includes(variacaoLabel.toLowerCase())) {
            variacaoLabel = null;
          }

          options.push({
            key: `s-${s.id}`,
            produtoId: p.id,
            skuId: s.id,
            nome: p.nome,
            variacao: variacaoLabel,
            categoria: p?.categoriaNome || null,
            preco: Number(precoSku) || 0,
            precoPromocional: typeof promoSku === 'number' ? promoSku : (typeof promo === 'number' ? promo : null),
            disponivel: p?.produto_disponivel !== false && (s?.ativo !== false),
          });
        });
      }
    });
    return options;
  };

  const fetchProducts = async () => {
    if (allProducts.length > 0) return; // Evita recarregar se já tem cache local na sessão do componente
    
    setLoadingProducts(true);
    try {
      const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/cardapio/v2`, {
        params: {
          page: 0,
          size: 2000, // Carregar catálogo inteiro para busca local rápida
        },
      });
      setAllProducts(mapProducts(data?.content || []));
    } catch (e) {
      console.error("Erro ao buscar produtos", e);
      toast({ title: "Erro", description: "Falha ao carregar produtos.", variant: "destructive" });
    } finally {
      setLoadingProducts(false);
    }
  };

  const addToCart = (product: ProductOption) => {
    setCart(prev => {
      // Tenta encontrar item idêntico (mesmo produto/sku e sem observação específica que diferencie)
      const existingIndex = prev.findIndex(item => item.key === product.key && !item.observacoes);
      
      if (existingIndex >= 0) {
        const newCart = [...prev];
        newCart[existingIndex].quantity += 1;
        return newCart;
      }
      
      return [...prev, {
        ...product,
        quantity: 1,
        tempId: Math.random().toString(36).substr(2, 9),
        observacoes: ''
      }];
    });
    
    // Manter foco na busca (se não for mobile, no mobile talvez não seja ideal focar input se ele sumir)
    if (!isMobileCartOpen) {
       searchInputRef.current?.focus();
    }
    
    // Feedback visual
    toast({ 
      title: "Item adicionado", 
      description: `${product.nome} adicionado ao carrinho.`,
      duration: 1500
    });
  };

  const updateQuantity = (tempId: string, delta: number) => {
    setCart(prev => prev.map(item => {
      if (item.tempId === tempId) {
        const newQty = Math.max(1, item.quantity + delta);
        return { ...item, quantity: newQty };
      }
      return item;
    }));
  };

  const removeFromCart = (tempId: string) => {
    setCart(prev => prev.filter(item => item.tempId !== tempId));
  };

  const updateObs = (tempId: string, obs: string) => {
    setCart(prev => prev.map(item => 
      item.tempId === tempId ? { ...item, observacoes: obs } : item
    ));
  };

  const cartTotal = useMemo(() => {
    return cart.reduce((acc, item) => {
      const price = typeof item.precoPromocional === 'number' ? item.precoPromocional : item.preco;
      return acc + (price * item.quantity);
    }, 0);
  }, [cart]);

  const handleFinish = async (redirectToPayment: boolean = false) => {
    if (cart.length === 0) return;

    // Validação obrigatória para modo Mesa
    if (isTableMode && !buyerId) {
        toast({ 
            title: "Identificação necessária", 
            description: "Selecione o convidado da mesa que está pedindo.", 
            variant: "destructive" 
        });
        return;
    }

    setIsSubmitting(true);
    try {
      if (isTableMode && activeSessaoId) {
          // Endpoint de Mesa
          const payload = {
            sessaoConvidadoId: Number(buyerId),
            itens: cart.map(item => ({
              produtoId: item.produtoId,
              skuId: item.skuId || null,
              quantidade: item.quantity,
              observacoes: item.observacoes?.trim() || (generalObs ? `Obs Geral: ${generalObs}` : null),
            }))
          };
          await axios.post(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes/${activeSessaoId}/pedidos`, payload);
          toast({ title: "Sucesso", description: `Pedido enviado para ${mesaSlug}` });
      } else {
          // Endpoint de Balcão (Fast Checkout)
          const payload = {
            itens: cart.map(item => ({
              produtoId: item.produtoId,
              skuId: item.skuId || null,
              quantidade: item.quantity,
              observacoes: item.observacoes?.trim() || (generalObs ? `Obs Geral: ${generalObs}` : null),
            })),
            origem: 'fast_checkout',
            compradorId: buyerId ? Number(buyerId) : null,
          };
          await axios.post(`${apiConfig.erpBaseUrl}/api/admin/mesas/balcao/pedidos`, payload);
          const toastRef = toast({ title: "Sucesso", description: "Venda lançada no balcão!" });
          window.setTimeout(() => toastRef.dismiss(), 2000);
      }
      
      // Limpeza de estado
      setCart([]);
      setGeneralObs('');
      setBuyerId(null);
      setSearchTerm('');
      
      // Callback para atualizar a grid
      onSuccess();
      
      // Fecha o drawer
      onOpenChange(false);

      // Redirecionamento se necessário (Lógica para Balcão mantida, para Mesa redireciona para Pagamentos da Mesa)
      if (redirectToPayment) {
        let targetId = activeSessaoId;

        // Se for balcão e não tinha ID, busca agora (Lógica antiga mantida)
        if (!isTableMode && !targetId) {
            await new Promise(r => setTimeout(r, 500));
            try {
                const { data } = await axios.get(`${apiConfig.erpBaseUrl}/api/admin/mesas/sessoes`, { params: { status: 'open' } });
                const balcaoRow = (data.sessoes || []).find((r: any) => r.mesaSlug.toUpperCase() === 'BALCAO');
                if (balcaoRow) targetId = balcaoRow.sessaoMesaId;
            } catch (err) { console.error(err); }
        }

        if (targetId) {
            navigate(`/admin/mesas/${targetId}/pagamentos`, { 
                state: { mesaSlug: isTableMode ? mesaSlug : 'BALCAO' } 
            });
        }
      }

    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.message || 'Erro ao registrar venda';
      toast({ title: "Erro", description: msg, variant: "destructive" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="z-[9999] w-full sm:max-w-[900px] flex flex-col p-0 gap-0 bg-[#FBF6F2] text-[#2A1F1B]">
        <div className="flex flex-1 h-full overflow-hidden flex-col md:flex-row relative">
          
          {/* COLUNA ESQUERDA: CATÁLOGO */}
          <div className={`flex-1 flex flex-col border-r border-[#8B7355]/20 bg-white/50 ${isMobileCartOpen ? 'hidden md:flex' : 'flex'}`}>
            <div className="p-4 border-b border-[#8B7355]/10 space-y-4">
              <div>
                <SheetTitle className="text-xl font-display uppercase tracking-wide text-[#2A1F1B]">{drawerTitle}</SheetTitle>
                <SheetDescription className="text-[#8B7355]">Adicione itens ao pedido</SheetDescription>
              </div>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input 
                  ref={searchInputRef}
                  placeholder="Buscar produto (Nome, Categoria...)" 
                  value={searchTerm}
                  onChange={e => setSearchTerm(e.target.value)}
                  className="pl-9 pr-10 bg-white border-[#D7B899]/30 focus-visible:ring-[#8B7355]"
                />
                {searchTerm && (
                  <button
                    type="button"
                    onClick={() => {
                      setSearchTerm('');
                      searchInputRef.current?.focus();
                    }}
                    className="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-black/5 p-1 text-muted-foreground transition hover:bg-black/10 focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <X className="h-3 w-3" />
                    <span className="sr-only">Limpar busca</span>
                  </button>
                )}
              </div>
            </div>
            
            <ScrollArea className="flex-1 p-4 pb-24">
              <div className="grid grid-cols-1 gap-2">
                {loadingProducts && <div className="text-center py-8 text-muted-foreground"><Loader2 className="h-6 w-6 animate-spin mx-auto"/>Carregando...</div>}
                
                {!loadingProducts && filteredProducts.length === 0 && (
                  <div className="text-center py-12 text-muted-foreground">Nenhum produto encontrado</div>
                )}

                {filteredProducts.map(product => {
                  const price = typeof product.precoPromocional === 'number' ? product.precoPromocional : product.preco;
                  return (
                    <button
                      key={product.key}
                      onClick={() => addToCart(product)}
                      disabled={!product.disponivel}
                      className="flex items-center justify-between p-3 rounded-lg border border-transparent hover:border-[#D7B899]/30 hover:bg-white hover:shadow-sm transition-all text-left group bg-[#FBF6F2]/50"
                    >
                      <div className="flex-1">
                        <div className="font-medium text-[#2A1F1B] group-hover:text-[#8B7355] transition-colors">
                          {product.nome}
                        </div>
                        <div className="text-xs text-muted-foreground flex gap-2 items-center">
                          {product.variacao && <Badge variant="secondary" className="text-[10px] h-4 px-1 bg-[#D7B899]/20 text-[#2A1F1B]">{product.variacao}</Badge>}
                          <span>{product.categoria}</span>
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-semibold text-[#2A1F1B]">R$ {price.toFixed(2)}</div>
                        {typeof product.precoPromocional === 'number' && (
                          <div className="text-[10px] line-through text-muted-foreground">R$ {product.preco.toFixed(2)}</div>
                        )}
                      </div>
                      {!product.disponivel && <div className="ml-3 text-xs text-red-500 font-medium">Indisponível</div>}
                    </button>
                  );
                })}
              </div>
            </ScrollArea>
          </div>

          {/* COLUNA DIREITA: CARRINHO */}
          <div className={`md:w-[400px] w-full flex flex-col bg-white shadow-xl z-10 ${!isMobileCartOpen ? 'hidden md:flex' : 'flex'}`}>
            
            {/* Header Mobile do Carrinho */}
            <div className="md:hidden p-2 border-b border-[#8B7355]/10 flex items-center bg-[#FBF6F2]">
                <Button variant="ghost" size="sm" onClick={() => setIsMobileCartOpen(false)} className="text-[#8B7355] hover:bg-[#8B7355]/10">
                   <ArrowLeft className="h-4 w-4 mr-2" />
                   Voltar aos produtos
                </Button>
            </div>

            <div className="p-4 bg-[#FBF6F2] border-b border-[#8B7355]/10 flex items-center justify-between">
              <div className="flex items-center gap-2 font-medium text-[#2A1F1B]">
                <ShoppingCart className="h-4 w-4" />
                Carrinho
                <Badge className="bg-[#8B7355] hover:bg-[#8B7355]/90 text-white ml-1">{cart.reduce((a, b) => a + b.quantity, 0)}</Badge>
              </div>
              <div className="text-xs text-muted-foreground">
                {drawerTitle}
              </div>
            </div>

            <ScrollArea className="flex-1 p-4">
              {cart.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-muted-foreground opacity-50 space-y-4">
                  <ShoppingCart className="h-12 w-12" />
                  <p>Carrinho vazio</p>
                  <Button variant="link" className="md:hidden text-[#8B7355]" onClick={() => setIsMobileCartOpen(false)}>
                      Adicionar produtos
                  </Button>
                </div>
              ) : (
                <div className="space-y-4">
                  {cart.map(item => {
                     const price = typeof item.precoPromocional === 'number' ? item.precoPromocional : item.preco;
                     return (
                      <div key={item.tempId} className="group relative bg-[#FBF6F2]/30 rounded-lg p-3 border border-[#D7B899]/20 hover:border-[#D7B899]/50 transition-colors">
                        <div className="flex justify-between items-start mb-2">
                          <div className="pr-4">
                            <div className="font-medium text-sm text-[#2A1F1B] leading-tight">{item.nome}</div>
                            {item.variacao && <div className="text-xs text-[#8B7355] mt-0.5">{item.variacao}</div>}
                          </div>
                          <div className="font-medium text-sm">R$ {(price * item.quantity).toFixed(2)}</div>
                        </div>
                        
                        <div className="flex items-center justify-between mt-2">
                          <div className="flex items-center gap-1 bg-white rounded-md border border-[#D7B899]/30 shadow-sm">
                            <Button variant="ghost" size="icon" className="h-7 w-7 rounded-none rounded-l-md hover:bg-[#D7B899]/20" onClick={() => updateQuantity(item.tempId, -1)}>
                              <Minus className="h-3 w-3" />
                            </Button>
                            <span className="w-8 text-center text-sm font-medium">{item.quantity}</span>
                            <Button variant="ghost" size="icon" className="h-7 w-7 rounded-none rounded-r-md hover:bg-[#D7B899]/20" onClick={() => updateQuantity(item.tempId, 1)}>
                              <Plus className="h-3 w-3" />
                            </Button>
                          </div>
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-red-500 hover:bg-red-50" onClick={() => removeFromCart(item.tempId)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                        <Input 
                          placeholder="Obs..." 
                          className="mt-2 h-7 text-xs bg-transparent border-transparent hover:border-[#D7B899]/30 focus:border-[#D7B899]/50 focus:bg-white px-1"
                          value={item.observacoes || ''}
                          onChange={e => updateObs(item.tempId, e.target.value)}
                        />
                      </div>
                    );
                  })}
                </div>
              )}
            </ScrollArea>

            <div className="p-4 bg-[#FBF6F2] border-t border-[#8B7355]/10 space-y-4 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
              {/* Seleção de Cliente */}
              <div className="space-y-2">
                 <div className="flex gap-2">
                    <Select
                      value={buyerId || 'none'}
                      onValueChange={v => setBuyerId(v === 'none' ? null : v)}
                      onOpenChange={(open) => setBuyerSelectOpen(open)}
                    >
                      <SelectTrigger className="h-8 text-xs bg-white border-[#D7B899]/30">
                        <div className="flex items-center gap-2">
                          <User className="h-3 w-3" />
                          <SelectValue placeholder={isTableMode ? "Convidado (Obrigatório)" : "Consumidor (Opcional)"} />
                        </div>
                      </SelectTrigger>
                      <SelectContent className="z-[10000] bg-[#FBF6F2] border-[#8B7355]/30 text-[#2A1F1B]">
                        <div className="sticky top-0 z-10 bg-[#FBF6F2] px-3 pt-3 pb-1">
                          <Input
                            ref={buyerSearchInputRef}
                            value={buyerSearch}
                            onChange={(event) => setBuyerSearch(event.target.value)}
                            placeholder="Buscar consumidor"
                            className="h-8 text-xs bg-white border-[#D7B899]/30"
                            autoComplete="off"
                            onKeyDown={(event) => {
                              const { key } = event;
                              if (key.length === 1 || key === 'Backspace' || key === 'Delete') {
                                event.stopPropagation();
                              }
                            }}
                          />
                          {loadingBuyers && (
                            <p className="text-[10px] text-[#8B7355] mt-1">Carregando consumidores...</p>
                          )}
                        </div>
                        <SelectItem className="focus:bg-[#D7B899]/30 focus:text-[#2A1F1B]" value="none">
                            {isTableMode ? "Selecione..." : "Consumidor"}
                        </SelectItem>
                        {filteredBuyerOptions.map(b => (
                          <SelectItem className="focus:bg-[#D7B899]/30 focus:text-[#2A1F1B]" key={b.id} value={String(b.id)}>{b.nome}</SelectItem>
                        ))}
                        {filteredBuyerOptions.length === 0 && !loadingBuyers && (
                          <div className="px-3 py-2 text-[11px] text-muted-foreground">Nenhum consumidor encontrado</div>
                        )}
                      </SelectContent>
                    </Select>
                 </div>
              </div>

              <Separator className="bg-[#D7B899]/20" />
              
              <div className="flex justify-between items-end">
                <div className="text-sm text-muted-foreground">Total a pagar</div>
                <div className="text-2xl font-bold text-[#2A1F1B]">R$ {cartTotal.toFixed(2)}</div>
              </div>

            <div className={isTableMode ? "grid grid-cols-2 gap-2" : "grid grid-cols-1 gap-2"}>
                 {isTableMode && (
                   <Button 
                      variant="outline" 
                      className="w-full border-[#8B7355]/30 text-[#8B7355] hover:bg-[#8B7355]/10"
                      onClick={() => handleFinish(false)}
                      disabled={cart.length === 0 || isSubmitting}
                   >
                      Adicionar
                   </Button>
                 )}

                 {!isTableMode && (
                   <Button 
                     ref={confirmButtonRef}
                     className="w-full bg-[#B5854C] hover:bg-[#B5854C]/90 text-white"
                     onClick={() => handleFinish(true)}
                     disabled={cart.length === 0 || isSubmitting}
                   >
                     {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Confirmar'}
                   </Button>
                 )}
              </div>
            </div>
          </div>

          {/* Barra de Rodapé Mobile (Flutuante GLOBAL) */}
          {cart.length > 0 && !isMobileCartOpen && (
                <div className="md:hidden absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-white via-white to-transparent z-50">
                  <Button 
                    className="w-full bg-[#B5854C] hover:bg-[#B5854C]/90 text-white flex justify-between items-center h-12 shadow-lg animate-in slide-in-from-bottom-5"
                    onClick={() => setIsMobileCartOpen(true)}
                  >
                    <div className="flex items-center gap-2">
                       <ShoppingCart className="h-4 w-4" />
                       <span className="text-sm font-medium">{cart.reduce((a, b) => a + b.quantity, 0)} itens</span>
                    </div>
                    <span className="font-bold">
                       Ver Carrinho (R$ {cartTotal.toFixed(2)})
                    </span>
                  </Button>
                </div>
             )}

        </div>
      </SheetContent>
    </Sheet>
  );
}
