import { Button, Card, Descriptions, Tag } from '@arco-design/web-react';
import styles from './ConfirmationCard.module.css';

type ConfirmationCardProps = {
  data?: Array<{ label: string; value: string }>;
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
  onConfirm,
  onEdit,
  onCancel
}: ConfirmationCardProps) {
  return (
    <Card className={styles.card} bordered={false}>
      <Tag color="red">写入前确认</Tag>
      <Descriptions column={1} data={data} />
      <div className={styles.actions}>
        <Button type="primary" onClick={onConfirm}>
          确认保存
        </Button>
        <Button onClick={onEdit}>修改</Button>
        <Button status="danger" onClick={onCancel}>
          取消
        </Button>
      </div>
    </Card>
  );
}
