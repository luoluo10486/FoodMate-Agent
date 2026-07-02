import { Button, Card, Table } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import styles from '../AdminPage.module.css';
import {
  AdminFilters,
  AdminOnlyNotice,
  OperationAuditCard,
  type DeletedRow,
  adminDeletedRows,
  canManage,
} from './AdminShared';
import type { AdminActionPayload } from './types';

export function DeletedSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
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
              onApply: () => {
                const rowIndex = adminDeletedRows.findIndex((item) => item.key === record.key);
                if (rowIndex >= 0) adminDeletedRows.splice(rowIndex, 1);
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
          data={adminDeletedRows}
          pagination={{ pageSize: 5, total: adminDeletedRows.length }}
          size="small"
        />
      </Card>
      <OperationAuditCard />
    </>
  );
}
