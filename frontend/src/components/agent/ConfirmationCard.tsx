import { Button, Card, Descriptions, Skeleton, Tag } from '@arco-design/web-react';
import type { UiComponentState } from '../../types/ui';
import styles from './ConfirmationCard.module.css';

type ConfirmationCardProps = {
  title?: string;
  helperText?: string;
  data?: Array<{ label: string; value: string }>;
  state?: UiComponentState;
  errorText?: string;
  onConfirm?: () => void;
  onEdit?: () => void;
  onCancel?: () => void;
  onRetry?: () => void;
  retryLabel?: string;
};

export function ConfirmationCard({
  title = '请确认要写入的内容',
  helperText = '确认前不会写入任何真实数据。',
  data = [
    { label: '餐型', value: '午餐' },
    { label: '食物', value: '鸡胸肉 200g、米饭 150g、西兰花 120g' },
    { label: '估算', value: '约 620 kcal · 蛋白质 54g' },
  ],
  state = 'normal',
  errorText = '确认数据加载失败，请重新估算。',
  onConfirm,
  onEdit,
  onCancel,
  onRetry,
  retryLabel = '重新确认',
}: ConfirmationCardProps) {
  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`} bordered={false}>
        <Skeleton text={{ rows: 3 }} animation />
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[state]}`} bordered={false}>
      <Tag color="red">{state === 'error' ? '确认失败' : '写入前确认'}</Tag>
      <h3 className={styles.title}>{title}</h3>
      {state === 'error' ? <p className={styles.errorText}>{errorText}</p> : <Descriptions column={1} data={data} />}
      {state !== 'error' ? <p className={styles.helperText}>{helperText}</p> : null}
      <div className={styles.actions}>
        <Button disabled={state === 'disabled' || state === 'error'} type="primary" onClick={onConfirm}>
          确认保存
        </Button>
        <Button disabled={state === 'disabled'} onClick={onEdit}>
          修改
        </Button>
        <Button disabled={state === 'disabled'} status="danger" onClick={onCancel}>
          取消
        </Button>
        {onRetry ? (
          <Button disabled={state === 'disabled'} onClick={onRetry}>
            {retryLabel}
          </Button>
        ) : null}
      </div>
    </Card>
  );
}
