import { Card, Table, Tag } from '@arco-design/web-react';
import styles from '../AdminPage.module.css';
import { AdminFilters, MiniStat, adminModelUsageRows, modelUsageColumns } from './AdminShared';

export function UsageSection() {
  return (
    <>
      <section className={styles.sectionCards}>
        <MiniStat label="今日成本" value="¥86.4" hint="+8%" tone="orange" />
        <MiniStat label="Tokens" value="514k" hint="近 24h" />
        <MiniStat label="Fallback" value="3" hint="供应商切换" tone="danger" />
      </section>
      <AdminFilters placeholder="provider / model / scene" />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>模型调用明细</strong>
          <Tag color="arcoblue">成本和延迟治理</Tag>
        </div>
        <Table
          columns={modelUsageColumns}
          data={adminModelUsageRows}
          pagination={{ pageSize: 5, total: adminModelUsageRows.length }}
          size="small"
        />
      </Card>
    </>
  );
}
