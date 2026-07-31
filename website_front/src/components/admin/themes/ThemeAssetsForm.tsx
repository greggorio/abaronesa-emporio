import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Upload, X } from 'lucide-react';
import villaApi from '@/services/villaApi';

interface ThemeAssetsFormProps {
  assets: Record<string, any>;
  setAssets: (assets: Record<string, any>) => void;
  assetsStr: string;
  setAssetsStr: (assetsStr: string) => void;
  content?: Record<string, any>;
  setContent?: (content: Record<string, any>) => void;
  contentStr?: string;
  setContentStr?: (contentStr: string) => void;
  themeId?: number; // Adiciona o ID do tema para atualização automática
}

export function ThemeAssetsForm({
  assets,
  setAssets,
  assetsStr,
  setAssetsStr,
  content,
  setContent,
  contentStr,
  setContentStr,
  themeId
}: ThemeAssetsFormProps) {
  const [showAssetsJson, setShowAssetsJson] = useState(false);
  const [uploading, setUploading] = useState<Record<string, boolean>>({});

  const handleImageUrlChange = (key: string, value: string) => {
    const updatedAssets = { ...assets, [key]: value };
    setAssets(updatedAssets);
    setAssetsStr(JSON.stringify(updatedAssets, null, 2));
  };

  const handleFileUpload = async (key: string, type: string, file: File) => {
    if (!file) return;

    setUploading(prev => ({ ...prev, [key]: true }));
    const targetThemeId = themeId ?? 0;

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('type', type);
      if (targetThemeId !== 0) {
        formData.append('themeId', String(targetThemeId));
      }

      const response = await villaApi.post('/api/themes/assets/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      if (response.data.success) {
        handleImageUrlChange(key, response.data.url);

        // Se for a imagem de compartilhamento, propagar automaticamente para os campos de SEO
        if (key === 'seoOgImage' && content && setContent && contentStr && setContentStr) {
          const updatedContent = {
            ...content,
            seoOgImage: response.data.url,
            seoTwitterImage: response.data.url
          };
          setContent(updatedContent);
          setContentStr(JSON.stringify(updatedContent, null, 2));
        }

        // Atualizar automaticamente o tema no backend com a nova URL
        if (themeId && themeId !== 0) { // themeId !== 0 indica que é um tema existente
          try {
            // Obter o tema atual
            const themeResponse = await villaApi.get(`/api/themes/${themeId}`);
            const currentTheme = themeResponse.data;

            // Atualizar o tema com os novos assets
            const updatedAssets = { ...currentTheme.assets, [key]: response.data.url };
            const updatedTheme = {
              ...currentTheme,
              assets: updatedAssets
            };

            // Enviar atualização para o backend
            await villaApi.put(`/api/themes/${themeId}`, updatedTheme);
          } catch (themeUpdateError) {
            console.error('Erro ao atualizar tema com novo asset:', themeUpdateError);
            // Se falhar, ainda assim atualizamos o campo localmente
          }
        }
      }
    } catch (error) {
      console.error(`Erro ao fazer upload do ${type}:`, error);
    } finally {
      setUploading(prev => ({ ...prev, [key]: false }));
    }
  };

  const removeImage = (key: string) => {
    const updatedAssets = { ...assets };
    delete updatedAssets[key];
    setAssets(updatedAssets);
    setAssetsStr(JSON.stringify(updatedAssets, null, 2));

    if (key === 'seoOgImage' && content && setContent && contentStr && setContentStr) {
      const updatedContent = { ...content };
      delete updatedContent.seoOgImage;
      delete updatedContent.seoTwitterImage;
      setContent(updatedContent);
      setContentStr(JSON.stringify(updatedContent, null, 2));
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <div className="flex justify-between items-center mb-2">
          <Label className="text-sm font-medium text-foreground">Assets (Imagens e arquivos)</Label>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setShowAssetsJson(!showAssetsJson)}
            className="border-border/40 text-foreground hover:bg-primary/10 hover:text-primary"
          >
            {showAssetsJson ? 'Campos Visuais' : 'Editar JSON'}
          </Button>
        </div>

        {showAssetsJson ? (
          <Textarea
            id="assets"
            value={assetsStr}
            onChange={(e) => {
              setAssetsStr(e.target.value);
              try {
                setAssets(JSON.parse(e.target.value));
              } catch (error) {
                console.warn('Erro ao parsear assets JSON', error);
              }
            }}
            className="font-mono h-60 border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            placeholder="JSON de assets (imagens e arquivos)"
          />
        ) : (
          <div className="space-y-6">
            {/* Logo Upload */}
            <div className="space-y-3">
              <Label className="text-sm text-foreground">Logo</Label>
              <div className="flex items-center gap-4">
                {assets.logoUrl && (
                  <div className="relative">
                    <img
                      src={assets.logoUrl}
                      alt="Logo atual"
                      className="h-16 w-16 object-contain border border-border/30 rounded"
                    />
                    <button
                      type="button"
                      onClick={() => removeImage('logoUrl')}
                      className="absolute -top-2 -right-2 bg-destructive text-destructive-foreground rounded-full p-1 hover:opacity-80"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                )}
                <div className="flex-1">
                  <Input
                    type="file"
                    accept="image/*"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        handleFileUpload('logoUrl', 'logo', file);
                      }
                    }}
                    className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
                    disabled={uploading.logoUrl}
                  />
                  {uploading.logoUrl && (
                    <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
                  )}
                </div>
              </div>
            </div>

            {/* Hero Background Upload */}
            <div className="space-y-3">
              <Label className="text-sm text-foreground">Imagem de Fundo do Hero</Label>
              <div className="flex items-center gap-4">
                {assets.heroBackgroundImageUrl && (
                  <div className="relative">
                    <img
                      src={assets.heroBackgroundImageUrl}
                      alt="Imagem de fundo atual"
                      className="h-16 w-16 object-cover border border-border/30 rounded"
                    />
                    <button
                      type="button"
                      onClick={() => removeImage('heroBackgroundImageUrl')}
                      className="absolute -top-2 -right-2 bg-destructive text-destructive-foreground rounded-full p-1 hover:opacity-80"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                )}
                <div className="flex-1">
                  <Input
                    type="file"
                    accept="image/*"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        handleFileUpload('heroBackgroundImageUrl', 'heroBackground', file);
                      }
                    }}
                    className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
                    disabled={uploading.heroBackgroundImageUrl}
                  />
                  {uploading.heroBackgroundImageUrl && (
                    <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
                  )}
                </div>
              </div>
            </div>

            {/* Favicon Upload */}
            <div className="space-y-3">
              <Label className="text-sm text-foreground">Favicon</Label>
              <div className="flex items-center gap-4">
                {assets.faviconUrl && (
                  <div className="relative">
                    <img
                      src={assets.faviconUrl}
                      alt="Favicon atual"
                      className="h-16 w-16 object-contain border border-border/30 rounded"
                    />
                    <button
                      type="button"
                      onClick={() => removeImage('faviconUrl')}
                      className="absolute -top-2 -right-2 bg-destructive text-destructive-foreground rounded-full p-1 hover:opacity-80"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                )}
                <div className="flex-1">
                  <Input
                    type="file"
                    accept="image/x-icon, image/png, image/jpeg"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        handleFileUpload('faviconUrl', 'favicon', file);
                      }
                    }}
                    className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
                    disabled={uploading.faviconUrl}
                  />
                  {uploading.faviconUrl && (
                    <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
                  )}
                </div>
              </div>
            </div>

            {/* OG/Twitter Image Upload */}
            <div className="space-y-3">
              <Label className="text-sm text-foreground">Imagem de Compartilhamento (OG/Twitter)</Label>
              <p className="text-xs text-muted-foreground">
                Use 1200x630. Ao fazer upload, os campos de SEO são preenchidos automaticamente.
              </p>
              <div className="flex items-center gap-4">
                {assets.seoOgImage && (
                  <div className="relative">
                    <img
                      src={assets.seoOgImage}
                      alt="Imagem OG/Twitter atual"
                      className="h-16 w-28 object-cover border border-border/30 rounded"
                    />
                    <button
                      type="button"
                      onClick={() => removeImage('seoOgImage')}
                      className="absolute -top-2 -right-2 bg-destructive text-destructive-foreground rounded-full p-1 hover:opacity-80"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                )}
                <div className="flex-1">
                  <Input
                    type="file"
                    accept="image/*"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        handleFileUpload('seoOgImage', 'ogImage', file);
                      }
                    }}
                    className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
                    disabled={uploading.seoOgImage}
                  />
                  {uploading.seoOgImage && (
                    <p className="text-xs text-muted-foreground mt-1">Fazendo upload...</p>
                  )}
                </div>
              </div>
            </div>

            {/* Campo texto alternativo */}
            <div className="space-y-2">
              <Label htmlFor="otherAssets" className="text-sm text-foreground">Outros Assets (JSON)</Label>
              <Textarea
                value={JSON.stringify(assets, null, 2)}
                onChange={(e) => {
                  try {
                    const parsed = JSON.parse(e.target.value);
                    setAssets(parsed);
                    setAssetsStr(e.target.value);
                  } catch (error) {
                    console.warn('Erro ao parsear JSON de assets', error);
                  }
                }}
                className="font-mono h-32 border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
                placeholder="JSON adicional de assets"
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
