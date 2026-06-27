import { Card, Skeleton } from '@arco-design/web-react';
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

export function MetricCard({ label, value, unit, tone = 'dark', state = 'normal', errorText = '指标暂不可用' }: MetricCardProps) {
  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`} bordered={false}>
        <Skeleton text={{ rows: 2 }} animation />
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[state]}`} bordered={false}>
      <span>{label}</span>
      <strong className={styles[tone]}>
        {state === 'error' ? errorText : value}
        {state === 'error' ? null : <small>{unit}</small>}
      </strong>
    </Card>
  );
}
