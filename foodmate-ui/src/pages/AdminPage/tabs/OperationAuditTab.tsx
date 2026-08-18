import { useEffect, useMemo, useState } from 'react';
import { Eye, Search } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DataTable, type TableColumnProps } from '@/components/ui/data-table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input as ShadcnInput } from '@/components/ui/input';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { AdminOnlyNotice } from './AdminComponents';
import { adminOperationAuditRows, canViewAudit, statusTag } from './AdminShared';
import { loadAdminDashboard } from '../../../services/adminService';
import styles from '../AdminPage.module.css';

type AuditSource = {
  key: string;
  operator_id: string;
  operator: string;
  action: string;
  target_type: string;
  target_id: string;
  result: string;
  request_id: string;
  trace_id: string;
  created_at?: string;
  createdAt?: string;
  request_summary?: string;
  requestSummary?: string;
  before_state?: string;
  beforeState?: string;
  after_state?: string;
  afterState?: string;
  error_code?: string;
  errorCode?: string;
  client_info?: string;
  clientInfo?: string;
};

type AuditRecord = {
  key: string;
  operatorId: string;
  operator: string;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  requestId: string;
  traceId: string;
  createdAt: string;
  requestSummary: string;
  beforeState: string;
  afterState: string;
  errorCode: string;
  clientInfo: string;
};

const pageSize = 8;

function normalizeAuditRow(row: AuditSource): AuditRecord {
  return {
    key: row.key,
    operatorId: row.operator_id || '-',
    operator: row.operator || '-',
    action: row.action || '-',
    targetType: row.target_type || '-',
    targetId: row.target_id || '-',
    result: row.result || '-',
    requestId: row.request_id || '-',
    traceId: row.trace_id || '-',
    createdAt: row.createdAt || row.created_at || '-',
    requestSummary:
      row.requestSummary || row.request_summary || `${row.action || '管理操作'} ${row.target_id || ''}`.trim(),
    beforeState: row.beforeState || row.before_state || '-',
    afterState: row.afterState || row.after_state || '-',
    errorCode: row.errorCode || row.error_code || '-',
    clientInfo: row.clientInfo || row.client_info || '-',
  };
}

function withinTime(createdAt: string, filter: string) {
  if (filter === 'all' || createdAt === '-') return true;
  const timestamp = Date.parse(createdAt.replace(' ', 'T'));
  if (Number.isNaN(timestamp)) return true;
  const days = filter === '24h' ? 1 : filter === '7d' ? 7 : 30;
  return Date.now() - timestamp <= days * 24 * 60 * 60 * 1000;
}

function AuditFilter({
  label,
  ariaLabel,
  value,
  options,
  onChange,
}: {
  label: string;
  ariaLabel: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={styles.auditFilter} aria-label={ariaLabel}>
        <span className={styles.auditFilterLabel}>{label}:</span>
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

function AuditDetail({ row, onClose }: { row: AuditRecord; onClose: () => void }) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={styles.auditDetailDialog} aria-describedby="operation-audit-detail-description">
        <DialogHeader>
          <DialogTitle>操作审计详情</DialogTitle>
          <DialogDescription id="operation-audit-detail-description">
            只读查看请求链路、状态变化和客户端摘要。
          </DialogDescription>
        </DialogHeader>
        <div className={styles.auditDetailStatus}>
          <Badge variant={row.result === 'success' ? 'default' : row.result === 'failed' ? 'destructive' : 'warning'}>
            {row.result}
          </Badge>
          <code>{row.requestId}</code>
        </div>
        <dl className={styles.auditDetailGrid}>
          <div>
            <dt>动作</dt>
            <dd>{row.action}</dd>
          </div>
          <div>
            <dt>操作者</dt>
            <dd>
              {row.operator} / {row.operatorId}
            </dd>
          </div>
          <div>
            <dt>目标</dt>
            <dd>
              {row.targetType}:{row.targetId}
            </dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{row.createdAt}</dd>
          </div>
          <div>
            <dt>请求摘要</dt>
            <dd>{row.requestSummary}</dd>
          </div>
          <div>
            <dt>前置状态</dt>
            <dd>{row.beforeState}</dd>
          </div>
          <div>
            <dt>后置状态</dt>
            <dd>{row.afterState}</dd>
          </div>
          <div>
            <dt>错误码</dt>
            <dd>{row.errorCode}</dd>
          </div>
          <div>
            <dt>trace_id</dt>
            <dd>{row.traceId}</dd>
          </div>
          <div>
            <dt>客户端</dt>
            <dd>{row.clientInfo}</dd>
          </div>
        </dl>
      </DialogContent>
    </Dialog>
  );
}

export function OperationAuditSection({ refreshNonce = 0 }: { refreshNonce?: number }) {
  const isRealMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const mockRows = (adminOperationAuditRows as AuditSource[]).map(normalizeAuditRow);
  const [realRows, setRealRows] = useState<AuditRecord[]>([]);
  const [loadError, setLoadError] = useState('');
  const [actionFilter, setActionFilter] = useState('all');
  const [targetFilter, setTargetFilter] = useState('all');
  const [resultFilter, setResultFilter] = useState('all');
  const [timeFilter, setTimeFilter] = useState('all');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [selectedRow, setSelectedRow] = useState<AuditRecord>();

  useEffect(() => {
    if (!isRealMode) return;
    loadAdminDashboard()
      .then((dashboard) => {
        setLoadError('');
        setRealRows((dashboard.operation_audits as AuditSource[]).map(normalizeAuditRow));
      })
      .catch((error) => {
        setRealRows([]);
        setLoadError(error instanceof Error ? error.message : '操作审计加载失败');
      });
  }, [isRealMode, refreshNonce]);

  const rows = isRealMode ? realRows : mockRows;

  const actionOptions = useMemo(
    () => [
      { value: 'all', label: '全部动作' },
      ...Array.from(new Set(rows.map((row) => row.action))).map((value) => ({ value, label: value })),
    ],
    [rows],
  );
  const targetOptions = useMemo(
    () => [
      { value: 'all', label: '全部目标' },
      ...Array.from(new Set(rows.map((row) => row.targetType))).map((value) => ({ value, label: value })),
    ],
    [rows],
  );

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return rows.filter((row) => {
      const searchable = [
        row.operatorId,
        row.operator,
        row.action,
        row.targetType,
        row.targetId,
        row.result,
        row.requestId,
        row.traceId,
        row.errorCode,
      ]
        .join(' ')
        .toLowerCase();
      return (
        (actionFilter === 'all' || row.action === actionFilter) &&
        (targetFilter === 'all' || row.targetType === targetFilter) &&
        (resultFilter === 'all' || row.result === resultFilter) &&
        withinTime(row.createdAt, timeFilter) &&
        (!normalizedQuery || searchable.includes(normalizedQuery))
      );
    });
  }, [actionFilter, query, resultFilter, rows, targetFilter, timeFilter]);

  const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const safePage = Math.min(page, pageCount);
  const visibleRows = filteredRows.slice((safePage - 1) * pageSize, safePage * pageSize);
  const rangeStart = filteredRows.length === 0 ? 0 : (safePage - 1) * pageSize + 1;
  const rangeEnd = Math.min(safePage * pageSize, filteredRows.length);

  const resetPage = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(1);
  };

  const columns: TableColumnProps<AuditRecord>[] = [
    { title: '动作', dataIndex: 'action' },
    {
      title: '目标',
      render: (_, row) => (
        <span className={styles.auditTargetCell}>
          <strong>{row.targetType}</strong>
          <code>{row.targetId}</code>
        </span>
      ),
    },
    { title: '操作者', dataIndex: 'operator' },
    { title: '结果', dataIndex: 'result', render: (_, row) => statusTag(row.result) },
    { title: '时间', dataIndex: 'createdAt' },
    { title: 'request_id', dataIndex: 'requestId' },
    { title: 'trace_id', dataIndex: 'traceId' },
    {
      title: '详情',
      render: (_, row) => (
        <Button variant="outline" size="sm" onClick={() => setSelectedRow(row)}>
          <Eye aria-hidden="true" />
          查看详情
        </Button>
      ),
    },
  ];

  if (!canViewAudit) return <AdminOnlyNotice title="无权访问操作审计" />;

  return (
    <>
      {loadError ? (
        <div className={styles.auditError} role="alert">
          {loadError}
        </div>
      ) : null}
      <section className={styles.auditFilters} aria-label="操作审计筛选">
        <AuditFilter
          label="动作"
          ariaLabel="审计动作筛选"
          value={actionFilter}
          options={actionOptions}
          onChange={resetPage(setActionFilter)}
        />
        <AuditFilter
          label="目标"
          ariaLabel="审计目标筛选"
          value={targetFilter}
          options={targetOptions}
          onChange={resetPage(setTargetFilter)}
        />
        <AuditFilter
          label="结果"
          ariaLabel="审计结果筛选"
          value={resultFilter}
          options={[
            { value: 'all', label: '全部结果' },
            { value: 'success', label: 'success' },
            { value: 'failed', label: 'failed' },
            { value: 'pending', label: 'pending' },
          ]}
          onChange={resetPage(setResultFilter)}
        />
        <AuditFilter
          label="时间"
          ariaLabel="审计时间筛选"
          value={timeFilter}
          options={[
            { value: 'all', label: '全部时间' },
            { value: '24h', label: '近 24 小时' },
            { value: '7d', label: '近 7 天' },
            { value: '30d', label: '近 30 天' },
          ]}
          onChange={resetPage(setTimeFilter)}
        />
        <label className={styles.auditSearch}>
          <Search aria-hidden="true" />
          <ShadcnInput
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
            placeholder="搜索 operator、target、request_id..."
            aria-label="搜索操作审计"
          />
        </label>
      </section>

      <Card className={styles.auditTableCard}>
        <div className={styles.auditTableHeader}>
          <div>
            <strong>操作审计记录</strong>
            <p>记录只读展示，包含请求摘要、前后状态和链路标识。</p>
          </div>
          <Badge variant="outline">仅 admin / superadmin</Badge>
        </div>
        {visibleRows.length ? (
          <DataTable
            className={styles.auditTableScroll}
            tableClassName={styles.auditTable}
            columns={columns}
            data={visibleRows}
          />
        ) : (
          <div className={styles.auditEmptyState} role="status">
            <Eye aria-hidden="true" />
            <strong>{isRealMode ? '暂无真实操作审计' : '暂无匹配的操作审计'}</strong>
            <span>
              {isRealMode ? '当前管理接口未返回审计记录，不展示 mock 样例。' : '请调整动作、结果、时间或搜索条件。'}
            </span>
          </div>
        )}
      </Card>

      <section className={styles.auditPagination} aria-label="操作审计分页">
        <span>
          显示第 {rangeStart} 到 {rangeEnd} 条，共 {filteredRows.length} 条结果
        </span>
        <div className={styles.auditPageButtons}>
          <Button
            variant="outline"
            size="sm"
            disabled={safePage <= 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            上一页
          </Button>
          <span aria-label={`第 ${safePage} 页，共 ${pageCount} 页`}>
            {safePage} / {pageCount}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={safePage >= pageCount}
            onClick={() => setPage((current) => Math.min(pageCount, current + 1))}
          >
            下一页
          </Button>
        </div>
      </section>

      {selectedRow ? <AuditDetail row={selectedRow} onClose={() => setSelectedRow(undefined)} /> : null}
    </>
  );
}
