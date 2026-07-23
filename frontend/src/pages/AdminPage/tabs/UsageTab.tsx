import { Card, Table, Tag } from '@arco-design/web-react';
import { useEffect, useState } from 'react';
import styles from '../AdminPage.module.css';
import { AdminFilters, MiniStat } from './AdminComponents';
import { adminModelUsageRows, modelUsageColumns } from './AdminShared';
import { loadAdminDashboard } from '../../../services/adminService';

export function UsageSection() {
  const [rows, setRows] = useState(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminModelUsageRows);
  useEffect(() => { if (import.meta.env.VITE_AGENT_MODE === 'real') loadAdminDashboard().then((d) => setRows(d.usage as typeof adminModelUsageRows)).catch(() => setRows([])); }, []);
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
          data={rows}
          pagination={{ pageSize: 5, total: rows.length }}
          size="small"
        />
      </Card>
    </>
  );
}
