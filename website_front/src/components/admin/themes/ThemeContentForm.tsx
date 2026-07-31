import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

interface ThemeContentFormProps {
  content: Record<string, any>;
  setContent: (content: Record<string, any>) => void;
  contentStr: string;
  setContentStr: (contentStr: string) => void;
}

// Componente para o seletor de ícones
interface IconSelectorProps {
  selectedIcon: string;
  onSelectIcon: (iconName: string) => void;
  inputId: string;
}

const IconSelector = ({ selectedIcon, onSelectIcon, inputId }: IconSelectorProps) => {
  const [isGridOpen, setIsGridOpen] = useState(false);

  const iconOptions = [
    { name: 'Coffee', icon: '☕' },
    { name: 'Heart', icon: '❤️' },
    { name: 'Users', icon: '👥' },
    { name: 'Guitar', icon: '🎸' },
    { name: 'Beer', icon: '🍺' },
    { name: 'Utensils', icon: '🍽️' },
    { name: 'Wine', icon: '🍷' },
    { name: 'Music', icon: '🎵' },
    { name: 'MapPin', icon: '📍' },
    { name: 'Calendar', icon: '📅' },
    { name: 'Clock', icon: '🕒' },
    { name: 'Star', icon: '⭐' },
    { name: 'Book', icon: '📚' },
    { name: 'Gift', icon: '🎁' },
    { name: 'Fire', icon: '🔥' },
    { name: 'Lightning', icon: '⚡' },
    { name: 'Smile', icon: '😊' },
    { name: 'Phone', icon: '📞' },
    { name: 'Envelope', icon: '✉️' },
    { name: 'ShoppingCart', icon: '🛒' },
    { name: 'Home', icon: '🏠' },
    { name: 'Search', icon: '🔍' },
    { name: 'Settings', icon: '⚙️' },
    { name: 'Bell', icon: '🔔' },
    { name: 'Camera', icon: '📷' },
    { name: 'Video', icon: '📹' },
    { name: 'Message', icon: '💬' },
    { name: 'Like', icon: '👍' },
    { name: 'Dislike', icon: '👎' },
    { name: 'Share', icon: '🔄' },
  ];

  return (
    <div className="relative">
      <button
        type="button"
        className="w-full border border-[#8B7355]/30 rounded px-3 py-2 text-left bg-[#FBF6F2] text-[#2A1F1B] hover:bg-[#D7B899]/10 flex justify-between items-center"
        onClick={(e) => {
          e.preventDefault();
          setIsGridOpen(!isGridOpen);
        }}
      >
        <span>
          {selectedIcon
            ? `${selectedIcon} `
            : 'Selecione um ícone...'}
        </span>
        <span>▼</span>
      </button>

      {isGridOpen && (
        <div className="absolute z-10 mt-1 w-full bg-white border border-[#8B7355]/30 rounded shadow-lg p-3 max-h-60 overflow-y-auto">
          <div className="grid grid-cols-8 gap-2">
            {iconOptions.map((iconOption) => (
              <button
                key={iconOption.name}
                type="button"
                className={`p-2 rounded border flex flex-col items-center justify-center text-xs ${
                  selectedIcon === iconOption.name
                    ? 'border-[#D7B899] bg-[#D7B899]/10'
                    : 'border-[#8B7355]/30 hover:bg-[#D7B899]/10'
                }`}
                onClick={() => {
                  onSelectIcon(iconOption.name);
                  setIsGridOpen(false);
                }}
                title={iconOption.name}
              >
                <span className="text-lg">{iconOption.icon}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export function ThemeContentForm({
  content,
  setContent,
  contentStr,
  setContentStr
}: ThemeContentFormProps) {
  const [showContentJson, setShowContentJson] = useState(false);

  const handleContentChange = (key: string, value: string) => {
    const updatedContent = { ...content, [key]: value };
    setContent(updatedContent);
    setContentStr(JSON.stringify(updatedContent, null, 2));
  };


  return (
    <div className="space-y-4">
      <div>
        <div className="flex justify-between items-center mb-2">
          <Label className="text-sm font-medium text-[#2A1F1B]">Conteúdo do Site</Label> {/* cafe-dark-roast */}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setShowContentJson(!showContentJson)}
            className="border-[#8B7355]/40 text-[#2A1F1B] hover:bg-[#D7B899]/10 hover:text-[#D7B899]"
          > {/* cafe-com-leite, cafe-dark-roast, cafe-latte-suave */}
            {showContentJson ? 'Campos Visuais' : 'Editar JSON'}
          </Button>
        </div>

        {showContentJson ? (
          <Textarea
            id="content"
            value={contentStr}
            onChange={(e) => {
              setContentStr(e.target.value);
              try {
                setContent(JSON.parse(e.target.value));
              } catch (error) {
                console.warn('Erro ao parsear content JSON', error);
              }
            }}
            className="font-mono h-60 border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
            placeholder="JSON de conteúdo do site"
          />
        ) : (
          <div className="space-y-6">
            {/* Seção Hero */}
            <div className="border border-[#8B7355]/30 rounded-lg p-4">
              <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Seção Hero</h3> {/* cafe-dark-roast */}

              <div className="space-y-3">
                <div className="space-y-2">
                  <Label htmlFor="heroTitle" className="text-sm text-[#2A1F1B]">Título do Hero</Label> {/* cafe-dark-roast */}
                  <Input
                    id="heroTitle"
                    value={content.heroTitle || ''}
                    onChange={(e) => handleContentChange('heroTitle', e.target.value)}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: Cafés Especiais, Momentos Inesquecíveis"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="heroSubtitle" className="text-sm text-[#2A1F1B]">Subtítulo do Hero</Label> {/* cafe-dark-roast */}
                  <Input
                    id="heroSubtitle"
                    value={content.heroSubtitle || ''}
                    onChange={(e) => handleContentChange('heroSubtitle', e.target.value)}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: Espaço aconchegante para estudar, trabalhar e saborear"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="heroCtaText" className="text-sm text-[#2A1F1B]">Texto do CTA Principal</Label> {/* cafe-dark-roast */}
                    <Input
                      id="heroCtaText"
                      value={content.heroCtaText || ''}
                      onChange={(e) => handleContentChange('heroCtaText', e.target.value)}
                      className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                      placeholder="Ex: Ver Cardápio"
                    /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="heroCtaHref" className="text-sm text-[#2A1F1B]">Link do CTA Principal</Label> {/* cafe-dark-roast */}
                    <Input
                      id="heroCtaHref"
                      value={content.heroCtaHref || ''}
                      onChange={(e) => handleContentChange('heroCtaHref', e.target.value)}
                      className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                      placeholder="Ex: /cardapio"
                    /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="heroSecondaryCtaText" className="text-sm text-[#2A1F1B]">Texto do CTA Secundário</Label> {/* cafe-dark-roast */}
                    <Input
                      id="heroSecondaryCtaText"
                      value={content.heroSecondaryCtaText || ''}
                      onChange={(e) => handleContentChange('heroSecondaryCtaText', e.target.value)}
                      className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                      placeholder="Ex: Reservar Mesa"
                    /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="reservationLink" className="text-sm text-[#2A1F1B]">Link de Reserva</Label> {/* cafe-dark-roast */}
                    <Input
                      id="reservationLink"
                      value={content.reservationLink || ''}
                      onChange={(e) => handleContentChange('reservationLink', e.target.value)}
                      className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                      placeholder="Ex: https://wa.me/..."
                    /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                  </div>
                </div>
              </div>
            </div>

            {/* Seção Cards do Hero */}
            <div className="border border-[#8B7355]/30 rounded-lg p-4">
              <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Cards do Hero</h3> {/* cafe-dark-roast */}
              <p className="text-sm text-[#8B7355]/70 mb-4">Configure os 3 cards exibidos abaixo do CTA no hero (ícones válidos: Coffee, Heart, Users, Guitar, Beer, UtensilsCrossed, Music, Wine)</p> {/* cafe-com-leite */}

              {[0, 1, 2].map((index) => (
                <div key={index} className="border border-[#8B7355]/20 rounded-md p-3 mb-3 last:mb-0">
                  <h4 className="text-md font-medium text-[#2A1F1B] mb-2">Card {index + 1}</h4> {/* cafe-dark-roast */}

                  <div className="space-y-3">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <Label htmlFor={`heroCard${index}Icon`} className="text-sm text-[#2A1F1B]">Ícone</Label> {/* cafe-dark-roast */}
                        <IconSelector
                          selectedIcon={(content.heroCards && content.heroCards[index]?.icon) || ''}
                          onSelectIcon={(iconName) => {
                            const updatedCards = [...(content.heroCards || [])];
                            if (!updatedCards[index]) updatedCards[index] = {};
                            updatedCards[index].icon = iconName;
                            handleContentChange('heroCards', updatedCards);
                          }}
                          inputId={`heroCard${index}Icon`}
                        />
                        <Input
                          id={`heroCard${index}Icon`}
                          value={(content.heroCards && content.heroCards[index]?.icon) || ''}
                          onChange={(e) => {
                            const updatedCards = [...(content.heroCards || [])];
                            if (!updatedCards[index]) updatedCards[index] = {};
                            updatedCards[index].icon = e.target.value;
                            handleContentChange('heroCards', updatedCards);
                          }}
                          className="mt-2 border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                          placeholder="Ou digite um nome de ícone personalizado"
                        /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor={`heroCard${index}Title`} className="text-sm text-[#2A1F1B]">Título</Label> {/* cafe-dark-roast */}
                        <Input
                          id={`heroCard${index}Title`}
                          value={(content.heroCards && content.heroCards[index]?.title) || ''}
                          onChange={(e) => {
                            const updatedCards = [...(content.heroCards || [])];
                            if (!updatedCards[index]) updatedCards[index] = {};
                            updatedCards[index].title = e.target.value;
                            handleContentChange('heroCards', updatedCards);
                          }}
                          className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                          placeholder="Ex: Cafés Especiais"
                        /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                      </div>
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor={`heroCard${index}Description`} className="text-sm text-[#2A1F1B]">Descrição</Label> {/* cafe-dark-roast */}
                      <Input
                        id={`heroCard${index}Description`}
                        value={(content.heroCards && content.heroCards[index]?.description) || ''}
                        onChange={(e) => {
                          const updatedCards = [...(content.heroCards || [])];
                          if (!updatedCards[index]) updatedCards[index] = {};
                          updatedCards[index].description = e.target.value;
                          handleContentChange('heroCards', updatedCards);
                        }}
                        className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                        placeholder="Ex: Grãos selecionados e baristas experientes"
                      /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Seção Informações do Negócio */}
            <div className="border border-[#8B7355]/30 rounded-lg p-4">
              <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Informações do Negócio</h3> {/* cafe-dark-roast */}

              <div className="space-y-3">
                <div className="space-y-2">
                  <Label htmlFor="name" className="text-sm text-[#2A1F1B]">Nome do Negócio</Label> {/* cafe-dark-roast */}
                  <Input
                    id="name"
                    value={content.name || ''}
                    onChange={(e) => handleContentChange('name', e.target.value)}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: Espresso English"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="businessType" className="text-sm text-[#2A1F1B]">Tipo de Negócio</Label> {/* cafe-dark-roast */}
                  <Input
                    id="businessType"
                    value={content.businessType || ''}
                    onChange={(e) => handleContentChange('businessType', e.target.value)}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: CAFETERIA"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>
              </div>
            </div>

          </div>
        )}
      </div>

      {/* Seção Sobre */}
      <div className="border border-[#8B7355]/30 rounded-lg p-4">
        <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Seção Sobre (About)</h3> {/* cafe-dark-roast */}

        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="aboutTitle" className="text-sm text-[#2A1F1B]">Título do Sobre</Label> {/* cafe-dark-roast */}
            <Input
              id="aboutTitle"
              value={content.aboutTitle || ''}
              onChange={(e) => handleContentChange('aboutTitle', e.target.value)}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: Nossa Cafeteria"
            /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
          </div>

          <div className="space-y-2">
            <Label htmlFor="aboutDescription1" className="text-sm text-[#2A1F1B]">Primeira Descrição</Label> {/* cafe-dark-roast */}
            <Textarea
              id="aboutDescription1"
              value={content.aboutDescription1 || ''}
              onChange={(e) => handleContentChange('aboutDescription1', e.target.value)}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: Desde 2020, oferecemos a melhor experiência em café..."
              rows={3}
            /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
          </div>

          <div className="space-y-2">
            <Label htmlFor="aboutDescription2" className="text-sm text-[#2A1F1B]">Segunda Descrição</Label> {/* cafe-dark-roast */}
            <Textarea
              id="aboutDescription2"
              value={content.aboutDescription2 || ''}
              onChange={(e) => handleContentChange('aboutDescription2', e.target.value)}
              className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
              placeholder="Ex: Drinks especiais, doces artesanais, salgados deliciosos..."
              rows={3}
            /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="aboutAddress" className="text-sm text-[#2A1F1B]">Endereço</Label> {/* cafe-dark-roast */}
              <Textarea
                id="aboutAddress"
                value={content.aboutAddress || ''}
                onChange={(e) => handleContentChange('aboutAddress', e.target.value)}
                className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                placeholder="Ex: R. João Wagner Wey, 421 - Jardim America&lt;br /&gt;Sorocaba - SP, 18046-695"
                rows={3}
              /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
            </div>

            <div className="space-y-2">
              <Label htmlFor="aboutPhone" className="text-sm text-[#2A1F1B]">Telefone</Label> {/* cafe-dark-roast */}
              <Input
                id="aboutPhone"
                value={content.aboutPhone || ''}
                onChange={(e) => handleContentChange('aboutPhone', e.target.value)}
                className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                placeholder="Ex: (15) 98835-4989"
              /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
            </div>
          </div>
        </div>
      </div>

      {/* Seção Horários do Sobre */}
      <div className="border border-[#8B7355]/30 rounded-lg p-4">
        <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Horários de Funcionamento</h3> {/* cafe-dark-roast */}
        <p className="text-sm text-[#8B7355]/70 mb-4">Configure os horários exibidos no Sobre</p> {/* cafe-com-leite */}

        {[0, 1, 2].map((index) => (
          <div key={index} className="border border-[#8B7355]/20 rounded-md p-3 mb-3 last:mb-0">
            <h4 className="text-md font-medium text-[#2A1F1B] mb-2">Período {index + 1}</h4> {/* cafe-dark-roast */}

            <div className="space-y-3">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor={`aboutHour${index}Days`} className="text-sm text-[#2A1F1B]">Dias</Label> {/* cafe-dark-roast */}
                  <Input
                    id={`aboutHour${index}Days`}
                    value={(content.aboutHours && content.aboutHours[index]?.days) || ''}
                    onChange={(e) => {
                      const updatedHours = [...(content.aboutHours || [])];
                      if (!updatedHours[index]) updatedHours[index] = {};
                      updatedHours[index].days = e.target.value;
                      handleContentChange('aboutHours', updatedHours);
                    }}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: Segunda a Sexta"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>

                <div className="space-y-2">
                  <Label htmlFor={`aboutHour${index}Hours`} className="text-sm text-[#2A1F1B]">Horário</Label> {/* cafe-dark-roast */}
                  <Input
                    id={`aboutHour${index}Hours`}
                    value={(content.aboutHours && content.aboutHours[index]?.hours) || ''}
                    onChange={(e) => {
                      const updatedHours = [...(content.aboutHours || [])];
                      if (!updatedHours[index]) updatedHours[index] = {};
                      updatedHours[index].hours = e.target.value;
                      handleContentChange('aboutHours', updatedHours);
                    }}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: 8h - 21h"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor={`aboutHour${index}Notes`} className="text-sm text-[#2A1F1B]">Notas (Opcional)</Label> {/* cafe-dark-roast */}
                <Input
                  id={`aboutHour${index}Notes`}
                  value={(content.aboutHours && content.aboutHours[index]?.notes) || ''}
                  onChange={(e) => {
                    const updatedHours = [...(content.aboutHours || [])];
                    if (!updatedHours[index]) updatedHours[index] = {};
                    updatedHours[index].notes = e.target.value;
                    handleContentChange('aboutHours', updatedHours);
                  }}
                  className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                  placeholder="Ex: Almoço Viking: 12h-16h (Come à Vontade!)"
                /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Seção Recursos do Sobre */}
      <div className="border border-[#8B7355]/30 rounded-lg p-4">
        <h3 className="text-lg font-medium text-[#2A1F1B] mb-3">Recursos do Sobre</h3> {/* cafe-dark-roast */}
        <p className="text-sm text-[#8B7355]/70 mb-4">Configure os 4 cards de recursos exibidos no Sobre (ícones válidos: Coffee, Cookie, Wifi, Armchair, Axe, Beer, Music, Flame)</p> {/* cafe-com-leite */}

        {[0, 1, 2, 3].map((index) => (
          <div key={index} className="border border-[#8B7355]/20 rounded-md p-3 mb-3 last:mb-0">
            <h4 className="text-md font-medium text-[#2A1F1B] mb-2">Recurso {index + 1}</h4> {/* cafe-dark-roast */}

            <div className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor={`aboutFeature${index}Icon`} className="text-sm text-[#2A1F1B]">Ícone</Label> {/* cafe-dark-roast */}
                <IconSelector
                  selectedIcon={(content.aboutFeatures && content.aboutFeatures[index]?.icon) || ''}
                  onSelectIcon={(iconName) => {
                    const updatedFeatures = [...(content.aboutFeatures || [])];
                    if (!updatedFeatures[index]) updatedFeatures[index] = {};
                    updatedFeatures[index].icon = iconName;
                    handleContentChange('aboutFeatures', updatedFeatures);
                  }}
                  inputId={`aboutFeature${index}Icon`}
                />
                <Input
                  id={`aboutFeature${index}Icon`}
                  value={(content.aboutFeatures && content.aboutFeatures[index]?.icon) || ''}
                  onChange={(e) => {
                    const updatedFeatures = [...(content.aboutFeatures || [])];
                    if (!updatedFeatures[index]) updatedFeatures[index] = {};
                    updatedFeatures[index].icon = e.target.value;
                    handleContentChange('aboutFeatures', updatedFeatures);
                  }}
                  className="mt-2 border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                  placeholder="Ou digite um nome de ícone personalizado"
                /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor={`aboutFeature${index}Title`} className="text-sm text-[#2A1F1B]">Título</Label> {/* cafe-dark-roast */}
                  <Input
                    id={`aboutFeature${index}Title`}
                    value={(content.aboutFeatures && content.aboutFeatures[index]?.title) || ''}
                    onChange={(e) => {
                      const updatedFeatures = [...(content.aboutFeatures || [])];
                      if (!updatedFeatures[index]) updatedFeatures[index] = {};
                      updatedFeatures[index].title = e.target.value;
                      handleContentChange('aboutFeatures', updatedFeatures);
                    }}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: Café Premium"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>

                <div className="space-y-2">
                  <Label htmlFor={`aboutFeature${index}Description`} className="text-sm text-[#2A1F1B]">Descrição</Label> {/* cafe-dark-roast */}
                  <Input
                    id={`aboutFeature${index}Description`}
                    value={(content.aboutFeatures && content.aboutFeatures[index]?.description) || ''}
                    onChange={(e) => {
                      const updatedFeatures = [...(content.aboutFeatures || [])];
                      if (!updatedFeatures[index]) updatedFeatures[index] = {};
                      updatedFeatures[index].description = e.target.value;
                      handleContentChange('aboutFeatures', updatedFeatures);
                    }}
                    className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
                    placeholder="Ex: Grãos especiais selecionados"
                  /> {/* cafe-com-leite, cafe-latte-claro, cafe-dark-roast */}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
