import { useCallback, useMemo, useState } from "react";
import { ProductType } from "@/components/mesa/ProductCard";
import { CartItemData } from "@/components/mesa/CartItem";
import { productHasValidPrice } from "@/utils/cardapio";

export function useDeliveryCart(initial: CartItemData[] = []) {
  const [cart, setCart] = useState<CartItemData[]>(initial);

  const addToCart = useCallback(
    (product: ProductType, price: number, skuId?: number, variacao?: string) => {
      if (!productHasValidPrice(product)) return;
      const name = variacao ? `${product.nome} (${variacao})` : product.nome;
      setCart((prev) => {
        const existingIndex = prev.findIndex(
          (item) => item.produtoId === product.id && item.skuId === skuId && item.nome === name
        );
        if (existingIndex >= 0) {
          const clone = [...prev];
          clone[existingIndex] = { ...clone[existingIndex], quantidade: clone[existingIndex].quantidade + 1 };
          return clone;
        }
        return [
          ...prev,
          {
            produtoId: product.id,
            skuId,
            nome: name,
            preco: price,
            quantidade: 1,
            origemDesconto: product.origem_desconto ?? product.origemDesconto,
          },
        ];
      });
    },
    []
  );

  const handleAddDefault = useCallback((product: ProductType, price: number) => addToCart(product, price), [addToCart]);
  const handleAddSku = useCallback(
    (product: ProductType, skuId: number, productName: string, variacao: string | undefined, preco: number) =>
      addToCart(product, preco, skuId, variacao || productName),
    [addToCart]
  );

  const handleRemove = useCallback((idx: number) => setCart((prev) => prev.filter((_, index) => index !== idx)), []);

  const handleIncrease = useCallback(
    (idx: number) =>
      setCart((prev) =>
        prev.map((item, index) => (index === idx ? { ...item, quantidade: item.quantidade + 1 } : item))
      ),
    []
  );

  const handleDecrease = useCallback(
    (idx: number) =>
      setCart((prev) =>
        prev
          .map((item, index) =>
            index === idx ? { ...item, quantidade: Math.max(1, item.quantidade - 1) } : item
          )
          .filter((item) => item.quantidade > 0)
      ),
    []
  );

  const handleChangeObs = useCallback(
    (idx: number, value: string) =>
      setCart((prev) => prev.map((item, index) => (index === idx ? { ...item, observacoes: value } : item))),
    []
  );

  const total = useMemo(
    () => cart.reduce((sum, item) => sum + item.preco * item.quantidade, 0),
    [cart]
  );

  return {
    cart,
    total,
    addToCart,
    handleAddDefault,
    handleAddSku,
    handleRemove,
    handleIncrease,
    handleDecrease,
    handleChangeObs,
    setCart,
  };
}
