import { Utensils } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';

interface MenuActionProps {
  mesaSlug?: string;
}

export default function MenuAction({ mesaSlug }: MenuActionProps) {
  const navigate = useNavigate();
  const target = mesaSlug ? `/m/${mesaSlug}` : '/';
  if (!mesaSlug) {
    return null;
  }

  return (
    <Button 
      className="w-full h-12 text-base bg-accent hover:bg-accent/90 text-mesa-text shadow-md flex items-center justify-center gap-2"
      onClick={() => navigate(target)}
    >
      <Utensils className="w-5 h-5" />
      Voltar para o Cardápio Digital
    </Button>
  );
}
