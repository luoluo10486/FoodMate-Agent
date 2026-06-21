import { Button, Card, Tag } from '@arco-design/web-react';
import styles from './ResultCard.module.css';

export function ResultCard() {
  return (
    <Card className={styles.card} bordered={false}>
      <Tag color="green">计划草案</Tag>
      <h3>3 天高蛋白备餐已生成，预算预计 286 元</h3>
      <p>计划优先复用鸡胸肉、鸡蛋、豆腐和西兰花，降低采购成本和食材浪费。当前正在校验晚餐烹饪时间和蛋白质目标。</p>
      <div className={styles.actions}>
        <Button type="primary">确认保存</Button>
        <Button>查看购物清单</Button>
      </div>
    </Card>
  );
}
