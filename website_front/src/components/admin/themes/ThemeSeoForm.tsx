import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

interface ThemeSeoFormProps {
  content: Record<string, any>;
  setContent: (content: Record<string, any>) => void;
  contentStr: string;
  setContentStr: (contentStr: string) => void;
}

const updateContent = (
  key: string,
  value: string,
  content: Record<string, any>,
  setContent: (content: Record<string, any>) => void,
  setContentStr: (contentStr: string) => void
) => {
  const updatedContent = { ...content, [key]: value };
  setContent(updatedContent);
  setContentStr(JSON.stringify(updatedContent, null, 2));
};

export function ThemeSeoForm({
  content,
  setContent,
  contentStr,
  setContentStr
}: ThemeSeoFormProps) {
  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-border/30 p-4 space-y-4">
        <div>
          <h3 className="text-lg font-medium text-foreground">SEO Geral</h3>
          <p className="text-sm text-muted-foreground">
            Títulos e descrições usados nos buscadores. Use URLs absolutas para o domínio do cliente.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoTitle" className="text-sm text-foreground">Título</Label>
            <Input
              id="seoTitle"
              value={content.seoTitle || ''}
              onChange={(e) => updateContent('seoTitle', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora - Coquetéis Autorais e Gastronomia"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="seoAuthor" className="text-sm text-foreground">Autor / Organização</Label>
            <Input
              id="seoAuthor"
              value={content.seoAuthor || ''}
              onChange={(e) => updateContent('seoAuthor', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="seoDescription" className="text-sm text-foreground">Descrição</Label>
          <Textarea
            id="seoDescription"
            value={content.seoDescription || ''}
            onChange={(e) => updateContent('seoDescription', e.target.value, content, setContent, setContentStr)}
            placeholder="Bar de coquetéis autorais e cozinha de temporada em Sorocaba. Drinks exclusivos, gastronomia artesanal e ambiente aconchegante para encontros."
            className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoUrl" className="text-sm text-foreground">URL Canônica</Label>
            <Input
              id="seoUrl"
              value={content.seoUrl || ''}
              onChange={(e) => updateContent('seoUrl', e.target.value, content, setContent, setContentStr)}
              placeholder="https://www.baraurora.com.br/"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoSiteName" className="text-sm text-foreground">Site Name</Label>
            <Input
              id="seoSiteName"
              value={content.seoSiteName || ''}
              onChange={(e) => updateContent('seoSiteName', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoLocale" className="text-sm text-foreground">Locale</Label>
            <Input
              id="seoLocale"
              value={content.seoLocale || ''}
              onChange={(e) => updateContent('seoLocale', e.target.value, content, setContent, setContentStr)}
              placeholder="pt_BR"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-border/30 p-4 space-y-4">
        <div>
          <h3 className="text-lg font-medium text-foreground">Open Graph / WhatsApp</h3>
          <p className="text-sm text-muted-foreground">
            Campos usados por Facebook/WhatsApp. Sempre use imagem absoluta e com 1200x630 se possível.
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            Se deixar título/descrição em branco aqui, os valores da seção SEO Geral serão usados como fallback.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoOgTitle" className="text-sm text-foreground">OG Title</Label>
            <Input
              id="seoOgTitle"
              value={content.seoOgTitle || content.seoTitle || ''}
              onChange={(e) => updateContent('seoOgTitle', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora - Coquetéis Autorais e Gastronomia"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoOgType" className="text-sm text-foreground">OG Type</Label>
            <Input
              id="seoOgType"
              value={content.seoOgType || ''}
              onChange={(e) => updateContent('seoOgType', e.target.value, content, setContent, setContentStr)}
              placeholder="website"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="seoOgDescription" className="text-sm text-foreground">OG Description</Label>
          <Textarea
            id="seoOgDescription"
            value={content.seoOgDescription || content.seoDescription || ''}
            onChange={(e) => updateContent('seoOgDescription', e.target.value, content, setContent, setContentStr)}
            placeholder="Bar de coquetéis autorais e cozinha de temporada em Sorocaba. Drinks exclusivos, gastronomia artesanal e ambiente aconchegante para encontros."
            className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoOgImage" className="text-sm text-foreground">OG Image (URL absoluta)</Label>
            <Input
              id="seoOgImage"
              value={content.seoOgImage || ''}
              onChange={(e) => updateContent('seoOgImage', e.target.value, content, setContent, setContentStr)}
              placeholder="https://www.baraurora.com.br/og-image.jpg"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoOgImageAlt" className="text-sm text-foreground">OG Image Alt</Label>
            <Input
              id="seoOgImageAlt"
              value={content.seoOgImageAlt || ''}
              onChange={(e) => updateContent('seoOgImageAlt', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora - Coquetéis Autorais e Gastronomia"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoOgImageWidth" className="text-sm text-foreground">Largura (px)</Label>
            <Input
              id="seoOgImageWidth"
              value={content.seoOgImageWidth || ''}
              onChange={(e) => updateContent('seoOgImageWidth', e.target.value, content, setContent, setContentStr)}
              placeholder="1200"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoOgImageHeight" className="text-sm text-foreground">Altura (px)</Label>
            <Input
              id="seoOgImageHeight"
              value={content.seoOgImageHeight || ''}
              onChange={(e) => updateContent('seoOgImageHeight', e.target.value, content, setContent, setContentStr)}
              placeholder="630"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoThemeColor" className="text-sm text-foreground">Theme Color</Label>
            <Input
              id="seoThemeColor"
              value={content.seoThemeColor || ''}
              onChange={(e) => updateContent('seoThemeColor', e.target.value, content, setContent, setContentStr)}
              placeholder="#f97316"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-border/30 p-4 space-y-4">
        <div>
          <h3 className="text-lg font-medium text-foreground">Twitter</h3>
          <p className="text-sm text-muted-foreground">
            Preencha para cartões no X/Twitter. Quando vazio, use valores de OG.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoTwitterCard" className="text-sm text-foreground">Card</Label>
            <Input
              id="seoTwitterCard"
              value={content.seoTwitterCard || ''}
              onChange={(e) => updateContent('seoTwitterCard', e.target.value, content, setContent, setContentStr)}
              placeholder="summary_large_image"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoTwitterUrl" className="text-sm text-foreground">URL</Label>
            <Input
              id="seoTwitterUrl"
              value={content.seoTwitterUrl || content.seoUrl || ''}
              onChange={(e) => updateContent('seoTwitterUrl', e.target.value, content, setContent, setContentStr)}
              placeholder="https://www.baraurora.com.br/"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoTwitterTitle" className="text-sm text-foreground">Título</Label>
            <Input
              id="seoTwitterTitle"
              value={content.seoTwitterTitle || content.seoOgTitle || content.seoTitle || ''}
              onChange={(e) => updateContent('seoTwitterTitle', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora - Coquetéis Autorais e Gastronomia"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoTwitterDescription" className="text-sm text-foreground">Descrição</Label>
            <Input
              id="seoTwitterDescription"
              value={content.seoTwitterDescription || content.seoOgDescription || content.seoDescription || ''}
              onChange={(e) => updateContent('seoTwitterDescription', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar de coquetéis autorais e cozinha de temporada em Sorocaba. Drinks exclusivos, gastronomia artesanal e ambiente aconchegante para encontros."
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoTwitterImage" className="text-sm text-foreground">Imagem (URL absoluta)</Label>
            <Input
              id="seoTwitterImage"
              value={content.seoTwitterImage || content.seoOgImage || ''}
              onChange={(e) => updateContent('seoTwitterImage', e.target.value, content, setContent, setContentStr)}
              placeholder="https://www.baraurora.com.br/og-image.jpg"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoTwitterImageAlt" className="text-sm text-foreground">Imagem Alt</Label>
            <Input
              id="seoTwitterImageAlt"
              value={content.seoTwitterImageAlt || content.seoOgImageAlt || ''}
              onChange={(e) => updateContent('seoTwitterImageAlt', e.target.value, content, setContent, setContentStr)}
              placeholder="Bar Aurora - Coquetéis Autorais e Gastronomia"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-border/30 p-4 space-y-3">
        <div>
          <h3 className="text-lg font-medium text-foreground">Configurações Extras</h3>
          <p className="text-sm text-muted-foreground">
            Robôs e observações para ajustes finos.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="seoRobots" className="text-sm text-foreground">Robots</Label>
            <Input
              id="seoRobots"
              value={content.seoRobots || ''}
              onChange={(e) => updateContent('seoRobots', e.target.value, content, setContent, setContentStr)}
              placeholder="index, follow"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seoNotes" className="text-sm text-foreground">Notas</Label>
            <Input
              id="seoNotes"
              value={content.seoNotes || ''}
              onChange={(e) => updateContent('seoNotes', e.target.value, content, setContent, setContentStr)}
              placeholder="Ex.: usar sempre imagem 1200x630 com logo"
              className="border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="seoContentJson" className="text-sm text-foreground">JSON de Conteúdo (opcional)</Label>
          <Textarea
            id="seoContentJson"
            value={contentStr}
            onChange={(e) => {
              setContentStr(e.target.value);
              try {
                setContent(JSON.parse(e.target.value));
              } catch (error) {
                console.warn('Erro ao parsear content JSON', error);
              }
            }}
            className="font-mono h-40 border-border/30 focus:ring-2 focus:ring-ring focus:border-transparent bg-input text-foreground"
            placeholder="JSON de conteúdo do tema"
          />
          <p className="text-xs text-muted-foreground">
            É o mesmo objeto da aba Conteúdo; edite direto aqui se preferir ajustes rápidos.
          </p>
        </div>
      </div>
    </div>
  );
}
