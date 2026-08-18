import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import styles from '../AdminPage.module.css';
import { AdminFilters, MiniStat } from './AdminComponents';
import { adminModelUsageRows, modelUsageColumns } from './AdminShared';
import { loadAdminDashboard } from '../../../services/adminService';

export function UsageSection() {
  const [rows, setRows] = useState(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminModelUsageRows);
  useEffect(() => {
    if (import.meta.env.VITE_AGENT_MODE === 'real')
      loadAdminDashboard()
        .then((d) => setRows(d.usage as typeof adminModelUsageRows))
        .catch(() => setRows([]));
  }, []);
  return (
    <>
      <section className={styles.sectionCards}>
        <MiniStat label="今日成本" value="¥86.4" hint="+8%" tone="orange" />
        <MiniStat label="Tokens" value="514k" hint="近 24h" />
        <MiniStat label="Fallback" value="3" hint="供应商切换" tone="danger" />
      </section>
      <AdminFilters placeholder="provider / model / scene" />
      <Card className={styles.wideCard}>
        <div className={styles.cardHead}>
          <strong>模型调用明细</strong>
          <Badge variant="outline">成本和延迟治理</Badge>
        </div>
        <DataTable columns={modelUsageColumns} data={rows} />
      </Card>
    </>
  );
}
