import { Card } from '@arco-design/web-react';
import styles from './MetricCard.module.css';

type MetricCardProps = {
  label: string;
  value: string;
  unit: string;
  tone?: string;
};

export function MetricCard({ label, value, unit, tone = 'dark' }: MetricCardProps) {
  return (
    <Card className={styles.card} bordered={false}>
      <span>{label}</span>
      <strong className={styles[tone]}>
        {value}
        <small>{unit}</small>
      </strong>
    </Card>
  );
}
