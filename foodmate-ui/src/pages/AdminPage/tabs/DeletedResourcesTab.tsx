import { Button, Card, Table } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import styles from '../AdminPage.module.css';
import { AdminFilters, AdminOnlyNotice, OperationAuditCard } from './AdminComponents';
import { type DeletedRow, adminDeletedRows, canManage } from './AdminShared';
import type { AdminActionPayload } from './types';
import { useEffect, useState } from 'react';
import { loadAdminDashboard, restoreAdminResource } from '../../../services/adminService';

export function DeletedSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [rows, setRows] = useState(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminDeletedRows);
  useEffect(() => { if (import.meta.env.VITE_AGENT_MODE === 'real') loadAdminDashboard().then((d) => setRows(d.deleted as typeof adminDeletedRows)).catch(() => setRows([])); }, []);
  if (!canManage) return <AdminOnlyNotice title="无权访问软删除资源" />;

  const deletedColumns: TableColumnProps<DeletedRow>[] = [
    { title: '资源类型', dataIndex: 'resourceType' },
    { title: '资源 ID', dataIndex: 'resourceId' },
    { title: '归属', dataIndex: 'owner' },
    { title: '删除时间', dataIndex: 'deletedAt' },
    { title: '原因', dataIndex: 'reason' },
    {
      title: '操作',
      render: (_, record) => (
        <Button
          size="mini"
          onClick={() =>
            onAction({
              action: '恢复软删除资源',
              targetLabel: `${record.resourceType}:${record.resourceId}`,
              targetType: record.resourceType,
              targetId: record.resourceId,
              execute: async () => { await restoreAdminResource(record.resourceType, record.resourceId); },
              onApply: () => {
                setRows((current) => current.filter((item) => item.key !== record.key));
              },
            })
          }
        >
          恢复
        </Button>
      ),
    },
  ];

  return (
    <>
      <AdminFilters placeholder="resourceType / resourceId / userId" />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>软删除资源</strong>
          <Button disabled={!canManage} size="mini" color="red">
            恢复仅 admin 可用
          </Button>
        </div>
        <Table
          columns={deletedColumns}
          data={rows}
          pagination={{ pageSize: 5, total: rows.length }}
          size="small"
        />
      </Card>
      <OperationAuditCard />
    </>
  );
}
