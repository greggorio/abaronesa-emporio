import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

interface ThemeTokensFormProps {
  tokens: Record<string, string>;
  setTokens: (tokens: Record<string, string>) => void;
  tokensStr: string;
  setTokensStr: (tokensStr: string) => void;
}

// Função para converter HSL string para hex
const hslToHex = (hslStr: string): string => {
  if (!hslStr) return '#000000';
  const [h, s, l] = hslStr.split(' ').map(v => parseFloat(v));
  const hDecimal = h / 360;
  const sDecimal = s / 100;
  const lDecimal = l / 100;

  const c = (1 - Math.abs(2 * lDecimal - 1)) * sDecimal;
  const x = c * (1 - Math.abs((hDecimal * 6) % 2 - 1));
  const m = lDecimal - c / 2;

  let r = 0, g = 0, b = 0;
  if (hDecimal < 1/6) { r = c; g = x; b = 0; }
  else if (hDecimal < 2/6) { r = x; g = c; b = 0; }
  else if (hDecimal < 3/6) { r = 0; g = c; b = x; }
  else if (hDecimal < 4/6) { r = 0; g = x; b = c; }
  else if (hDecimal < 5/6) { r = x; g = 0; b = c; }
  else { r = c; g = 0; b = x; }

  const toHex = (n: number) => {
    const hex = Math.round((n + m) * 255).toString(16);
    return hex.length === 1 ? '0' + hex : hex;
  };

  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
};

// Função para converter hex para HSL string
const hexToHsl = (hex: string): string => {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0, s = 0;
  const l = (max + min) / 2;

  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);

    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
      case g: h = ((b - r) / d + 2) / 6; break;
      case b: h = ((r - g) / d + 4) / 6; break;
    }
  }

  return `${Math.round(h * 360)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
};

const handleTokenColorChange = (
  key: string, 
  hexColor: string, 
  tokens: Record<string, string>, 
  setTokens: (tokens: Record<string, string>) => void,
  setTokensStr: (tokensStr: string) => void
) => {
  const hslValue = hexToHsl(hexColor);
  const updatedTokens = { ...tokens, [key]: hslValue };
  setTokens(updatedTokens);
  setTokensStr(JSON.stringify(updatedTokens, null, 2));
};

export function ThemeTokensForm({ 
  tokens, 
  setTokens, 
  tokensStr, 
  setTokensStr 
}: ThemeTokensFormProps) {
  const [showTokensJson, setShowTokensJson] = useState(false);

  return (
    <div className="space-y-4">
      <div>
        <div className="flex justify-between items-center mb-2">
          <Label className="text-sm font-medium text-[#2A1F1B]">Tokens (Cores e estilos)</Label> 
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setShowTokensJson(!showTokensJson)}
            className="border-[#8B7355]/40 text-[#2A1F1B] hover:bg-[#D7B899]/10 hover:text-[#D7B899]"
          >
            {showTokensJson ? 'Editor Visual' : 'Editar JSON'}
          </Button>
        </div>

        {showTokensJson ? (
          <Textarea
            id="tokens"
            value={tokensStr}
            onChange={(e) => {
              setTokensStr(e.target.value);
              try {
                setTokens(JSON.parse(e.target.value));
              } catch (error) {
                console.warn('Erro ao parsear tokens JSON', error);
              }
            }}
            className="font-mono h-60 border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
            placeholder="JSON de tokens de estilo"
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-96 overflow-y-auto border border-[#8B7355]/30 rounded-md p-4 bg-[#FBF6F2]"> 
            {Object.entries(tokens).map(([key, value]) => {
              // Ignora se não for uma cor (como radius)
              if (key === 'radius' || typeof value !== 'string' || !value.includes('%')) {
                return (
                  <div key={key} className="flex items-center gap-2">
                    <Label className="text-sm flex-1 text-[#2A1F1B]">{key}</Label> 
                    <Input
                      value={value as string}
                      onChange={(e) => {
                        const updatedTokens = { ...tokens, [key]: e.target.value };
                        setTokens(updatedTokens);
                        setTokensStr(JSON.stringify(updatedTokens, null, 2));
                      }}
                      className="h-8 text-xs font-mono border-[#8B7355]/30 bg-white text-[#2A1F1B]"
                      placeholder="Valor do token"
                    />
                  </div>
                );
              }

              const hexColor = hslToHex(value as string);
              return (
                <div key={key} className="flex items-center gap-2">
                  <div
                    className="w-8 h-8 rounded border-2 border-[#8B7355] flex-shrink-0 cursor-pointer"
                    style={{ backgroundColor: hexColor }}
                    title={`${key}: ${value}`}
                  />
                  <Label className="text-sm flex-1 text-[#2A1F1B]">{key}</Label> 
                  <input
                    type="color"
                    value={hexColor}
                    onChange={(e) => handleTokenColorChange(key, e.target.value, tokens, setTokens, setTokensStr)}
                    className="w-12 h-8 cursor-pointer rounded border border-[#8B7355]"
                    title={`Selecionar cor para ${key}`}
                  />
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}