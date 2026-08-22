import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import styles from '../AdminPage.module.css';
import { AdminFilters, MiniStat } from './AdminComponents';
import { adminModelUsageRows, modelUsageColumns } from './AdminShared';
import { ModelGovernanceSection } from './ModelGovernanceTab';
import type { AdminActionPayload } from './types';

export function UsageSection({
  onAction,
  refreshNonce,
}: {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce: number;
}) {
  if (import.meta.env.VITE_AGENT_MODE === 'real') {
    return <ModelGovernanceSection onAction={onAction} refreshNonce={refreshNonce} />;
  }

  const rows = adminModelUsageRows;
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
