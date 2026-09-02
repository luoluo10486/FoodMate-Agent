import { useEffect, useMemo, useState } from 'react';
import { Copy, Download, Eye, RefreshCw, Search } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DataTable, type TableColumnProps } from '@/components/ui/data-table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input as ShadcnInput } from '@/components/ui/input';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { AdminOnlyNotice } from './AdminComponents';
import { adminOperationAuditRows, canViewAudit, statusTag } from './AdminShared';
import {
  downloadAdminExport,
  loadAdminExportStatus,
  loadAdminOperationAudits,
  requestAdminExport,
  type AdminExportStatus,
} from '../../../services/adminService';
import styles from '../AdminPage.module.css';

type AuditSource = {
  key: string;
  operator_id: string | number | null;
  operator: string;
  action: string;
  target_type: string;
  target_id: string;
  result: string;
  request_id: string;
  trace_id: string;
  created_at?: string | null;
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

const figmaAuditRows: AuditRecord[] = [
  {
    key: 'figma-audit-0',
    operatorId: '1234567',
    operator: 'anddy_admin',
    action: '更新权限',
    targetType: 'tool_registry',
    targetId: 'tool_registry',
    result: '成功',
    requestId: 'req_7c2e',
    traceId: 'tr_88192a',
    createdAt: '10:24:12',
    requestSummary: '更新权限 tool_registry',
    beforeState: 'operator',
    afterState: 'admin',
    errorCode: 'OK',
    clientInfo: 'admin',
  },
  {
    key: 'figma-audit-1',
    operatorId: 'operator-chen',
    operator: 'operator_chen',
    action: '查看详情',
    targetType: 'user',
    targetId: 'user_1234567',
    result: '失败',
    requestId: 'req_7c44',
    traceId: 'tr_881a2b',
    createdAt: '10:19:04',
    requestSummary: '查看详情 user_1234567',
    beforeState: 'active',
    afterState: 'active',
    errorCode: 'ERR_POLICY',
    clientInfo: 'operator',
  },
  {
    key: 'figma-audit-2',
    operatorId: 'system',
    operator: 'system',
    action: '重新索引',
    targetType: 'document',
    targetId: 'doc_552b1',
    result: '成功',
    requestId: 'req_7c51',
    traceId: 'tr_881b0c',
    createdAt: '09:58:31',
    requestSummary: '重新索引 doc_552b1',
    beforeState: 'stale',
    afterState: 'indexed',
    errorCode: 'OK',
    clientInfo: 'system',
  },
  {
    key: 'figma-audit-3',
    operatorId: '1234567',
    operator: 'anddy_admin',
    action: '恢复资源',
    targetType: 'run',
    targetId: 'run_98218a',
    result: '成功',
    requestId: 'req_7c63',
    traceId: 'tr_881c11',
    createdAt: '09:44:08',
    requestSummary: '恢复资源 run_98218a',
    beforeState: 'deleted',
    afterState: 'active',
    errorCode: 'OK',
    clientInfo: 'admin',
  },
  {
    key: 'figma-audit-4',
    operatorId: 'system',
    operator: 'system',
    action: '停用工具',
    targetType: 'tool',
    targetId: 'sql_query',
    result: '拒绝',
    requestId: 'req_7c75',
    traceId: 'tr_881d09',
    createdAt: '09:31:16',
    requestSummary: '停用工具 sql_query',
    beforeState: 'active',
    afterState: 'active',
    errorCode: 'DENIED',
    clientInfo: 'system',
  },
  {
    key: 'figma-audit-5',
    operatorId: '1234567',
    operator: 'anddy_admin',
    action: '导出报告',
    targetType: 'profile',
    targetId: 'profile_88',
    result: '成功',
    requestId: 'req_7c88',
    traceId: 'tr_881e31',
    createdAt: '09:12:44',
    requestSummary: '导出报告 profile_88',
    beforeState: 'ready',
    afterState: 'exported',
    errorCode: 'OK',
    clientInfo: 'admin',
  },
];

type AuditFigmaFilter = 'all' | '成功' | '失败' | '拒绝';

function FigmaAuditFilter({
  label,
  ariaLabel,
  value,
  options,
  onChange,
  className,
}: {
  label: string;
  ariaLabel: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
  className?: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={`${styles.auditFigmaFilter} ${className ?? ''}`} aria-label={ariaLabel}>
        <span className={styles.auditFigmaFilterLabel}>{label}:</span>
        <SelectValue />
        <span className={styles.auditFigmaFilterArrow} aria-hidden="true" />
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

function FigmaAuditAction({ action }: { action: string }) {
  return (
    <span className={`${styles.auditFigmaAction} ${action === '停用工具' ? styles.auditFigmaActionMuted : ''}`}>
      {action}
    </span>
  );
}

function FigmaAuditTarget({ targetId }: { targetId: string }) {
  const tone = targetId === 'sql_query' ? 'Danger' : targetId === 'profile_88' ? 'Orange' : 'Info';
  return <span className={`${styles.auditFigmaTarget} ${styles[`auditFigmaTarget${tone}`]}`}>{targetId}</span>;
}

function FigmaOperationAuditSection() {
  const [result, setResult] = useState<AuditFigmaFilter>('all');
  const [target, setTarget] = useState('all');
  const [action, setAction] = useState('all');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [selectedRow, setSelectedRow] = useState<AuditRecord>();

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return figmaAuditRows.filter((row) => {
      const searchable = [row.requestId, row.traceId].join(' ').toLowerCase();
      return (
        (result === 'all' || row.result === result) &&
        (target === 'all' || row.targetId === target) &&
        (action === 'all' || row.action === action) &&
        (!normalizedQuery || searchable.includes(normalizedQuery))
      );
    });
  }, [action, query, result, target]);

  const resultCount =
    result === 'all' && target === 'all' && action === 'all' && !query.trim() ? 1284 : filteredRows.length;
  const totalPages = Math.max(1, Math.ceil(resultCount / figmaAuditRows.length));
  const currentPage = Math.min(page, totalPages);
  const visibleRows = currentPage === 1 ? filteredRows : [];
  const rangeStart = resultCount === 0 ? 0 : (currentPage - 1) * figmaAuditRows.length + 1;
  const rangeEnd = Math.min(currentPage * figmaAuditRows.length, resultCount);

  const resetPage = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(1);
  };

  const copyTime = async (time: string) => {
    try {
      await navigator.clipboard?.writeText(time);
    } catch {
      // Clipboard access is optional in embedded preview contexts.
    }
  };

  return (
    <div className={styles.auditFigmaPage}>
      <section className={styles.auditFigmaFilters} aria-label="操作审计筛选">
        <div className={styles.auditFigmaFilterGroup}>
          <FigmaAuditFilter
            label="结果"
            ariaLabel="审计结果筛选"
            value={result}
            onChange={(value) => {
              setResult(value as AuditFigmaFilter);
              setPage(1);
            }}
            options={[
              { value: 'all', label: '全部' },
              { value: '成功', label: '成功' },
              { value: '失败', label: '失败' },
              { value: '拒绝', label: '拒绝' },
            ]}
          />
          <FigmaAuditFilter
            label="目标类型"
            ariaLabel="审计目标类型筛选"
            value={target}
            onChange={resetPage(setTarget)}
            options={[
              { value: 'all', label: '全部' },
              ...Array.from(new Set(figmaAuditRows.map((row) => row.targetId))).map((value) => ({
                value,
                label: value,
              })),
            ]}
            className={styles.auditFigmaFilterTarget}
          />
          <FigmaAuditFilter
            label="动作"
            ariaLabel="审计动作筛选"
            value={action}
            onChange={resetPage(setAction)}
            options={[
              { value: 'all', label: '全部' },
              ...Array.from(new Set(figmaAuditRows.map((row) => row.action))).map((value) => ({ value, label: value })),
            ]}
          />
        </div>
        <label className={styles.auditFigmaSearch}>
          <span className={styles.auditFigmaSearchIcon} aria-hidden="true" />
          <ShadcnInput
            aria-label="搜索 request_id / trace_id..."
            placeholder="搜索 request_id / trace_id..."
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
          />
        </label>
      </section>

      <section className={styles.auditFigmaStats} aria-label="操作审计统计">
        <Card className={styles.auditFigmaStatCard}>
          <span>近 24h 操作</span>
          <strong>1,284</strong>
        </Card>
        <Card className={`${styles.auditFigmaStatCard} ${styles.auditFigmaStatFailure}`}>
          <span>失败操作</span>
          <strong>18</strong>
        </Card>
        <Card className={`${styles.auditFigmaStatCard} ${styles.auditFigmaStatReview}`}>
          <span>待复核</span>
          <strong>4 条</strong>
        </Card>
      </section>

      <section className={styles.auditFigmaTableCard} aria-label="操作审计明细">
        <Table className={styles.auditFigmaTable}>
          <TableHeader className={styles.auditFigmaTableHeader}>
            <TableRow>
              <TableHead>时间</TableHead>
              <TableHead>操作者</TableHead>
              <TableHead>动作</TableHead>
              <TableHead>目标</TableHead>
              <TableHead>结果</TableHead>
              <TableHead>request_id</TableHead>
              <TableHead>trace_id</TableHead>
              <TableHead>来源</TableHead>
              <TableHead>错误码</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visibleRows.map((row) => (
              <TableRow key={row.key}>
                <TableCell>
                  <span className={styles.auditFigmaTimeCell}>
                    <strong>{row.createdAt}</strong>
                    <Button
                      variant="ghost"
                      size="icon"
                      className={styles.auditFigmaCopyButton}
                      aria-label={`复制 ${row.createdAt}`}
                      onClick={() => void copyTime(row.createdAt)}
                    >
                      <Copy aria-hidden="true" />
                    </Button>
                  </span>
                </TableCell>
                <TableCell className={styles.auditFigmaOperator}>{row.operator}</TableCell>
                <TableCell>
                  <FigmaAuditAction action={row.action} />
                </TableCell>
                <TableCell>
                  <FigmaAuditTarget targetId={row.targetId} />
                </TableCell>
                <TableCell>{row.result}</TableCell>
                <TableCell className={styles.auditFigmaRequest}>{row.requestId}</TableCell>
                <TableCell className={styles.auditFigmaTrace}>{row.traceId}</TableCell>
                <TableCell className={styles.auditFigmaSource}>{row.clientInfo}</TableCell>
                <TableCell
                  className={`${styles.auditFigmaError} ${row.errorCode === 'OK' ? '' : styles.auditFigmaErrorDanger}`}
                >
                  {row.errorCode}
                </TableCell>
                <TableCell className={styles.auditFigmaActionCell}>
                  <Button
                    variant="outline"
                    className={styles.auditFigmaDetailButton}
                    onClick={() => setSelectedRow(row)}
                  >
                    查看详情
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {!visibleRows.length ? (
              <TableRow>
                <TableCell colSpan={10} className={styles.auditFigmaEmpty}>
                  暂无匹配的操作审计
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </section>

      <nav className={styles.auditFigmaPagination} aria-label="操作审计分页">
        <p>
          显示第 {rangeStart} 到 {rangeEnd} 条，共 {resultCount.toLocaleString('en-US')} 条结果
        </p>
        <div className={styles.auditFigmaPageButtons}>
          <Button
            variant="outline"
            className={styles.auditFigmaPageButton}
            disabled={currentPage === 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            上一页
          </Button>
          {[1, 2, 3, 4].map((pageNumber) => (
            <Button
              key={pageNumber}
              variant={currentPage === pageNumber ? 'default' : 'outline'}
              className={`${styles.auditFigmaPageButton} ${currentPage === pageNumber ? styles.auditFigmaPageButtonActive : ''}`}
              onClick={() => setPage(pageNumber)}
            >
              {pageNumber}
            </Button>
          ))}
          <Button
            variant="outline"
            className={styles.auditFigmaPageButton}
            disabled={currentPage === totalPages}
            onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
          >
            下一页
          </Button>
        </div>
      </nav>

      <Card className={styles.auditFigmaAnalytics} aria-label="操作审计分析">
        <article>
          <h2>审计详情</h2>
          <p>按动作 / 目标 / 结果筛选</p>
          <p>request_id / trace_id 可复制 · 敏感字段脱敏</p>
        </article>
        <article>
          <h2>最近异常</h2>
          <p>ERR_POLICY 4 条 · DENIED 3 条</p>
          <p>查看前后状态摘要 · 审计记录只读</p>
        </article>
        <article>
          <h2>关联追踪</h2>
          <p>Trace / Run / Tool Call 可跳转</p>
          <p>保留客户端、时间和稳定错误码</p>
        </article>
      </Card>

      {selectedRow ? <AuditDetail row={selectedRow} onClose={() => setSelectedRow(undefined)} /> : null}
    </div>
  );
}

function normalizeAuditRow(row: AuditSource): AuditRecord {
  return {
    key: row.key,
    operatorId: row.operator_id == null ? '-' : String(row.operator_id),
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

function RealOperationAuditSection({ refreshNonce = 0 }: { refreshNonce?: number }) {
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
  const [exportJob, setExportJob] = useState<AdminExportStatus>();
  const [exportBusy, setExportBusy] = useState(false);
  const [exportMessage, setExportMessage] = useState('');

  const createExport = async () => {
    setExportBusy(true);
    setExportMessage('');
    try {
      const created = await requestAdminExport(
        'operation-audits',
        { query: query.trim() || undefined, status: resultFilter === 'all' ? undefined : resultFilter },
        ['operator_id', 'action', 'target_type', 'target_id', 'result', 'request_id', 'trace_id', 'created_at'],
      );
      const status = await loadAdminExportStatus(created.export_job_id);
      setExportJob(status);
      setExportMessage(`导出任务 #${created.export_job_id} 已创建，当前状态：${status.status}`);
    } catch (error) {
      setExportMessage(error instanceof Error ? error.message : '导出任务创建失败');
    } finally {
      setExportBusy(false);
    }
  };

  const refreshExport = async () => {
    if (!exportJob) return;
    setExportBusy(true);
    try {
      const status = await loadAdminExportStatus(exportJob.export_job_id);
      setExportJob(status);
      setExportMessage(`导出任务 #${status.export_job_id} 当前状态：${status.status}`);
    } catch (error) {
      setExportMessage(error instanceof Error ? error.message : '导出状态查询失败');
    } finally {
      setExportBusy(false);
    }
  };

  const consumeExport = async () => {
    if (!exportJob || exportJob.status !== 'completed') return;
    setExportBusy(true);
    try {
      const result = await downloadAdminExport(exportJob.export_job_id);
      window.open(result.download_url, '_blank', 'noopener,noreferrer');
      setExportJob({ ...exportJob, download_consumed_at: new Date().toISOString() });
      setExportMessage('下载链接已生成，下载资格已消费一次。');
    } catch (error) {
      setExportMessage(error instanceof Error ? error.message : '导出下载失败');
    } finally {
      setExportBusy(false);
    }
  };

  useEffect(() => {
    if (!isRealMode) return;
    loadAdminOperationAudits()
      .then((items) => {
        setLoadError('');
        setRealRows(
          items.map((row, index) =>
            normalizeAuditRow({
              ...row,
              key: `operation-${row.request_id || index}`,
              operator: row.operator_id == null ? '-' : String(row.operator_id),
            }),
          ),
        );
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
          <div className={styles.auditTableActions}>
            {isRealMode && canViewAudit ? (
              <Button variant="outline" size="sm" disabled={exportBusy} onClick={() => void createExport()}>
                <Download aria-hidden="true" />
                导出当前结果
              </Button>
            ) : null}
            <Badge variant="outline">仅 admin / superadmin</Badge>
          </div>
        </div>
        {isRealMode && exportMessage ? (
          <div className={styles.auditExportStatus} role="status">
            <span>{exportMessage}</span>
            {exportJob && exportJob.status !== 'completed' && exportJob.status !== 'failed' ? (
              <Button variant="outline" size="sm" disabled={exportBusy} onClick={() => void refreshExport()}>
                <RefreshCw aria-hidden="true" />
                检查状态
              </Button>
            ) : null}
            {exportJob?.status === 'completed' && !exportJob.download_consumed_at ? (
              <Button variant="outline" size="sm" disabled={exportBusy} onClick={() => void consumeExport()}>
                <Download aria-hidden="true" />
                下载 JSON
              </Button>
            ) : null}
          </div>
        ) : null}
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

export function OperationAuditSection(props: { refreshNonce?: number }) {
  return import.meta.env.VITE_AGENT_MODE === 'real' ? (
    <RealOperationAuditSection {...props} />
  ) : (
    <FigmaOperationAuditSection />
  );
}
