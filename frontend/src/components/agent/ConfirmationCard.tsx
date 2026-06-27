import { Button, Card, Descriptions, Skeleton, Tag } from '@arco-design/web-react';
import type { UiComponentState } from '../../types/ui';
import styles from './ConfirmationCard.module.css';

type ConfirmationCardProps = {
  data?: Array<{ label: string; value: string }>;
  state?: UiComponentState;
  errorText?: string;
  onConfirm?: () => void;
  onEdit?: () => void;
  onCancel?: () => void;
};

export function ConfirmationCard({
  data = [
    { label: '餐型', value: '午餐' },
    { label: '食物', value: '鸡胸肉 200g、米饭 150g、西兰花 120g' },
    { label: '估算', value: '约 620 kcal · 蛋白质 54g' }
  ],
  state = 'normal',
  errorText = '确认数据加载失败，请重新估算。',
  onConfirm,
  onEdit,
  onCancel
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
      {state === 'error' ? <p className={styles.errorText}>{errorText}</p> : <Descriptions column={1} data={data} />}
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
      </div>
    </Card>
  );
}
