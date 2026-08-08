import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import type { UiComponentState } from '../../types/ui';
import styles from './ResultCard.module.css';

type ResultCardProps = {
  label?: string;
  title?: string;
  description?: string;
  primaryAction?: string;
  secondaryAction?: string;
  state?: UiComponentState;
  errorText?: string;
  onPrimary?: () => void;
  onSecondary?: () => void;
};

export function ResultCard({
  label = '计划草案',
  title = '3 天高蛋白备餐已生成，预算预计 286 元',
  description = '计划优先复用鸡胸肉、鸡蛋、豆腐和西兰花，降低采购成本和食材浪费。当前正在校验晚餐烹饪时间和蛋白质目标。',
  primaryAction = '确认保存',
  secondaryAction = '查看购物清单',
  state = 'normal',
  errorText = '结果生成失败，请重试。',
  onPrimary,
  onSecondary,
}: ResultCardProps) {
  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`}>
        <div className="grid gap-3">
          <Skeleton className="h-4 w-1/4" />
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-10 w-32" />
        </div>
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[state]}`}>
      <Badge variant={state === 'error' ? 'destructive' : 'default'}>{state === 'error' ? '执行失败' : label}</Badge>
      <h3>{state === 'error' ? '结果暂不可用' : title}</h3>
      <p>{state === 'error' ? errorText : description}</p>
      <div className={styles.actions}>
        <Button
          disabled={state === 'disabled'}
          variant={state === 'error' ? 'destructive' : 'default'}
          onClick={onPrimary}
        >
          {state === 'error' ? '重试' : primaryAction}
        </Button>
        <Button variant="outline" disabled={state === 'disabled'} onClick={onSecondary}>
          {secondaryAction}
        </Button>
      </div>
    </Card>
  );
}
