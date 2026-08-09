import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Copy, Search } from 'lucide-react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input as ShadcnInput } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button, Card, Table, type TableColumnProps } from './AdminPrimitives';
import styles from '../AdminPage.module.css';
import { AdminOnlyNotice } from './AdminComponents';
import { type DeletedRow, adminDeletedRows, canManage } from './AdminShared';
import type { AdminActionPayload } from './types';
import { loadAdminDashboard, restoreAdminResource } from '../../../services/adminService';

const deletedTotal = 19;
const pageSize = 4;

const resourceTypeLabels: Record<string, string> = {
  document: '文档',
  knowledge_document: '文档',
  session: '会话',
  plan: '计划',
  meal_plan: '计划',
  record: '记录',
  food_log: '记录',
};

const resourceTypeKeys: Record<string, string> = {
  文档: 'document',
  会话: 'session',
  计划: 'plan',
  记录: 'record',
};

const resourceTypeTones: Record<string, string> = {
  document: styles.deletedTypeDocument,
  knowledge_document: styles.deletedTypeDocument,
  session: styles.deletedTypeSession,
  plan: styles.deletedTypePlan,
  meal_plan: styles.deletedTypePlan,
  record: styles.deletedTypeRecord,
  food_log: styles.deletedTypeRecord,
};

const deletedTypeOptions = [
  { value: 'all', label: '全部' },
  { value: 'document', label: '文档' },
  { value: 'session', label: '会话' },
  { value: 'plan', label: '计划' },
  { value: 'record', label: '记录' },
];

function isWithinArchiveWindow(value: string, days: number) {
  const timestamp = Date.parse(value.replace(' ', 'T'));
  if (Number.isNaN(timestamp)) return true;
  const referenceTime = import.meta.env.VITE_AGENT_MODE === 'real' ? Date.now() : Date.parse('2024-03-15T00:00:00');
  const age = referenceTime - timestamp;
  return age >= 0 && age <= days * 24 * 60 * 60 * 1000;
}

const deletedByOptions = [
  { value: 'all', label: '全部' },
  { value: 'anddy_operator_9', label: 'anddy_operator_9' },
  { value: 'system_cleanup', label: 'system_cleanup' },
  { value: 'user_8892110', label: 'user_8892110' },
];

function resourceTypeLabel(value: string) {
  return resourceTypeLabels[value] ?? value;
}

function resourceTypeKey(value: string) {
  return resourceTypeKeys[resourceTypeLabel(value)] ?? value;
}

function copyResourceId(resourceId: string) {
  if (navigator.clipboard) void navigator.clipboard.writeText(resourceId);
}

function DeletedFilterSelect({
  label,
  value,
  options,
  onChange,
  ariaLabel,
  className,
}: {
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
  ariaLabel: string;
  className: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={`${styles.deletedFilter} ${className}`} aria-label={ariaLabel}>
        <span className={styles.deletedFilterLabel}>{label}:</span>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function restorablePill(restorable: boolean) {
  return (
    <span
      className={`${styles.deletedRestorable} ${restorable ? styles.deletedRestorableYes : styles.deletedRestorableNo}`}
    >
      {restorable ? '是' : '否'}
    </span>
  );
}

export function DeletedSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [rows, setRows] = useState<DeletedRow[]>(
    import.meta.env.VITE_AGENT_MODE === 'real' ? [] : (adminDeletedRows as DeletedRow[]),
  );
  const [resourceFilter, setResourceFilter] = useState('all');
  const [deletedByFilter, setDeletedByFilter] = useState('all');
  const [timeFilter, setTimeFilter] = useState('30d');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [selectedRow, setSelectedRow] = useState<DeletedRow>();

  useEffect(() => {
    if (import.meta.env.VITE_AGENT_MODE !== 'real') return;
    loadAdminDashboard()
      .then((dashboard) => setRows(dashboard.deleted))
      .catch(() => setRows([]));
  }, []);

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return rows.filter((row) => {
      const matchesType = resourceFilter === 'all' || resourceTypeKey(row.resourceType) === resourceFilter;
      const matchesDeletedBy = deletedByFilter === 'all' || row.deletedBy === deletedByFilter;
      const matchesTime = timeFilter === 'all' || isWithinArchiveWindow(row.deletedAt, 30);
      const searchable = `${row.resourceId} ${row.summary} ${row.owner} ${row.deletedBy}`.toLowerCase();
      return (
        matchesType && matchesDeletedBy && matchesTime && (!normalizedQuery || searchable.includes(normalizedQuery))
      );
    });
  }, [deletedByFilter, query, resourceFilter, rows, timeFilter]);

  const hasFilter =
    resourceFilter !== 'all' || deletedByFilter !== 'all' || timeFilter !== '30d' || Boolean(query.trim());
  const totalResults = import.meta.env.VITE_AGENT_MODE === 'real' || hasFilter ? filteredRows.length : deletedTotal;
  const pageCount = Math.max(1, Math.ceil(totalResults / pageSize));
  const visibleRows = filteredRows.slice((page - 1) * pageSize, page * pageSize);
  const rangeStart = totalResults === 0 ? 0 : (page - 1) * pageSize + 1;
  const rangeEnd = Math.min(page * pageSize, totalResults);

  const setFilter = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(1);
  };

  const deletedColumns: TableColumnProps<DeletedRow>[] = [
    {
      title: '资源ID',
      dataIndex: 'resourceId',
      render: (value) => {
        const resourceId = String(value);
        return (
          <span className={styles.deletedIdCell}>
            <strong>{resourceId}</strong>
            <button
              className={styles.deletedCopyButton}
              type="button"
              aria-label={`复制 ${resourceId}`}
              onClick={() => copyResourceId(resourceId)}
              title={`复制 ${resourceId}`}
            >
              <Copy aria-hidden="true" />
            </button>
          </span>
        );
      },
    },
    {
      title: '类型',
      dataIndex: 'resourceType',
      render: (value) => {
        const type = String(value);
        return (
          <span className={`${styles.deletedTypePill} ${resourceTypeTones[type] ?? ''}`}>
            {resourceTypeLabel(type)}
          </span>
        );
      },
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      render: (value) => <span className={styles.deletedSummary}>{String(value)}</span>,
    },
    {
      title: '原所有者',
      dataIndex: 'owner',
      render: (value) => <span className={styles.deletedMonoCell}>{String(value)}</span>,
    },
    {
      title: '删除者',
      dataIndex: 'deletedBy',
      render: (value) => <span className={styles.deletedMonoCell}>{String(value)}</span>,
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      render: (value) => <span className={styles.deletedDateCell}>{String(value)}</span>,
    },
    { title: '可恢复', dataIndex: 'restorable', render: (value) => restorablePill(Boolean(value)) },
    {
      title: '操作',
      render: (_, record) => (
        <div className={styles.deletedActions}>
          <Button className={styles.deletedDetailButton} size="small" onClick={() => setSelectedRow(record)}>
            查看详情
          </Button>
          <Button
            className={styles.deletedRestoreButton}
            size="small"
            color="red"
            disabled={!record.restorable || !canManage}
            onClick={() =>
              onAction({
                action: '恢复软删除资源',
                targetLabel: `${resourceTypeLabel(record.resourceType)}:${record.resourceId}`,
                targetType: record.resourceType,
                targetId: record.resourceId,
                execute: async () => {
                  await restoreAdminResource(record.resourceType, record.resourceId);
                },
                onApply: () => setRows((current) => current.filter((item) => item.key !== record.key)),
              })
            }
          >
            恢复
          </Button>
        </div>
      ),
    },
  ];

  if (!canManage) return <AdminOnlyNotice title="无权访问软删除资源" />;

  return (
    <>
      <section className={styles.deletedFilters} aria-label="删除资源筛选">
        <div className={styles.deletedFilterGroup}>
          <DeletedFilterSelect
            label="资源类型"
            value={resourceFilter}
            options={deletedTypeOptions}
            onChange={setFilter(setResourceFilter)}
            ariaLabel="资源类型筛选"
            className={styles.deletedFilterType}
          />
          <DeletedFilterSelect
            label="删除者"
            value={deletedByFilter}
            options={deletedByOptions}
            onChange={setFilter(setDeletedByFilter)}
            ariaLabel="删除者筛选"
            className={styles.deletedFilterActor}
          />
          <DeletedFilterSelect
            label="删除时间"
            value={timeFilter}
            options={[
              { value: '30d', label: '最近30天' },
              { value: 'all', label: '全部' },
            ]}
            onChange={setFilter(setTimeFilter)}
            ariaLabel="删除时间筛选"
            className={styles.deletedFilterTime}
          />
        </div>
        <label className={styles.deletedSearch}>
          <Search aria-hidden="true" />
          <ShadcnInput
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
            placeholder="搜索存档ID, 归档所有者..."
            aria-label="搜索存档ID或归档所有者"
          />
        </label>
      </section>

      <section className={styles.deletedNotice} aria-label="存档数据保护规范与合规通告" role="note">
        <div className={styles.deletedNoticeTitle}>
          <AlertTriangle aria-hidden="true" />
          <strong>存档数据保护规范与合规通告</strong>
        </div>
        <p>
          根据系统数据隐私政策，所有已被“彻底删除”的用户档案均首先移至此存档区存储，自删除之日起进行为期90天的合规性审计保留。在此期间，通过管理员审核后可随时申请“一键恢复”，或等待到期后系统安全脚本自动将其全盘清除，在此页面不设“永久删除”操作项，保障追踪溯源安全。
        </p>
      </section>

      <Card className={styles.deletedTableCard} bordered={false}>
        <Table
          className={styles.deletedTableScroll}
          tableClassName={styles.deletedTable}
          columns={deletedColumns}
          data={visibleRows}
          pagination={false}
          size="small"
        />
      </Card>

      <section className={styles.deletedPagination} aria-label="删除资源分页">
        <span>
          显示第 {rangeStart} 到 {rangeEnd} 条，共 {totalResults} 条结果
        </span>
        <div className={styles.deletedPageButtons}>
          <Button
            className={styles.deletedPageButton}
            disabled={page === 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            上一页
          </Button>
          {Array.from({ length: pageCount }, (_, index) => index + 1).map((value) => (
            <Button
              className={`${styles.deletedPageButton} ${page === value ? styles.deletedPageActive : ''}`}
              key={value}
              onClick={() => setPage(value)}
            >
              {value}
            </Button>
          ))}
          <Button
            className={styles.deletedPageButton}
            disabled={page === pageCount}
            onClick={() => setPage((current) => Math.min(pageCount, current + 1))}
          >
            下一页
          </Button>
        </div>
      </section>

      <Dialog open={Boolean(selectedRow)} onOpenChange={(open) => !open && setSelectedRow(undefined)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{selectedRow?.resourceId} 详情</DialogTitle>
            <DialogDescription>删除资源详情只读展示，恢复操作仍需经过 admin 二次确认并记录审计。</DialogDescription>
          </DialogHeader>
          {selectedRow ? (
            <div className={styles.deletedDetailGrid}>
              <span>资源类型</span>
              <strong>{resourceTypeLabel(selectedRow.resourceType)}</strong>
              <span>资源 ID</span>
              <strong>{selectedRow.resourceId}</strong>
              <span>摘要</span>
              <strong>{selectedRow.summary}</strong>
              <span>原所有者</span>
              <strong>{selectedRow.owner}</strong>
              <span>删除者</span>
              <strong>{selectedRow.deletedBy}</strong>
              <span>删除时间</span>
              <strong>{selectedRow.deletedAt}</strong>
              <span>可恢复</span>
              <strong>{selectedRow.restorable ? '是' : '否'}</strong>
            </div>
          ) : null}
          <DialogFooter>
            <Button onClick={() => setSelectedRow(undefined)}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
