import { Button, Card, Skeleton, Tag } from '@arco-design/web-react';
import type { UiComponentState } from '../../types/ui';
import styles from './ClarificationCard.module.css';

type ClarificationCardProps = {
  title?: string;
  options?: string[];
  state?: UiComponentState;
  errorText?: string;
  onSelect?: (value: string) => void;
};

export function ClarificationCard({
  title = '为了让计划更可执行，我还需要 3 个信息',
  options = ['预算 300 元以内', '不吃猪肉', '目标高蛋白'],
  state = 'normal',
  errorText = '追问选项加载失败，请直接在输入框补充。',
  onSelect
}: ClarificationCardProps) {
  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`} bordered={false}>
        <Skeleton text={{ rows: 2 }} animation />
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[state]}`} bordered={false}>
      <Tag color={state === 'error' ? 'red' : 'orange'}>{state === 'error' ? '追问失败' : '需要补充'}</Tag>
      <h3>{state === 'error' ? errorText : title}</h3>
      <div className={styles.options}>
        {options.map((option) => (
          <Button disabled={state === 'disabled' || state === 'error'} onClick={() => onSelect?.(option)} key={option}>
            {option}
          </Button>
        ))}
      </div>
    </Card>
  );
}
