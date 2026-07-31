import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type Category = {
  id: number;
  nome: string;
};

type Props = {
  search: string;
  onSearchChange: (value: string) => void;
  categories: Category[];
  selectedCategoryId: number | "all";
  onCategoryChange: (categoryId: number | "all") => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
};

export function DeliveryCatalogFilters({
  search,
  onSearchChange,
  categories,
  selectedCategoryId,
  onCategoryChange,
  t,
}: Props) {
  return (
    <div className="flex flex-col sm:flex-row gap-2">
      <Input
        placeholder={t("delivery.filters.search.placeholder")}
        value={search}
        onChange={(event) => onSearchChange(event.target.value)}
        className="sm:w-64"
      />
      <div className="flex gap-2 overflow-x-auto py-1">
        <Button
          variant={selectedCategoryId === "all" ? "default" : "outline"}
          size="sm"
          onClick={() => onCategoryChange("all")}
        >
          {t("delivery.filters.category.all")}
        </Button>
        {categories.map((category) => (
          <Button
            key={category.id}
            variant={selectedCategoryId === category.id ? "default" : "outline"}
            size="sm"
            onClick={() => onCategoryChange(category.id)}
          >
            {category.nome}
          </Button>
        ))}
      </div>
    </div>
  );
}
