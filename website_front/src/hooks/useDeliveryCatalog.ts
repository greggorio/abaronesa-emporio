import { useEffect, useMemo, useState } from "react";
import { apiConfig } from "@/config/api";
import { sanitizeCardapio } from "@/utils/cardapio";
import { ProductType } from "@/components/mesa/ProductCard";

type CardapioCategoria = {
  id: number;
  nome: string;
  produtos: ProductType[];
};

export function useDeliveryCatalog(locale?: string) {
  const [categories, setCategories] = useState<CardapioCategoria[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | "all">("all");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [errorKey, setErrorKey] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError("");
      setErrorKey(null);
      try {
        const res = await fetch(`${apiConfig.erpBaseUrl}/api/public/cardapio/delivery`, {
          headers: {
            "Cache-Control": "no-store",
            ...(locale ? { "Accept-Language": locale } : {}),
          },
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data: CardapioCategoria[] = await res.json();
        const sanitized = sanitizeCardapio(data);
        setCategories(sanitized);
        if (sanitized.length > 0) {
          setSelectedCategoryId("all");
        }
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : "Falha ao carregar cardápio";
        setError(errorMessage);
        setErrorKey("delivery.menu.errors.catalogLoad");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [locale]);

  const allProducts = useMemo(() => {
    if (!categories.length) return [];
    const filteredCats =
      selectedCategoryId === "all"
        ? categories
        : categories.filter((category) => category.id === selectedCategoryId);
    const flat = filteredCats.flatMap((category) => category.produtos || []);
    if (!search.trim()) return flat;
    const searchTerm = search.trim().toLowerCase();
    return flat.filter((product) => product.nome.toLowerCase().includes(searchTerm));
  }, [categories, search, selectedCategoryId]);

  return {
    categories,
    selectedCategoryId,
    setSelectedCategoryId,
    search,
    setSearch,
    loading,
    error,
    errorKey,
    allProducts,
  };
}
