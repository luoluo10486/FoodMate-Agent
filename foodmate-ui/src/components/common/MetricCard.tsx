import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import type { UiComponentState } from '../../types/ui';
import styles from './MetricCard.module.css';

type MetricCardProps = {
  label: string;
  value: string;
  unit: string;
  tone?: string;
  state?: UiComponentState;
  errorText?: string;
};

export function MetricCard({
  label,
  value,
  unit,
  tone = 'dark',
  state = 'normal',
  errorText = '指标暂不可用',
}: MetricCardProps) {
  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`}>
        <div className="grid gap-3">
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-8 w-2/3" />
        </div>
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[state]}`}>
      <span>{label}</span>
      <strong className={styles[tone]}>
        {state === 'error' ? errorText : value}
        {state === 'error' ? null : <small>{unit}</small>}
      </strong>
    </Card>
  );
}
