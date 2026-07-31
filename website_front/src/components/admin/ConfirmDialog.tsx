import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  title: string;
  description: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'default' | 'destructive';
  theme?: 'viking' | 'forest';
}

export default function ConfirmDialog({
  open,
  onOpenChange,
  onConfirm,
  title,
  description,
  confirmText = 'Confirmar',
  cancelText = 'Cancelar',
  variant = 'default',
  theme = 'forest',
}: ConfirmDialogProps) {
  const handleConfirm = () => {
    onConfirm();
    onOpenChange(false);
  };

  const themeClasses = {
    container: 'bg-card border-border/30',
    title: 'text-foreground text-xl font-display tracking-wider',
    description: 'text-foreground/70 text-base',
    cancel: 'bg-secondary border-border/40 text-secondary-foreground hover:bg-primary/10 font-medium',
    action: variant === 'destructive'
      ? 'bg-destructive hover:bg-destructive/90 text-destructive-foreground border-none font-medium'
      : 'bg-primary hover:bg-primary/90 text-primary-foreground border-none font-medium',
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className={themeClasses.container} data-theme={theme}>
        <AlertDialogHeader>
          <AlertDialogTitle className={themeClasses.title}>
            {title}
          </AlertDialogTitle>
          <AlertDialogDescription className={themeClasses.description}>
            {description}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel
            className={themeClasses.cancel}
          >
            {cancelText}
          </AlertDialogCancel>
          <AlertDialogAction
            onClick={handleConfirm}
            className={themeClasses.action}
          >
            {confirmText}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
