import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Copy, Lock, Search } from 'lucide-react';
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
import { Button, Card, Table, Tag, type TableColumnProps } from './AdminPrimitives';
import styles from '../AdminPage.module.css';
import { AdminFilters, OperationAuditCard } from './AdminComponents';
import {
  type ToolRegistryRow,
  type ToolRow,
  adminToolRegistryRows,
  adminToolRows,
  canManage,
  riskTag,
  statusTag,
} from './AdminShared';
import type { AdminActionPayload, AdminOperationState } from './types';
import { loadAdminDashboard, updateAdminToolStatus } from '../../../services/adminService';

const registryMetrics = [
  { label: '已注册工具', value: '24 个', tone: 'neutral' },
  { label: '高风险运行限制', value: '3 个', tone: 'coral' },
  { label: '今日API总调用', value: '1,420,951 次', tone: 'green' },
] as const;

const registryStatusLabels: Record<string, string> = { active: '已启用', disabled: '已停用' };
const registryRiskLabels: Record<string, string> = { low: '低风险', medium: '中风险', high: '高风险' };

function RegistryPill({ value, tone }: { value: string; tone: 'green' | 'coral' | 'amber' | 'teal' | 'neutral' }) {
  return (
    <span className={`${styles.registryPill} ${styles[`registryPill${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
      {value}
    </span>
  );
}

function registryStatusPill(value: string) {
  return <RegistryPill value={registryStatusLabels[value] ?? value} tone={value === 'active' ? 'green' : 'neutral'} />;
}

function registryRiskPill(value: string) {
  const tone = value === 'high' ? 'coral' : value === 'medium' ? 'amber' : 'teal';
  return <RegistryPill value={registryRiskLabels[value] ?? value} tone={tone} />;
}

function registryRowFromTool(tool: ToolRow): ToolRegistryRow {
  return {
    ...tool,
    timeoutMs: tool.timeoutMs ?? '-',
    retryPolicy: tool.retryPolicy ?? '-',
    failedRate: tool.failedRate ?? '-',
  };
}

function copyToolName(name: string) {
  if (navigator.clipboard) void navigator.clipboard.writeText(name);
}

function RegistryFilterSelect({
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
      <SelectTrigger className={`${styles.registryFilter} ${className}`} aria-label={ariaLabel}>
        <span className={styles.registryFilterLabel}>{label}:</span>
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

function ToolRegistrySection({
  onAction,
  operationStatus = 'idle',
  refreshNonce = 0,
}: {
  onAction: (payload: AdminActionPayload) => void;
  operationStatus?: AdminOperationState;
  refreshNonce?: number;
}) {
  const [tools, setTools] = useState<ToolRegistryRow[]>(
    import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminToolRegistryRows,
  );
  const [statusFilter, setStatusFilter] = useState('all');
  const [riskFilter, setRiskFilter] = useState('all');
  const [scopeFilter, setScopeFilter] = useState('all');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [selectedTool, setSelectedTool] = useState<ToolRegistryRow>();
  const showOperationActions = operationStatus !== 'idle';

  const createToolAction = (record: ToolRegistryRow): AdminActionPayload => {
    const nextStatus = record.status === 'active' ? 'disabled' : 'active';
    return {
      action: nextStatus === 'disabled' ? '停用工具' : '启用工具',
      targetLabel: record.name,
      targetType: 'tool',
      targetId: record.name,
      execute: async () => {
        await updateAdminToolStatus(record.name, nextStatus);
      },
      onApply: () => {
        setTools((current) =>
          current.map((tool) => (tool.key === record.key ? { ...tool, status: nextStatus } : tool)),
        );
      },
    };
  };

  useEffect(() => {
    if (import.meta.env.VITE_AGENT_MODE !== 'real') return;
    loadAdminDashboard()
      .then((dashboard) => setTools(dashboard.tools.map(registryRowFromTool)))
      .catch(() => setTools([]));
  }, [refreshNonce]);

  const filteredTools = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return tools.filter((tool) => {
      const matchesStatus = statusFilter === 'all' || tool.status === statusFilter;
      const matchesRisk = riskFilter === 'all' || tool.risk === riskFilter;
      const matchesScope = scopeFilter === 'all' || tool.scope === scopeFilter;
      const matchesQuery =
        !normalizedQuery ||
        `${tool.name} ${tool.version} ${tool.scope} ${tool.retryPolicy}`.toLowerCase().includes(normalizedQuery);
      return matchesStatus && matchesRisk && matchesScope && matchesQuery;
    });
  }, [query, riskFilter, scopeFilter, statusFilter, tools]);

  const registryColumns: TableColumnProps<ToolRegistryRow>[] = [
    {
      title: '工具名称',
      dataIndex: 'name',
      render: (value) => {
        const name = String(value);
        return (
          <span className={styles.registryNameCell}>
            <strong>{name}</strong>
            <button
              className={styles.registryCopyButton}
              type="button"
              aria-label={`复制 ${name}`}
              onClick={() => copyToolName(name)}
              title={`复制 ${name}`}
            >
              <Copy aria-hidden="true" />
            </button>
          </span>
        );
      },
    },
    {
      title: '版本',
      dataIndex: 'version',
      render: (value) => <span className={styles.registryMonoMuted}>{String(value)}</span>,
    },
    { title: '状态', dataIndex: 'status', render: (value) => registryStatusPill(String(value)) },
    { title: '风险等级', dataIndex: 'risk', render: (value) => registryRiskPill(String(value)) },
    {
      title: '超时(ms)',
      dataIndex: 'timeoutMs',
      render: (value) => <span className={styles.registryMono}>{String(value)}</span>,
    },
    {
      title: '重试策略',
      dataIndex: 'retryPolicy',
      render: (value) => <span className={styles.registryCellMuted}>{String(value)}</span>,
    },
    {
      title: '权限范围',
      dataIndex: 'scope',
      render: (value) => <span className={styles.registryCellMuted}>{String(value)}</span>,
    },
    {
      title: '最近调用',
      dataIndex: 'lastCalledAt',
      render: (value) => <span className={styles.registryCellMuted}>{String(value)}</span>,
    },
    {
      title: '失败率',
      dataIndex: 'failedRate',
      render: (value, record) => (
        <span className={`${styles.registryMono} ${record.risk === 'high' ? styles.registryFailure : ''}`}>
          {String(value)}
        </span>
      ),
    },
    {
      title: '操作',
      render: (_, record) => (
        <>
          {showOperationActions ? (
            <Button
              className={styles.registryActionButton}
              size="small"
              disabled={operationStatus === 'submitting' || operationStatus === 'no-permission'}
              onClick={() => onAction(createToolAction(record))}
            >
              {operationStatus === 'no-permission' ? <Lock aria-hidden="true" /> : null}
              {record.status === 'active' ? '停用工具' : '启用工具'}
            </Button>
          ) : (
            <Button className={styles.registryActionButton} size="small" onClick={() => setSelectedTool(record)}>
              配置详情
            </Button>
          )}
        </>
      ),
    },
  ];

  const totalResults =
    query || statusFilter !== 'all' || riskFilter !== 'all' || scopeFilter !== 'all' ? filteredTools.length : 24;
  const visibleResults = filteredTools.slice(0, 6);

  return (
    <>
      <section className={styles.registryFilters} aria-label="工具注册表筛选">
        <div className={styles.registryFilterGroup}>
          <RegistryFilterSelect
            label="状态"
            value={statusFilter}
            options={[
              { value: 'all', label: '全部' },
              { value: 'active', label: '已启用' },
              { value: 'disabled', label: '已停用' },
            ]}
            onChange={(value) => {
              setStatusFilter(value);
              setPage(1);
            }}
            ariaLabel="工具状态筛选"
            className={styles.registryFilterStatus}
          />
          <RegistryFilterSelect
            label="风险等级"
            value={riskFilter}
            options={[
              { value: 'all', label: '全部' },
              { value: 'low', label: '低风险' },
              { value: 'medium', label: '中风险' },
              { value: 'high', label: '高风险' },
            ]}
            onChange={(value) => {
              setRiskFilter(value);
              setPage(1);
            }}
            ariaLabel="风险等级筛选"
            className={styles.registryFilterRisk}
          />
          <RegistryFilterSelect
            label="权限范围"
            value={scopeFilter}
            options={[
              { value: 'all', label: '全部' },
              { value: 'read-only', label: 'read-only' },
              { value: 'read-write', label: 'read-write' },
              { value: 'write-only', label: 'write-only' },
              { value: 'admin', label: 'admin' },
            ]}
            onChange={(value) => {
              setScopeFilter(value);
              setPage(1);
            }}
            ariaLabel="权限范围筛选"
            className={styles.registryFilterScope}
          />
        </div>
        <label className={styles.registrySearch}>
          <Search aria-hidden="true" />
          <ShadcnInput
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
            placeholder="搜索工具、指令或版本..."
            aria-label="搜索工具、指令或版本"
          />
        </label>
      </section>

      <section className={styles.registryStats} aria-label="工具注册表指标">
        {registryMetrics.map((metric, index) => (
          <Card className={`${styles.registryStatCard} ${styles[`registryStat${index}`]}`} key={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
          </Card>
        ))}
      </section>

      <Card className={styles.registryTableCard} bordered={false}>
        <Table
          className={styles.registryTableScroll}
          tableClassName={styles.registryTable}
          columns={registryColumns}
          data={visibleResults}
          pagination={false}
          size="small"
        />
      </Card>

      <section className={styles.registryPagination} aria-label="工具注册表分页">
        <span>
          显示第 {totalResults === 0 ? 0 : 1} 到 {Math.min(6, totalResults)} 条，共 {totalResults} 条结果
        </span>
        <div className={styles.registryPageButtons}>
          <Button
            className={styles.registryPageButton}
            disabled={page === 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            上一页
          </Button>
          {[1, 2, 3, 4].map((value) => (
            <Button
              className={`${styles.registryPageButton} ${page === value ? styles.registryPageActive : ''}`}
              key={value}
              onClick={() => setPage(value)}
            >
              {value}
            </Button>
          ))}
          <Button
            className={styles.registryPageButton}
            disabled={page === 4}
            onClick={() => setPage((current) => Math.min(4, current + 1))}
          >
            下一页
          </Button>
        </div>
      </section>

      <Dialog open={Boolean(selectedTool)} onOpenChange={(open) => !open && setSelectedTool(undefined)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{selectedTool?.name} 配置详情</DialogTitle>
            <DialogDescription>注册表展示工具契约；启停操作需要管理员确认并写入操作审计。</DialogDescription>
          </DialogHeader>
          {selectedTool ? (
            <div className={styles.registryDetailGrid}>
              <span>版本</span>
              <strong>{selectedTool.version}</strong>
              <span>状态</span>
              <strong>{registryStatusLabels[selectedTool.status] ?? selectedTool.status}</strong>
              <span>风险等级</span>
              <strong>{registryRiskLabels[selectedTool.risk] ?? selectedTool.risk}</strong>
              <span>权限范围</span>
              <strong>{selectedTool.scope}</strong>
              <span>超时</span>
              <strong>{selectedTool.timeoutMs}</strong>
              <span>重试策略</span>
              <strong>{selectedTool.retryPolicy}</strong>
              <span>入参 schema</span>
              <strong>{selectedTool.schema}</strong>
            </div>
          ) : null}
          <DialogFooter>
            {selectedTool ? (
              <Button
                className={styles.registryActionButton}
                disabled={operationStatus === 'submitting'}
                onClick={() => {
                  const target = selectedTool;
                  setSelectedTool(undefined);
                  onAction(createToolAction(target));
                }}
              >
                {selectedTool.status === 'active' ? '停用工具' : '启用工具'}
              </Button>
            ) : null}
            <Button onClick={() => setSelectedTool(undefined)}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

export function ToolsSection({
  onAction,
  operationStatus = 'idle',
  refreshNonce = 0,
}: {
  onAction: (payload: AdminActionPayload) => void;
  operationStatus?: AdminOperationState;
  refreshNonce?: number;
}) {
  const [searchParams] = useSearchParams();
  return searchParams.get('tab') === 'registry' ? (
    <ToolRegistrySection onAction={onAction} operationStatus={operationStatus} refreshNonce={refreshNonce} />
  ) : (
    <ToolCallsSection onAction={onAction} refreshNonce={refreshNonce} />
  );
}

function ToolCallsSection({
  onAction,
  refreshNonce = 0,
}: {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce?: number;
}) {
  const [tools, setTools] = useState<ToolRow[]>(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminToolRows);
  const [selectedTool, setSelectedTool] = useState<ToolRow | undefined>(tools[0]);
  useEffect(() => {
    if (import.meta.env.VITE_AGENT_MODE === 'real')
      loadAdminDashboard()
        .then((d) => {
          const rows = d.tools as ToolRow[];
          setTools(rows);
          setSelectedTool(rows[0]);
        })
        .catch(() => setTools([]));
  }, [refreshNonce]);

  const toolColumns: TableColumnProps<ToolRow>[] = [
    { title: '工具名', dataIndex: 'name' },
    { title: '版本', dataIndex: 'version' },
    { title: '范围', dataIndex: 'scope' },
    { title: '风险', dataIndex: 'risk', render: riskTag },
    { title: '状态', dataIndex: 'status', render: statusTag },
    {
      title: '操作',
      render: (_, record) => (
        <div className={styles.rowActions}>
          <Button size="mini" onClick={() => setSelectedTool(record)}>
            详情
          </Button>
          <Button
            size="mini"
            disabled={!canManage}
            onClick={() =>
              onAction({
                action: record.status === 'active' ? '停用工具' : '启用工具',
                targetLabel: record.name,
                targetType: 'tool',
                targetId: record.name,
                execute: async () => {
                  await updateAdminToolStatus(record.name, record.status === 'active' ? 'disabled' : 'active');
                },
                onApply: () => {
                  record.status = record.status === 'active' ? 'disabled' : 'active';
                },
              })
            }
          >
            启停
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <AdminFilters placeholder="toolName / risk / scope" />
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>工具注册表</strong>
            <Tag color="red">高风险工具仅 admin 可停用</Tag>
          </div>
          <Table columns={toolColumns} data={tools} pagination={{ pageSize: 6, total: tools.length }} size="small" />
        </Card>
        <aside className={styles.side}>
          {selectedTool ? <ToolDetailCard tool={selectedTool} /> : null}
          <OperationAuditCard />
        </aside>
      </section>
    </>
  );
}

function ToolDetailCard({ tool }: { tool: ToolRow }) {
  return (
    <Card className={styles.card} bordered={false}>
      <div className={styles.cardHead}>
        <strong>工具详情</strong>
        {riskTag(tool.risk)}
      </div>
      <div className={styles.detailGrid}>
        <span>名称</span>
        <strong>{tool.name}</strong>
        <span>版本</span>
        <strong>{tool.version}</strong>
        <span>负责人域</span>
        <strong>{tool.owner}</strong>
        <span>可用范围</span>
        <strong>{tool.scope}</strong>
        <span>入参 schema</span>
        <strong>{tool.schema}</strong>
        <span>最近调用</span>
        <strong>{tool.lastCalledAt}</strong>
      </div>
    </Card>
  );
}
