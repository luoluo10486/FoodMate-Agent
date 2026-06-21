import { Button, Card, Tag } from '@arco-design/web-react';
import styles from './ClarificationCard.module.css';

export function ClarificationCard() {
  return (
    <Card className={styles.card} bordered={false}>
      <Tag color="orange">需要补充</Tag>
      <h3>为了让计划更可执行，我还需要 3 个信息</h3>
      <div className={styles.options}>
        <Button>预算 300 元以内</Button>
        <Button>不吃猪肉</Button>
        <Button>目标高蛋白</Button>
      </div>
    </Card>
  );
}
