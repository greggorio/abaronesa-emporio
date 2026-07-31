export const badgeClass = (status?: string) => {
  switch ((status || '').toLowerCase()) {
    case 'queued':
    case 'pending':
      return 'bg-gray-100 text-gray-700';
    case 'accepted':
      return 'bg-blue-100 text-blue-700';
    case 'preparing':
      return 'bg-amber-100 text-amber-700';
    case 'ready':
      return 'bg-emerald-100 text-emerald-700';
    case 'delivered':
      return 'bg-green-100 text-green-700';
    case 'canceled':
      return 'bg-red-100 text-red-700';
    default:
      return 'bg-gray-100 text-gray-700';
  }
};

export const statusLabel = (status?: string) => {
  switch ((status || '').toLowerCase()) {
    case 'queued':
    case 'pending':
      return 'Pendente';
    case 'accepted':
      return 'Aceito';
    case 'preparing':
      return 'Preparando';
    case 'ready':
      return 'Pronto';
    case 'delivered':
      return 'Entregue';
    case 'canceled':
      return 'Cancelado';
    default:
      return status || '';
  }
};
