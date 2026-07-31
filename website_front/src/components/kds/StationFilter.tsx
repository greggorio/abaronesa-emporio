import { Estacao } from '@/types/kds';
import { Button } from '@/components/ui/button';

interface StationFilterProps {
  currentStation: Estacao | 'all';
  onStationChange: (station: Estacao | 'all') => void;
  compact?: boolean;
  counts?: Record<'all' | 'kitchen' | 'bar', number>;
}

export function StationFilter({ currentStation, onStationChange, compact, counts }: StationFilterProps) {
  const stations: { value: Estacao | 'all'; label: string }[] = [
    { value: 'all', label: 'Todos' },
    { value: 'kitchen', label: 'Cozinha' },
    { value: 'bar', label: 'Bar' },
  ];

  return (
    <div className="flex gap-2 flex-wrap">
      {stations.map((station) => {
        const selected = currentStation === station.value;
        const count = counts?.[station.value] ?? 0;

        return (
          <Button
            key={station.value}
            size={compact ? 'sm' : 'default'}
            variant={'outline'}
            onClick={() => onStationChange(station.value)}
            className={`${compact ? 'min-w-[80px]' : 'min-w-[100px]'} font-medium ${
              selected
                ? 'bg-accent text-accent-foreground border-accent shadow-sm'
                : 'border-accent text-[hsl(var(--accent))] bg-[hsl(var(--accent)/0.08)] hover:bg-[hsl(var(--accent)/0.15)]'
            }`}
          >
            <span>{station.label}</span>
            {counts && (
              <span className={`ml-2 inline-flex items-center justify-center min-w-[18px] h-[18px] text-xs rounded-full font-semibold ${
                selected ? 'bg-accent-foreground/30 text-accent-foreground' : 'bg-[hsl(var(--accent)/0.15)] text-[hsl(var(--accent))]'
              }`}>
                {count}
              </span>
            )}
          </Button>
        );
      })}
    </div>
  );
}
