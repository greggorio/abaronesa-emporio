import { useState, useEffect } from "react";
import { Card } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import villaApi from '@/services/villaApi';
import { useSiteI18n } from "@/i18n/useSiteI18n";

interface Categoria {
  id: number;
  nome: string;
  ordem: number;
}

interface Foto {
  id: number;
  url: string;
}

const Gallery = () => {
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [fotos, setFotos] = useState<Record<number, Foto[]>>({});
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const { t } = useSiteI18n();

  useEffect(() => {
    carregarDados();
  }, []);

  const carregarDados = async () => {
    try {
      const categoriasRes = await villaApi.get('/api/galeria/categorias');
      setCategorias(categoriasRes.data);

      // Carregar fotos de cada categoria
      const fotosPromises = categoriasRes.data.map((cat: Categoria) =>
        villaApi.get(`/api/galeria/fotos/categoria/${cat.id}`)
      );
      const fotosResults = await Promise.all(fotosPromises);

      const fotosPorCategoria: Record<number, Foto[]> = {};
      categoriasRes.data.forEach((cat: Categoria, index: number) => {
        fotosPorCategoria[cat.id] = fotosResults[index].data;
      });

      setFotos(fotosPorCategoria);
    } catch (error) {
      console.error('Erro ao carregar galeria:', error);
    } finally {
      setLoading(false);
    }
  };

  const getFotosPorCategoria = (categoriaId: number) => {
    return fotos[categoriaId] || [];
  };

  // Não exibir se não houver categorias ou nenhuma foto em todas as categorias
  const totalFotos = Object.values(fotos).reduce((acc, arr) => acc + arr.length, 0);
  if (!loading && (categorias.length === 0 || totalFotos === 0)) {
    return null;
  }

  return (
    <section id="galeria" className="py-16 md:py-20 bg-background">
      <div className="container mx-auto px-4">
        <div className="text-center mb-12 md:mb-16">
          <h2 className="text-4xl md:text-5xl font-display mb-4 md:mb-6 text-foreground">
            {t("site.gallery.title")}
            <span className="block text-accent">{t("site.gallery.subtitleAccent")}</span>
          </h2>
          <p className="text-lg md:text-xl text-muted-foreground max-w-3xl mx-auto leading-relaxed">
            {t("site.gallery.description")}
          </p>
        </div>

        {loading ? (
          <div className="text-center text-muted-foreground py-12">
            {t("site.gallery.loading")}
          </div>
        ) : (
          <Tabs defaultValue={categorias[0]?.id.toString()} className="w-full">
            <TabsList className="grid w-full mb-8" style={{ gridTemplateColumns: `repeat(${categorias.length}, minmax(0, 1fr))` }}>
              {categorias.map(categoria => (
                <TabsTrigger key={categoria.id} value={categoria.id.toString()}>
                  {categoria.nome}
                </TabsTrigger>
              ))}
            </TabsList>

            {categorias.map(categoria => {
              const fotosDaCategoria = getFotosPorCategoria(categoria.id);

              return (
                <TabsContent key={categoria.id} value={categoria.id.toString()}>
                  {fotosDaCategoria.length === 0 ? (
                    <div className="text-center text-muted-foreground py-12">
                      {t("site.gallery.empty")}
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                      {fotosDaCategoria.map((foto, index) => (
                        <Card
                          key={foto.id}
                          className="group relative overflow-hidden cursor-pointer bg-card border-border hover:border-accent/50 transition-all duration-300 hover:shadow-lg"
                          onClick={() => setSelectedImage(foto.url)}
                        >
                          <div className="aspect-square relative overflow-hidden">
                            <img
                              src={foto.url}
                              alt={t("site.gallery.imageAlt")}
                              className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                            />
                            <div className="absolute inset-0 bg-foreground/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                          </div>
                        </Card>
                      ))}
                    </div>
                  )}
                </TabsContent>
              );
            })}
          </Tabs>
        )}

        {/* Dialog para imagem expandida */}
        <Dialog open={selectedImage !== null} onOpenChange={() => setSelectedImage(null)}>
          <DialogContent className="max-w-5xl p-0 overflow-hidden">
            {selectedImage && (
              <div className="relative">
                <img
                  src={selectedImage}
                  alt={t("site.gallery.imageExpandedAlt")}
                  className="w-full h-auto max-h-[90vh] object-contain"
                />
              </div>
            )}
          </DialogContent>
        </Dialog>
      </div>
    </section>
  );
};

export default Gallery;
