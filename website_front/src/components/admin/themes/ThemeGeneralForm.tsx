import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Theme } from '@/types/theme';

interface ThemeGeneralFormProps {
  theme: Theme | null;
  name: string;
  setName: (name: string) => void;
  status: Theme['status'];
  setStatus: (status: Theme['status']) => void;
  tenantId: string;
  setTenantId: (tenantId: string) => void;
}

export function ThemeGeneralForm({ 
  theme, 
  name, 
  setName, 
  status, 
  setStatus, 
  tenantId, 
  setTenantId 
}: ThemeGeneralFormProps) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="name" className="text-sm font-medium text-[#2A1F1B]">
            Nome do Tema
          </Label>
          <Input
            id="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
            placeholder="Ex: Tema Padrão"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="status" className="text-sm font-medium text-[#2A1F1B]">
            Status
          </Label>
          <Select value={status} onValueChange={(value: Theme['status']) => setStatus(value)}>
            <SelectTrigger className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]">
              <SelectValue placeholder="Selecione um status" />
            </SelectTrigger>
            <SelectContent className="bg-white border-[#8B7355]/30 text-[#2A1F1B]">
              <SelectItem value="DRAFT">Rascunho</SelectItem>
              <SelectItem value="PUBLISHED">Publicado</SelectItem>
              <SelectItem value="ARCHIVED">Arquivado</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2 md:col-span-2">
          <Label htmlFor="tenantId" className="text-sm font-medium text-[#2A1F1B]">
            Tenant
          </Label>
          <Input
            id="tenantId"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            required
            className="border-[#8B7355]/30 focus:ring-2 focus:ring-[#D7B899] focus:border-transparent bg-[#FBF6F2] text-[#2A1F1B]"
            placeholder="Ex: espresso, villa"
          />
        </div>
      </div>
    </div>
  );
}