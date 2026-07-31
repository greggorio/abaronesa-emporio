import { useEffect } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";

export default function MesaPaymentSuccessPage() {
  const { mesaSlug } = useParams();

  useEffect(() => {
    if (!mesaSlug) return;
    const storageKey = `qrGuest:${mesaSlug}`;
    try {
      localStorage.removeItem(storageKey);
    } catch {}
  }, [mesaSlug]);

  return (
    <div className="min-h-screen bg-mesa-bg text-mesa-text flex items-center justify-center px-4">
      <div className="w-full max-w-md rounded-2xl bg-white/95 p-6 shadow-lg border border-accent/10 text-center space-y-4">
        <div className="text-2xl font-semibold">Pagamento confirmado</div>
        <p className="text-sm text-muted-foreground">
          Obrigado! Seu pagamento foi recebido com sucesso.
        </p>
        <p className="text-xs text-muted-foreground">
          Você pode encerrar esta página ou voltar ao início para abrir uma nova sessão.
        </p>
        <div className="pt-2">
          <Button asChild className="w-full">
            <Link to={mesaSlug ? `/m/${mesaSlug}` : "/"}>Voltar ao início</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
