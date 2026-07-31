import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Trash2, Upload, Plus, Image as ImageIcon, Pencil, CheckCircle, XCircle } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import villaApi from '@/services/villaApi';
import ConfirmDialog from '@/components/admin/ConfirmDialog';
import { Switch } from '@/components/ui/switch';

interface Categoria {
  id: number;
  nome: string;
  ordem: number;
  ativo: boolean;
}

interface Foto {
  id: number;
  url: string;
  createdAt: string;
}

export default function GaleriaAdmin() {
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [fotos, setFotos] = useState<Record<number, Foto[]>>({});
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<string>('');
  
  // Estados para diálogos
  const [isCategoriaDialogOpen, setIsCategoriaDialogOpen] = useState(false);
  const [isUploadDialogOpen, setIsUploadDialogOpen] = useState(false);
  const [novaCategoriaNome, setNovaCategoriaNome] = useState('');
  const [novaCategoriaOrdem, setNovaCategoriaOrdem] = useState(0);
  const [novaCategoriaAtiva, setNovaCategoriaAtiva] = useState(true);
  const [selectedFiles, setSelectedFiles] = useState<FileList | null>(null);
  const [uploading, setUploading] = useState(false);

  // Estados para exclusão
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteData, setDeleteData] = useState<{ type: 'foto' | 'categoria'; id: number } | null>(null);

  // Estados para edição
  const [isEditCategoriaDialogOpen, setIsEditCategoriaDialogOpen] = useState(false);
  const [editCategoriaId, setEditCategoriaId] = useState<number | null>(null);
  const [editCategoriaNome, setEditCategoriaNome] = useState('');
  const [editCategoriaOrdem, setEditCategoriaOrdem] = useState(0);
  const [editCategoriaAtiva, setEditCategoriaAtiva] = useState(true);

  const { toast } = useToast();

  useEffect(() => {
    carregarDados();
  }, []);

  const carregarDados = async () => {
    try {
      setLoading(true);
      // Carregar todas as categorias, incluindo inativas, para o admin
      const categoriasRes = await villaApi.get('/api/galeria/categorias?includeInactive=true');
      setCategorias(categoriasRes.data);

      if (categoriasRes.data.length > 0 && !activeTab) {
        setActiveTab(categoriasRes.data[0].id.toString());
      }

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
      console.error('Erro ao carregar dados:', error);
      toast({
        title: 'Erro',
        description: 'Falha ao carregar galeria.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCriarCategoria = async () => {
    if (!novaCategoriaNome.trim()) return;

    try {
      await villaApi.post('/api/galeria/categorias', {
        nome: novaCategoriaNome,
        ordem: novaCategoriaOrdem,
        ativo: novaCategoriaAtiva
      });
      
      toast({ title: 'Sucesso', description: 'Categoria criada.' });
      setIsCategoriaDialogOpen(false);
      setNovaCategoriaNome('');
      setNovaCategoriaAtiva(true);
      carregarDados();
    } catch (error) {
      toast({ title: 'Erro', description: 'Erro ao criar categoria.', variant: 'destructive' });
    }
  };

  const handleEditClick = (cat: Categoria) => {
    setEditCategoriaId(cat.id);
    setEditCategoriaNome(cat.nome);
    setEditCategoriaOrdem(cat.ordem);
    setEditCategoriaAtiva(cat.ativo);
    setIsEditCategoriaDialogOpen(true);
  };

  const handleUpdateCategoria = async () => {
    if (!editCategoriaId || !editCategoriaNome.trim()) return;

    try {
      await villaApi.put(`/api/galeria/categorias/${editCategoriaId}`, {
        nome: editCategoriaNome,
        ordem: editCategoriaOrdem,
        ativo: editCategoriaAtiva
      });

      toast({ title: 'Sucesso', description: 'Categoria atualizada.' });
      setIsEditCategoriaDialogOpen(false);
      carregarDados();
    } catch (error) {
      toast({ title: 'Erro', description: 'Erro ao atualizar categoria.', variant: 'destructive' });
    }
  };

  const handleUpload = async () => {
    if (!selectedFiles || !activeTab) return;

    const formData = new FormData();
    formData.append('categoriaId', activeTab);
    
    Array.from(selectedFiles).forEach((file) => {
      formData.append('fotos', file);
    });

    try {
      setUploading(true);
      await villaApi.post('/api/galeria/fotos/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });

      toast({ title: 'Sucesso', description: 'Fotos enviadas com sucesso.' });
      setIsUploadDialogOpen(false);
      setSelectedFiles(null);
      carregarDados();
    } catch (error: any) {
      let errorMessage = 'Falha no upload.';
      if (error.response && error.response.data && error.response.data.message) {
        errorMessage = error.response.data.message;
      } else if (error.message) {
        errorMessage = error.message;
      }

      toast({
        title: 'Erro',
        description: errorMessage,
        variant: 'destructive'
      });
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteFoto = (fotoId: number) => {
    setDeleteData({ type: 'foto', id: fotoId });
    setConfirmOpen(true);
  };

  const handleDeleteCategoria = (categoriaId: number) => {
    setDeleteData({ type: 'categoria', id: categoriaId });
    setConfirmOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!deleteData) return;

    try {
      if (deleteData.type === 'foto') {
        await villaApi.delete(`/api/galeria/fotos/${deleteData.id}`);
        toast({ title: 'Excluída', description: 'Foto removida.' });
      } else {
        await villaApi.delete(`/api/galeria/categorias/${deleteData.id}`);
        toast({ title: 'Excluída', description: 'Categoria removida.' });
        if (activeTab === deleteData.id.toString()) {
          setActiveTab('');
        }
      }
      carregarDados();
    } catch (error: any) {
      let errorMessage = `Erro ao excluir ${deleteData.type}.`;
      if (error.response && error.response.data && error.response.data.message) {
        errorMessage = error.response.data.message;
      }
      toast({ title: 'Erro', description: errorMessage, variant: 'destructive' });
    } finally {
      setConfirmOpen(false);
      setDeleteData(null);
    }
  };

  return (
    <div className="min-h-screen bg-[#FAFAFA] p-6 space-y-6"> {/* cafe-latte-claro */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-display tracking-wider text-[#2A1F1B]">Galeria de Fotos</h1> {/* cafe-dark-roast */}
          <p className="text-sm text-[#2A1F1B]/70">Gerencie as categorias e fotos do site.</p> {/* cafe-dark-roast */}
        </div>
        <div className="flex gap-2">
          <Button
            onClick={() => setIsCategoriaDialogOpen(true)}
            variant="outline"
            className="bg-white border-[#8B7355]/30 text-[#2A1F1B] hover:bg-[#8B7355]/10"
          > {/* cafe-com-leite and cafe-dark-roast */}
            <Plus className="w-4 h-4 mr-2" /> Nova Categoria
          </Button>
          <Button
            onClick={() => setIsUploadDialogOpen(true)}
            className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90" /* cafe-latte-suave and cafe-dark-roast */
            disabled={categorias.length === 0}
          >
            <Upload className="w-4 h-4 mr-2" /> Upload Fotos
          </Button>
        </div>
      </div>

      {loading ? (
        <div>Carregando...</div>
      ) : categorias.length === 0 ? (
        <Card className="bg-[#FBF6F2] border-[#8B7355]/20"> {/* cafe-latte-claro and cafe-com-leite */}
          <CardContent className="flex flex-col items-center justify-center py-12 text-[#2A1F1B]/60"> {/* cafe-dark-roast */}
            <ImageIcon className="w-12 h-12 mb-4 opacity-50" />
            <p>Nenhuma categoria criada.</p>
            <Button
              onClick={() => setIsCategoriaDialogOpen(true)}
              variant="link"
              className="text-[#D7B899]" /* cafe-latte-suave */
            >
              Criar a primeira categoria
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <div className="flex items-center justify-between mb-4">
            <TabsList className="bg-[#FBF6F2] border border-[#8B7355]/20"> {/* cafe-latte-claro and cafe-com-leite */}
              {categorias.map(cat => (
                <TabsTrigger
                  key={cat.id}
                  value={cat.id.toString()}
                  className="data-[state=active]:bg-[#D7B899] data-[state=active]:text-[#2A1F1B] text-[#2A1F1B] flex items-center gap-2"
                >
                  <span className={cat.ativo ? '' : 'text-gray-400'}>{cat.nome}</span>
                  {!cat.ativo && <XCircle className="w-3 h-3 text-red-400" />}
                </TabsTrigger>
              ))}
            </TabsList>

            {activeTab && (
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-[#8B7355] hover:bg-[#8B7355]/10"
                  onClick={() => {
                    const cat = categorias.find(c => c.id.toString() === activeTab);
                    if (cat) handleEditClick(cat);
                  }}
                >
                  <Pencil className="w-4 h-4 mr-2" /> Editar
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-red-600 hover:bg-red-50"
                  onClick={() => handleDeleteCategoria(Number(activeTab))}
                >
                  <Trash2 className="w-4 h-4 mr-2" /> Excluir
                </Button>
              </div>
            )}
          </div>

          {categorias.map(cat => (
            <TabsContent key={cat.id} value={cat.id.toString()}>
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-4">
                {fotos[cat.id]?.map(foto => (
                  <Card key={foto.id} className="group relative overflow-hidden bg-white border-[#8B7355]/10"> {/* cafe-com-leite */}
                    <div className="aspect-square relative">
                      <img
                        src={foto.url}
                        alt="Galeria"
                        className="w-full h-full object-cover transition-transform group-hover:scale-105"
                      />
                      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                        <Button
                          variant="destructive"
                          size="icon"
                          onClick={() => handleDeleteFoto(foto.id)}
                        >
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </div>
                  </Card>
                ))}
                {(!fotos[cat.id] || fotos[cat.id].length === 0) && (
                  <div className="col-span-full py-12 text-center text-[#2A1F1B]/50 border-2 border-dashed border-[#8B7355]/10 rounded-lg"> {/* cafe-dark-roast and cafe-com-leite */}
                    Nenhuma foto nesta categoria.
                  </div>
                )}
              </div>
            </TabsContent>
          ))}
        </Tabs>
      )}

      {/* Dialog Nova Categoria */}
      <Dialog open={isCategoriaDialogOpen} onOpenChange={setIsCategoriaDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Nova Categoria</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Nome</Label>
              <Input
                value={novaCategoriaNome}
                onChange={e => setNovaCategoriaNome(e.target.value)}
                placeholder="Ex: Ambiente, Pratos..."
              />
            </div>
            <div className="space-y-2">
              <Label>Ordem</Label>
              <Input
                type="number"
                value={novaCategoriaOrdem}
                onChange={e => setNovaCategoriaOrdem(Number(e.target.value))}
              />
            </div>
            <div className="flex items-center space-x-2">
              <Switch
                id="nova-ativo"
                checked={novaCategoriaAtiva}
                onCheckedChange={setNovaCategoriaAtiva}
              />
              <Label htmlFor="nova-ativo">Ativo</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCategoriaDialogOpen(false)}>Cancelar</Button>
            <Button
              onClick={handleCriarCategoria}
              className="bg-[#D7B899] text-[#2A1F1B]" /* cafe-latte-suave and cafe-dark-roast */
            >
              Criar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog Editar Categoria */}
      <Dialog open={isEditCategoriaDialogOpen} onOpenChange={setIsEditCategoriaDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Editar Categoria</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Nome</Label>
              <Input
                value={editCategoriaNome}
                onChange={e => setEditCategoriaNome(e.target.value)}
                placeholder="Ex: Ambiente, Pratos..."
              />
            </div>
            <div className="space-y-2">
              <Label>Ordem</Label>
              <Input
                type="number"
                value={editCategoriaOrdem}
                onChange={e => setEditCategoriaOrdem(Number(e.target.value))}
              />
            </div>
            <div className="flex items-center space-x-2">
              <Switch
                id="edit-ativo"
                checked={editCategoriaAtiva}
                onCheckedChange={setEditCategoriaAtiva}
              />
              <Label htmlFor="edit-ativo">Ativo</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsEditCategoriaDialogOpen(false)}>Cancelar</Button>
            <Button
              onClick={handleUpdateCategoria}
              className="bg-[#D7B899] text-[#2A1F1B]"
            >
              Salvar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog Upload */}
      <Dialog open={isUploadDialogOpen} onOpenChange={setIsUploadDialogOpen}>
        <DialogContent className="bg-[#FBF6F2] border-[#8B7355]/20 text-[#2A1F1B]"> {/* cafe-latte-claro, cafe-com-leite and cafe-dark-roast */}
          <DialogHeader>
            <DialogTitle className="text-[#2A1F1B]">Upload de Fotos</DialogTitle> {/* cafe-dark-roast */}
          </DialogHeader>
          <div className="space-y-4 py-4">
             <p className="text-sm text-[#2A1F1B]/70"> {/* cafe-dark-roast */}
               Enviando para categoria: <strong className="text-[#8B7355]">{categorias.find(c => c.id.toString() === activeTab)?.nome}</strong> {/* cafe-com-leite */}
             </p>
             <Input
               type="file"
               multiple
               accept="image/*"
               onChange={e => setSelectedFiles(e.target.files)}
               className="bg-white border-[#8B7355]/30 text-[#2A1F1B] file:bg-[#8B7355]/10 file:text-[#2A1F1B] file:border-0 file:rounded-sm file:font-medium hover:file:bg-[#8B7355]/20 cursor-pointer" /* cafe-com-leite and cafe-dark-roast */
             />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsUploadDialogOpen(false)}
              className="bg-white border-[#8B7355]/30 text-[#2A1F1B] hover:bg-[#8B7355]/10" /* cafe-com-leite and cafe-dark-roast */
            >
              Cancelar
            </Button>
            <Button
              onClick={handleUpload}
              disabled={!selectedFiles || uploading}
              className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 font-medium" /* cafe-latte-suave and cafe-dark-roast */
            >
              {uploading ? 'Enviando...' : 'Enviar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        onConfirm={handleConfirmDelete}
        title={deleteData?.type === 'foto' ? "Excluir foto" : "Excluir categoria"}
        description={deleteData?.type === 'foto'
          ? "Tem certeza que deseja excluir esta foto? Esta ação não pode ser desfeita."
          : "Tem certeza que deseja excluir esta categoria? Ela deve estar vazia para ser excluída."}
        confirmText="Excluir"
        cancelText="Cancelar"
        variant="destructive"
        theme="forest"
      />
    </div>
  );
}
