import { Button, Card, Tag } from '@arco-design/web-react';
import styles from './ResultCard.module.css';

type ResultCardProps = {
  label?: string;
  title?: string;
  description?: string;
  primaryAction?: string;
  secondaryAction?: string;
  onPrimary?: () => void;
  onSecondary?: () => void;
};

export function ResultCard({
  label = '计划草案',
  title = '3 天高蛋白备餐已生成，预算预计 286 元',
  description = '计划优先复用鸡胸肉、鸡蛋、豆腐和西兰花，降低采购成本和食材浪费。当前正在校验晚餐烹饪时间和蛋白质目标。',
  primaryAction = '确认保存',
  secondaryAction = '查看购物清单',
  onPrimary,
  onSecondary
}: ResultCardProps) {
  return (
    <Card className={styles.card} bordered={false}>
      <Tag color="green">{label}</Tag>
      <h3>{title}</h3>
      <p>{description}</p>
      <div className={styles.actions}>
        <Button type="primary" onClick={onPrimary}>
          {primaryAction}
        </Button>
        <Button onClick={onSecondary}>{secondaryAction}</Button>
      </div>
    </Card>
  );
}
