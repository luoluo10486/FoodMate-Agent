import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  Copy,
  GitBranch,
  RotateCcw,
  Search,
  ShieldAlert,
  Timer,
  Workflow,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DataTable, type TableColumnProps } from '@/components/ui/data-table';
import { Input as ShadcnInput } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import styles from '../AdminPage.module.css';
import { MiniStat } from './AdminComponents';
import {
  adminAuditRows,
  adminSqlAuditRows,
  adminToolCallRows,
  adminTraceRows,
  riskTag,
  statusTag,
} from './AdminShared';
import {
  loadAdminQuery,
  type AdminRunRow,
  type AdminQueryRun,
  type AdminQuerySqlAudit,
  type AdminQueryToolCall,
  type AdminSqlAuditRow,
  type AdminToolCallRow,
  type AdminTraceRow,
} from '../../../services/adminService';

type DashboardState = {
  runs: AdminRunRow[];
  toolCalls: AdminToolCallRow[];
  sqlAudits: AdminSqlAuditRow[];
  traces: AdminTraceRow[];
};

type GovernanceTab = 'agent-runs' | 'tool-calls' | 'sql-audits' | 'traces';

type DetailSelection =
  | { type: 'run'; row: AdminRunRow }
  | { type: 'tool'; row: AdminToolCallRow }
  | { type: 'sql'; row: AdminSqlAuditRow }
  | { type: 'trace'; row: AdminTraceRow };

const isRealMode = import.meta.env.VITE_AGENT_MODE === 'real';

const emptyDashboard: DashboardState = { runs: [], toolCalls: [], sqlAudits: [], traces: [] };

const mockDashboard: DashboardState = {
  runs: adminAuditRows,
  toolCalls: adminToolCallRows,
  sqlAudits: adminSqlAuditRows,
  traces: adminTraceRows,
};

function tabFromSearch(params: URLSearchParams): GovernanceTab {
  if (params.get('tab') === 'sql') return 'sql-audits';
  if (params.get('tab') === 'trace') return 'traces';
  if (params.get('tab') === 'tool') return 'tool-calls';
  return 'agent-runs';
}

function matchesCommon(
  values: Array<string | number | undefined>,
  status: string,
  error: string | undefined,
  query: string,
  statusFilter: string,
  errorFilter: string,
) {
  const queryMatches =
    !query ||
    values.some((value) =>
      String(value ?? '')
        .toLowerCase()
        .includes(query),
    );
  const statusMatches = statusFilter === 'all' || status === statusFilter;
  const errorMatches =
    !errorFilter ||
    String(error ?? '-')
      .toLowerCase()
      .includes(errorFilter);
  return queryMatches && statusMatches && errorMatches;
}

function formatDuration(value?: number) {
  if (value == null || Number.isNaN(value)) return '-';
  return `${value} ms`;
}

function medianDuration(rows: AdminRunRow[]) {
  if (!rows.length) return '-';
  const values = rows.map((row) => row.durationMs).sort((a, b) => a - b);
  return formatDuration(values[Math.floor(values.length / 2)]);
}

function queryRunRow(row: AdminQueryRun, index: number): AdminRunRow {
  return {
    key: `run-${row.agent_run_id ?? index}`,
    runId: textValue(row.agent_run_id),
    user: row.actor_ref || '-',
    intent: row.intent || '-',
    status: row.status || '-',
    durationMs: Number(row.duration_ms ?? 0),
    traceId: row.trace_id || '-',
    sessionId: row.session_id == null ? undefined : String(row.session_id),
    resultType: row.status || '-',
    errorCode: '-',
    stage: row.intent || '-',
    model: '-',
  };
}

function queryToolCallRow(row: AdminQueryToolCall, index: number): AdminToolCallRow {
  return {
    key: `call-${row.tool_call_id ?? index}`,
    callId: textValue(row.tool_call_id),
    runId: textValue(row.agent_run_id),
    toolName: row.tool_name || '-',
    status: row.status || '-',
    latencyMs: row.latency_ms ?? 0,
    traceId: row.trace_id || '-',
    requestId: '-',
    inputSummary: '-',
    outputSummary: '-',
    errorCode: '-',
  };
}

function querySqlAuditRow(row: AdminQuerySqlAudit, index: number): AdminSqlAuditRow {
  return {
    key: `sql-${row.sql_audit_id ?? index}`,
    auditId: textValue(row.sql_audit_id),
    actor: textValue(row.actor),
    statement: row.query_hash ? `query_hash:${row.query_hash}` : '-',
    risk: '-',
    result: row.result || '-',
    traceId: row.trace_id || '-',
    durationMs: row.latency_ms ?? 0,
    rowCount: row.row_count ?? 0,
    policy: '-',
    queryHash: row.query_hash || '-',
    errorCode: row.error_code || '-',
    createdAt: row.created_at || '-',
  };
}

function textValue(value: number | null | undefined) {
  return value == null ? '-' : String(value);
}

function copyValue(value: string) {
  if (navigator.clipboard && value !== '-') void navigator.clipboard.writeText(value);
}

function CopyValue({ value, label = '复制 ID' }: { value: string; label?: string }) {
  return (
    <Button
      aria-label={`${label}: ${value}`}
      className={styles.runCopyButton}
      size="icon"
      title={label}
      variant="ghost"
      onClick={() => copyValue(value)}
    >
      <Copy aria-hidden="true" />
    </Button>
  );
}

function selectionStatus(selection: DetailSelection) {
  return selection.type === 'sql' ? selection.row.result : selection.row.status;
}

function DetailValue({ label, value, copy = false }: { label: string; value?: string | number; copy?: boolean }) {
  const text = value == null || value === '' ? '-' : String(value);
  return (
    <div>
      <dt>{label}</dt>
      <dd className={copy ? styles.runCopyValue : undefined}>
        <span>{text}</span>
        {copy && text !== '-' ? <CopyValue value={text} /> : null}
      </dd>
    </div>
  );
}

function RunDetailSheet({
  selection,
  dashboard,
  onClose,
  onSelect,
}: {
  selection?: DetailSelection;
  dashboard: DashboardState;
  onClose: () => void;
  onSelect: (selection: DetailSelection) => void;
}) {
  const relatedTrace = selection
    ? selection.type === 'trace'
      ? selection.row
      : dashboard.traces.find((trace) => trace.traceId === selection.row.traceId)
    : undefined;
  const relatedRun = selection
    ? selection.type === 'run'
      ? selection.row
      : dashboard.runs.find((run) => run.traceId === selection.row.traceId)
    : undefined;

  return (
    <Sheet open={Boolean(selection)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className={styles.runDetailSheet}>
        <SheetHeader className={styles.runDetailHeader}>
          <div>
            <SheetTitle>
              {selection?.type === 'run'
                ? 'Run 详情'
                : selection?.type === 'tool'
                  ? 'Tool Call 详情'
                  : selection?.type === 'sql'
                    ? 'SQL Audit 详情'
                    : 'Trace 详情'}
            </SheetTitle>
            <SheetDescription>展示当前记录的权威字段与可追踪关联，不补写后端未返回的事实。</SheetDescription>
          </div>
          {selection ? (
            <Badge variant={selectionStatus(selection) === 'failed' ? 'destructive' : 'outline'}>
              {selectionStatus(selection)}
            </Badge>
          ) : null}
        </SheetHeader>

        {selection ? (
          <div className={styles.runDetailBody}>
            {selection.type === 'run' ? (
              <>
                <section>
                  <h3 className={styles.detailSectionHeading}>
                    <Workflow aria-hidden="true" /> Run 事实
                  </h3>
                  <dl className={styles.detailGrid}>
                    <DetailValue label="Run ID" value={selection.row.runId} copy />
                    <DetailValue label="用户" value={selection.row.user} />
                    <DetailValue label="Session ID" value={selection.row.sessionId ?? selection.row.userId} copy />
                    <DetailValue label="意图" value={selection.row.intent} />
                    <DetailValue label="阶段" value={selection.row.stage} />
                    <DetailValue label="结果类型" value={selection.row.resultType} />
                    <DetailValue label="错误码" value={selection.row.errorCode} />
                    <DetailValue label="模型" value={selection.row.model} />
                    <DetailValue label="耗时" value={formatDuration(selection.row.durationMs)} />
                    <DetailValue label="创建时间" value={selection.row.createdAt} />
                    <DetailValue label="Trace ID" value={selection.row.traceId} copy />
                  </dl>
                </section>
                <section>
                  <h3 className={styles.detailSectionHeading}>
                    <Activity aria-hidden="true" /> 关联 Tool Call
                  </h3>
                  {dashboard.toolCalls.filter((call) => call.runId === selection.row.runId).length ? (
                    <div className={styles.relatedRecords}>
                      {dashboard.toolCalls
                        .filter((call) => call.runId === selection.row.runId)
                        .map((call) => (
                          <Button
                            variant="ghost"
                            className={styles.relatedRecord}
                            key={call.key}
                            type="button"
                            onClick={() => onSelect({ type: 'tool', row: call })}
                          >
                            <span>
                              <strong>{call.toolName}</strong>
                              <small>{call.callId}</small>
                            </span>
                            {statusTag(call.status)}
                          </Button>
                        ))}
                    </div>
                  ) : (
                    <p className={styles.detailMuted}>该 Run 没有返回 Tool Call 记录。</p>
                  )}
                </section>
              </>
            ) : null}

            {selection.type === 'tool' ? (
              <>
                <section>
                  <h3 className={styles.detailSectionHeading}>
                    <Activity aria-hidden="true" /> Tool Call 事实
                  </h3>
                  <dl className={styles.detailGrid}>
                    <DetailValue label="Call ID" value={selection.row.callId} copy />
                    <DetailValue label="Run ID" value={selection.row.runId} copy />
                    <DetailValue label="工具" value={selection.row.toolName} />
                    <DetailValue label="状态" value={selection.row.status} />
                    <DetailValue label="延迟" value={formatDuration(selection.row.latencyMs)} />
                    <DetailValue label="请求 ID" value={selection.row.requestId} copy />
                    <DetailValue label="错误码" value={selection.row.errorCode} />
                    <DetailValue label="开始时间" value={selection.row.startedAt} />
                    <DetailValue label="完成时间" value={selection.row.completedAt} />
                    <DetailValue label="Trace ID" value={selection.row.traceId} copy />
                  </dl>
                </section>
                <section className={styles.payloadGrid}>
                  <div>
                    <span>输入摘要</span>
                    <code>{selection.row.inputSummary ?? '-'}</code>
                  </div>
                  <div>
                    <span>输出摘要</span>
                    <code>{selection.row.outputSummary ?? '-'}</code>
                  </div>
                </section>
                {relatedTrace ? (
                  <Button
                    className={styles.runDetailLink}
                    variant="outline"
                    onClick={() => onSelect({ type: 'trace', row: relatedTrace })}
                  >
                    <GitBranch aria-hidden="true" /> 查看关联 Trace
                  </Button>
                ) : null}
              </>
            ) : null}

            {selection.type === 'sql' ? (
              <>
                <section>
                  <h3 className={styles.detailSectionHeading}>
                    <ShieldAlert aria-hidden="true" /> SQL Audit 事实
                  </h3>
                  <dl className={styles.detailGrid}>
                    <DetailValue label="Audit ID" value={selection.row.auditId} copy />
                    <DetailValue label="执行方" value={selection.row.actor} />
                    <DetailValue label="风险" value={selection.row.risk} />
                    <DetailValue label="结果" value={selection.row.result} />
                    <DetailValue label="策略" value={selection.row.policy} />
                    <DetailValue label="耗时" value={formatDuration(selection.row.durationMs)} />
                    <DetailValue label="返回行数" value={selection.row.rowCount} />
                    <DetailValue label="错误码" value={selection.row.errorCode} />
                    <DetailValue label="查询哈希" value={selection.row.queryHash} copy />
                    <DetailValue label="Trace ID" value={selection.row.traceId} copy />
                  </dl>
                </section>
                <section className={styles.payloadGrid}>
                  <div>
                    <span>语句摘要</span>
                    <code>{selection.row.statement}</code>
                  </div>
                </section>
                {relatedTrace ? (
                  <Button
                    className={styles.runDetailLink}
                    variant="outline"
                    onClick={() => onSelect({ type: 'trace', row: relatedTrace })}
                  >
                    <GitBranch aria-hidden="true" /> 查看关联 Trace
                  </Button>
                ) : null}
              </>
            ) : null}

            {selection.type === 'trace' ? (
              <>
                <section>
                  <h3 className={styles.detailSectionHeading}>
                    <GitBranch aria-hidden="true" /> Trace 事实
                  </h3>
                  <dl className={styles.detailGrid}>
                    <DetailValue label="Trace ID" value={selection.row.traceId} copy />
                    <DetailValue label="Run ID" value={selection.row.runId} copy />
                    <DetailValue label="根服务" value={selection.row.rootService} />
                    <DetailValue label="Span 数量" value={selection.row.spanCount} />
                    <DetailValue label="状态" value={selection.row.status} />
                    <DetailValue label="耗时" value={formatDuration(selection.row.durationMs)} />
                    <DetailValue label="错误码" value={selection.row.errorCode} />
                    <DetailValue label="开始时间" value={selection.row.startedAt} />
                  </dl>
                </section>
                <section>
                  <h3 className={styles.detailSectionHeading}>
                    <Timer aria-hidden="true" /> 链路节点
                  </h3>
                  <ol className={styles.traceSteps}>
                    {selection.row.entry.split(' -> ').map((step, index) => (
                      <li key={`${step}-${index}`}>
                        <span>{index + 1}</span>
                        <code>{step}</code>
                      </li>
                    ))}
                  </ol>
                </section>
                {relatedRun ? (
                  <Button
                    className={styles.runDetailLink}
                    variant="outline"
                    onClick={() => onSelect({ type: 'run', row: relatedRun })}
                  >
                    <Workflow aria-hidden="true" /> 查看关联 Run
                  </Button>
                ) : null}
              </>
            ) : null}
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function DataPlaceholder({ filtered, tab, error }: { filtered: boolean; tab: GovernanceTab; error?: string }) {
  const title = error ? '真实接口加载失败' : filtered ? '未找到匹配记录' : isRealMode ? '真实接口暂未返回数据' : '暂无治理记录';
  const description = filtered
    ? '请调整关键词、状态或错误码筛选条件。'
    : error
      ? error
    : isRealMode
      ? tab === 'traces'
        ? '当前管理仪表盘契约尚未接入 Trace 明细，页面不会伪造链路数据。'
        : '当前接口没有返回该类记录。'
      : 'mock 数据集中没有可展示的记录。';
  return (
    <div className={styles.runEmptyState} role="status">
      <AlertTriangle aria-hidden="true" />
      <strong>{title}</strong>
      <span>{description}</span>
    </div>
  );
}

export function RunsSection({ refreshNonce = 0 }: { refreshNonce?: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [dashboard, setDashboard] = useState<DashboardState>(isRealMode ? emptyDashboard : mockDashboard);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [resultFilter, setResultFilter] = useState('all');
  const [errorFilter, setErrorFilter] = useState('');
  const [selection, setSelection] = useState<DetailSelection>();
  const [loadError, setLoadError] = useState('');
  const activeTab = tabFromSearch(searchParams);

  useEffect(() => {
    if (!isRealMode) return;
    let mounted = true;
    setLoadError('');
    Promise.all([
      loadAdminQuery<AdminQueryRun>('runs'),
      loadAdminQuery<AdminQueryToolCall>('tool-calls'),
      loadAdminQuery<AdminQuerySqlAudit>('sql-audits'),
    ])
      .then(([runs, toolCalls, sqlAudits]) => {
        if (mounted)
          setDashboard({
            runs: runs.items.map(queryRunRow),
            toolCalls: toolCalls.items.map(queryToolCallRow),
            sqlAudits: sqlAudits.items.map(querySqlAuditRow),
            traces: [],
          });
      })
      .catch((error) => {
        if (mounted) {
          setDashboard(emptyDashboard);
          setLoadError(error instanceof Error ? error.message : '运行治理数据加载失败');
        }
      });
    return () => {
      mounted = false;
    };
  }, [refreshNonce]);

  const normalizedQuery = query.trim().toLowerCase();
  const normalizedError = errorFilter.trim().toLowerCase();
  const filteredRuns = useMemo(
    () =>
      dashboard.runs.filter((row) => {
        const resultMatches =
          resultFilter === 'all' ||
          row.resultType === resultFilter ||
          (resultFilter === 'error' && row.status === 'failed');
        return (
          matchesCommon(
            [row.runId, row.user, row.userId, row.sessionId, row.traceId, row.intent, row.stage, row.model],
            row.status,
            row.errorCode,
            normalizedQuery,
            statusFilter,
            normalizedError,
          ) && resultMatches
        );
      }),
    [dashboard.runs, normalizedQuery, normalizedError, resultFilter, statusFilter],
  );

  const filteredToolCalls = useMemo(
    () =>
      dashboard.toolCalls.filter((row) =>
        matchesCommon(
          [row.callId, row.runId, row.toolName, row.traceId, row.requestId],
          row.status,
          row.errorCode,
          normalizedQuery,
          statusFilter,
          normalizedError,
        ),
      ),
    [dashboard.toolCalls, normalizedQuery, normalizedError, statusFilter],
  );

  const filteredSqlAudits = useMemo(
    () =>
      dashboard.sqlAudits.filter((row) =>
        matchesCommon(
          [row.auditId, row.actor, row.statement, row.traceId, row.queryHash, row.policy],
          row.result,
          row.errorCode,
          normalizedQuery,
          statusFilter,
          normalizedError,
        ),
      ),
    [dashboard.sqlAudits, normalizedQuery, normalizedError, statusFilter],
  );

  const filteredTraces = useMemo(
    () =>
      dashboard.traces.filter((row) =>
        matchesCommon(
          [row.traceId, row.runId, row.entry, row.rootService],
          row.status,
          row.errorCode,
          normalizedQuery,
          statusFilter,
          normalizedError,
        ),
      ),
    [dashboard.traces, normalizedQuery, normalizedError, statusFilter],
  );

  const failureCount = dashboard.runs.filter((row) => row.status === 'failed').length;
  const failureRate = dashboard.runs.length ? `${((failureCount / dashboard.runs.length) * 100).toFixed(1)}%` : '-';
  const changeTab = (value: string) => {
    if (!['agent-runs', 'tool-calls', 'sql-audits', 'traces'].includes(value)) return;
    const tab = value as GovernanceTab;
    const next = new URLSearchParams(searchParams);
    if (tab === 'sql-audits') next.set('tab', 'sql');
    else if (tab === 'traces') next.set('tab', 'trace');
    else if (tab === 'tool-calls') next.set('tab', 'tool');
    else next.delete('tab');
    setSearchParams(next, { replace: true });
  };
  const resetFilters = () => {
    setQuery('');
    setStatusFilter('all');
    setResultFilter('all');
    setErrorFilter('');
  };

  const runColumns: TableColumnProps<AdminRunRow>[] = [
    { title: 'Run ID', dataIndex: 'runId' },
    { title: '用户', dataIndex: 'user' },
    { title: 'Session ID', dataIndex: 'sessionId' },
    { title: '意图', dataIndex: 'intent' },
    { title: '阶段', dataIndex: 'stage' },
    { title: '状态', dataIndex: 'status', render: (_, row) => statusTag(row.status) },
    { title: '结果类型', dataIndex: 'resultType' },
    { title: '错误码', dataIndex: 'errorCode' },
    { title: '耗时', render: (_, row) => formatDuration(row.durationMs) },
    { title: 'Trace ID', dataIndex: 'traceId' },
    {
      title: '操作',
      render: (_, row) => (
        <Button variant="outline" size="sm" onClick={() => setSelection({ type: 'run', row })}>
          查看详情
        </Button>
      ),
    },
  ];
  const toolColumns: TableColumnProps<AdminToolCallRow>[] = [
    { title: 'Call ID', dataIndex: 'callId' },
    { title: 'Run ID', dataIndex: 'runId' },
    { title: '工具', dataIndex: 'toolName' },
    { title: '状态', dataIndex: 'status', render: (_, row) => statusTag(row.status) },
    { title: '延迟', render: (_, row) => formatDuration(row.latencyMs) },
    { title: '请求 ID', dataIndex: 'requestId' },
    { title: '错误码', dataIndex: 'errorCode' },
    { title: 'Trace ID', dataIndex: 'traceId' },
    {
      title: '操作',
      render: (_, row) => (
        <Button variant="outline" size="sm" onClick={() => setSelection({ type: 'tool', row })}>
          查看详情
        </Button>
      ),
    },
  ];
  const sqlColumns: TableColumnProps<AdminSqlAuditRow>[] = [
    { title: 'Audit ID', dataIndex: 'auditId' },
    { title: '执行方', dataIndex: 'actor' },
    { title: '语句摘要', dataIndex: 'statement' },
    { title: '风险', dataIndex: 'risk', render: (_, row) => riskTag(row.risk) },
    { title: '结果', dataIndex: 'result' },
    { title: '策略', dataIndex: 'policy' },
    { title: '耗时', render: (_, row) => formatDuration(row.durationMs) },
    { title: 'Trace ID', dataIndex: 'traceId' },
    {
      title: '操作',
      render: (_, row) => (
        <Button variant="outline" size="sm" onClick={() => setSelection({ type: 'sql', row })}>
          查看详情
        </Button>
      ),
    },
  ];
  const traceColumns: TableColumnProps<AdminTraceRow>[] = [
    { title: 'Trace ID', dataIndex: 'traceId' },
    { title: 'Run ID', dataIndex: 'runId' },
    { title: '根服务', dataIndex: 'rootService' },
    { title: '链路', dataIndex: 'entry' },
    { title: 'Span 数量', dataIndex: 'spanCount' },
    { title: '状态', dataIndex: 'status', render: (_, row) => statusTag(row.status) },
    { title: '耗时', render: (_, row) => formatDuration(row.durationMs) },
    { title: '错误码', dataIndex: 'errorCode' },
    {
      title: '操作',
      render: (_, row) => (
        <Button variant="outline" size="sm" onClick={() => setSelection({ type: 'trace', row })}>
          查看详情
        </Button>
      ),
    },
  ];

  const activeRows =
    activeTab === 'agent-runs'
      ? filteredRuns
      : activeTab === 'tool-calls'
        ? filteredToolCalls
        : activeTab === 'sql-audits'
          ? filteredSqlAudits
          : filteredTraces;

  return (
    <>
      <section className={styles.sectionCards} aria-label="运行治理指标">
        <MiniStat label="AgentRun 总量" value={String(dashboard.runs.length)} hint="当前数据集" />
        <MiniStat label="失败率" value={failureRate} hint={`${failureCount} 条失败`} tone="danger" />
        <MiniStat label="运行耗时 p50" value={medianDuration(dashboard.runs)} hint="基于返回记录" tone="blue" />
      </section>

      <section className={styles.runFilters} aria-label="运行治理筛选">
        <label className={styles.runFilterField} htmlFor="run-governance-query">
          <span>关键词</span>
          <span className={styles.runFilterInputWrap}>
            <Search aria-hidden="true" />
            <ShadcnInput
              id="run-governance-query"
              value={query}
              placeholder="Run ID / user / session / trace"
              onChange={(event) => setQuery(event.target.value)}
            />
          </span>
        </label>
        <label className={styles.runFilterField}>
          <span>状态</span>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className={styles.runFilterControl} aria-label="运行状态筛选">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              <SelectItem value="completed">completed</SelectItem>
              <SelectItem value="failed">failed</SelectItem>
              <SelectItem value="running">running</SelectItem>
              <SelectItem value="waiting_user">waiting_user</SelectItem>
              <SelectItem value="cancelled">cancelled</SelectItem>
            </SelectContent>
          </Select>
        </label>
        <label className={styles.runFilterField}>
          <span>结果类型</span>
          <Select value={resultFilter} onValueChange={setResultFilter}>
            <SelectTrigger className={styles.runFilterControl} aria-label="结果类型筛选">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部结果</SelectItem>
              <SelectItem value="answer">answer</SelectItem>
              <SelectItem value="error">error</SelectItem>
            </SelectContent>
          </Select>
        </label>
        <label className={styles.runFilterField} htmlFor="run-governance-error">
          <span>错误码</span>
          <ShadcnInput
            id="run-governance-error"
            value={errorFilter}
            placeholder="例如 SQL_POLICY"
            onChange={(event) => setErrorFilter(event.target.value)}
          />
        </label>
        <Button aria-label="重置筛选" size="icon" title="重置筛选" variant="ghost" onClick={resetFilters}>
          <RotateCcw aria-hidden="true" />
        </Button>
      </section>

      <Card className={styles.wideCard}>
        <div className={styles.cardHead}>
          <div>
            <strong>运行治理</strong>
            <p className={styles.runTableDescription}>通过 Run ID、Session ID、Tool Call 和 Trace ID 追踪一次执行。</p>
          </div>
          <Badge variant="outline">{activeRows.length} 条记录</Badge>
        </div>
        <Tabs value={activeTab} onValueChange={changeTab}>
          <TabsList aria-label="运行治理视图" className={styles.runTabsList}>
            <TabsTrigger value="agent-runs">AgentRun</TabsTrigger>
            <TabsTrigger value="tool-calls">ToolCall</TabsTrigger>
            <TabsTrigger value="sql-audits">SQLAudit</TabsTrigger>
            <TabsTrigger value="traces">Trace</TabsTrigger>
          </TabsList>
          <TabsContent value="agent-runs">
            {filteredRuns.length ? (
              <DataTable className={styles.runTable} columns={runColumns} data={filteredRuns} />
            ) : (
              <DataPlaceholder
                filtered={Boolean(query || errorFilter || statusFilter !== 'all' || resultFilter !== 'all')}
                tab="agent-runs"
                error={loadError}
              />
            )}
          </TabsContent>
          <TabsContent value="tool-calls">
            {filteredToolCalls.length ? (
              <DataTable className={styles.runTable} columns={toolColumns} data={filteredToolCalls} />
            ) : (
              <DataPlaceholder filtered={Boolean(query || errorFilter || statusFilter !== 'all')} tab="tool-calls" error={loadError} />
            )}
          </TabsContent>
          <TabsContent value="sql-audits">
            {filteredSqlAudits.length ? (
              <DataTable className={styles.runTable} columns={sqlColumns} data={filteredSqlAudits} />
            ) : (
              <DataPlaceholder filtered={Boolean(query || errorFilter || statusFilter !== 'all')} tab="sql-audits" error={loadError} />
            )}
          </TabsContent>
          <TabsContent value="traces">
            {filteredTraces.length ? (
              <DataTable className={styles.runTable} columns={traceColumns} data={filteredTraces} />
            ) : (
              <DataPlaceholder filtered={Boolean(query || errorFilter || statusFilter !== 'all')} tab="traces" />
            )}
          </TabsContent>
        </Tabs>
      </Card>

      <RunDetailSheet
        selection={selection}
        dashboard={dashboard}
        onClose={() => setSelection(undefined)}
        onSelect={setSelection}
      />
    </>
  );
}
