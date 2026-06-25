import { Button, Card, Tag } from '@arco-design/web-react';
import styles from './ClarificationCard.module.css';

type ClarificationCardProps = {
  title?: string;
  options?: string[];
  onSelect?: (value: string) => void;
};

export function ClarificationCard({
  title = '为了让计划更可执行，我还需要 3 个信息',
  options = ['预算 300 元以内', '不吃猪肉', '目标高蛋白'],
  onSelect
}: ClarificationCardProps) {
  return (
    <Card className={styles.card} bordered={false}>
      <Tag color="orange">需要补充</Tag>
      <h3>{title}</h3>
      <div className={styles.options}>
        {options.map((option) => (
          <Button onClick={() => onSelect?.(option)} key={option}>
            {option}
          </Button>
        ))}
      </div>
    </Card>
  );
}
