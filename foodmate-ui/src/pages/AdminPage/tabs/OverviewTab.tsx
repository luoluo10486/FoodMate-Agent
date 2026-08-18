import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Copy, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input as ShadcnInput } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ROUTES } from '../../../constants/routes';
import { loadAdminDashboard, type AdminRunRow } from '../../../services/adminService';
import { adminOverviewMetrics, adminOverviewRows } from './AdminShared';
import styles from '../AdminPage.module.css';

type OverviewMetric = {
  label: string;
  value: string;
  hint?: string;
};

type OverviewRow = {
  key: string;
  runId: string;
  user: string;
  status: string;
  stage: string;
  duration: string;
  cost: string;
  toolCount: string;
  result: string;
  errorCode: string;
};

const overviewMetrics: OverviewMetric[] = adminOverviewMetrics;
const overviewRows: OverviewRow[] = adminOverviewRows;

function apiRowsToOverviewRows(rows: AdminRunRow[]): OverviewRow[] {
  return rows.map((row) => ({
    key: row.key,
    runId: row.runId.startsWith('run_') ? row.runId : `run_${row.runId}`,
    user: row.user,
    status: row.status,
    stage: row.intent.toUpperCase(),
    duration: `${(row.durationMs / 1000).toFixed(1)}s`,
    cost: row.intent.toUpperCase(),
    toolCount: String(row.toolCalls ?? 0),
    result: row.status,
    errorCode: row.status === 'failed' ? 'RUN_FAILED' : '-',
  }));
}

function OverviewPill({ value, tone }: { value: string; tone: 'green' | 'coral' | 'amber' | 'neutral' | 'teal' }) {
  return (
    <span className={`${styles.overviewPill} ${styles[`overviewPill${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
      {value}
    </span>
  );
}

function statusTone(value: string): 'green' | 'neutral' {
  return value === 'completed' ? 'green' : 'neutral';
}

function stageTone(value: string): 'coral' | 'amber' | 'teal' {
  if (value === 'COMPOSE') return 'coral';
  if (value === 'PLAN') return 'amber';
  return 'teal';
}

function OverviewFilterSelect({
  label,
  value,
  options,
  onChange,
  ariaLabel,
}: {
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
  ariaLabel: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={styles.overviewFilter} aria-label={ariaLabel}>
        <span className={styles.overviewFilterLabel}>{label}:</span>
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

function copyRunId(runId: string) {
  if (navigator.clipboard) void navigator.clipboard.writeText(runId);
}

export function OverviewSection({ refreshNonce = 0 }: { onAction?: unknown; refreshNonce?: number }) {
  const [metrics, setMetrics] = useState<OverviewMetric[]>(overviewMetrics);
  const [rows, setRows] = useState<OverviewRow[]>(overviewRows);
  const [resultFilter, setResultFilter] = useState('all');
  const [degradedFilter, setDegradedFilter] = useState('all');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);

  useEffect(() => {
    if (import.meta.env.VITE_AGENT_MODE !== 'real') return;
    loadAdminDashboard()
      .then((dashboard) => {
        setMetrics(dashboard.overview_metrics.slice(0, 3));
        setRows(apiRowsToOverviewRows(dashboard.runs));
      })
      .catch(() => {
        setMetrics([]);
        setRows([]);
      });
  }, [refreshNonce]);

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return rows.filter((row) => {
      const matchesResult = resultFilter === 'all' || row.result === resultFilter;
      const matchesDegraded =
        degradedFilter === 'all' || (degradedFilter === 'yes' ? row.errorCode !== '-' : row.errorCode === '-');
      const matchesQuery = !normalizedQuery || `${row.runId} ${row.user}`.toLowerCase().includes(normalizedQuery);
      return matchesResult && matchesDegraded && matchesQuery;
    });
  }, [degradedFilter, query, resultFilter, rows]);

  return (
    <>
      <section className={styles.overviewFilters} aria-label="管理概览筛选">
        <div className={styles.overviewFilterGroup}>
          <OverviewFilterSelect
            label="时间"
            value="all"
            options={[
              { value: 'all', label: '全部' },
              { value: '24h', label: '近 24h' },
              { value: '7d', label: '近 7 天' },
              { value: '30d', label: '近 30 天' },
            ]}
            onChange={() => undefined}
            ariaLabel="时间范围"
          />
          <OverviewFilterSelect
            label="结果"
            value={resultFilter}
            options={[
              { value: 'all', label: '全部' },
              { value: 'completed', label: 'completed' },
              { value: 'running', label: 'running' },
              { value: 'failed', label: 'failed' },
            ]}
            onChange={(value) => {
              setResultFilter(value);
              setPage(1);
            }}
            ariaLabel="结果筛选"
          />
          <OverviewFilterSelect
            label="是否降级"
            value={degradedFilter}
            options={[
              { value: 'all', label: '全部' },
              { value: 'yes', label: '是' },
              { value: 'no', label: '否' },
            ]}
            onChange={(value) => {
              setDegradedFilter(value);
              setPage(1);
            }}
            ariaLabel="降级筛选"
          />
        </div>
        <label className={styles.overviewSearch}>
          <Search aria-hidden="true" />
          <ShadcnInput
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
            placeholder="搜索运行 / 用户..."
            aria-label="搜索运行或用户"
          />
        </label>
      </section>

      <section className={styles.overviewStats} aria-label="管理概览指标">
        {metrics.slice(0, 3).map((metric, index) => (
          <Card className={`${styles.overviewStatCard} ${styles[`overviewStat${index}`]}`} key={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
          </Card>
        ))}
      </section>

      <Card className={styles.overviewTableCard}>
        <div className={styles.overviewTableScroll}>
          <Table className={styles.overviewTable}>
            <TableHeader>
              <TableRow>
                {['运行 ID', '用户', '状态', '阶段', '耗时', '成本', '工具数', '结果', '错误码', '操作'].map(
                  (title) => (
                    <TableHead key={title}>{title}</TableHead>
                  ),
                )}
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredRows.slice(0, 6).map((row) => (
                <TableRow key={row.key}>
                  <TableCell>
                    <span className={styles.overviewRunIdCell}>
                      <strong>{row.runId}</strong>
                      <button
                        className={styles.copyButton}
                        type="button"
                        aria-label={`复制 ${row.runId}`}
                        onClick={() => copyRunId(row.runId)}
                        title={`复制 ${row.runId}`}
                      >
                        <Copy aria-hidden="true" />
                      </button>
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className={styles.overviewMonoMuted}>{row.user}</span>
                  </TableCell>
                  <TableCell>
                    <OverviewPill value={row.status} tone={statusTone(row.status)} />
                  </TableCell>
                  <TableCell>
                    <OverviewPill value={row.stage} tone={stageTone(row.stage)} />
                  </TableCell>
                  <TableCell>
                    <span className={styles.overviewMono}>{row.duration}</span>
                  </TableCell>
                  <TableCell>
                    <span className={styles.overviewCellMuted}>{row.cost}</span>
                  </TableCell>
                  <TableCell>
                    <span className={styles.overviewMono}>{row.toolCount}</span>
                  </TableCell>
                  <TableCell>
                    <span className={styles.overviewCellMuted}>{row.result}</span>
                  </TableCell>
                  <TableCell>
                    <span className={styles.overviewErrorCode}>{row.errorCode}</span>
                  </TableCell>
                  <TableCell>
                    <Link
                      className={styles.overviewActionButton}
                      to={`${ROUTES.ADMIN}/runs?run=${encodeURIComponent(row.runId)}`}
                    >
                      查看详情
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </Card>

      <section className={styles.overviewPagination} aria-label="运行结果分页">
        <span>
          显示第 {(page - 1) * 6 + 1} 到 {Math.min(page * 6, 12480)} 条，共 12,480 条结果
        </span>
        <div className={styles.overviewPageButtons}>
          <Button
            className={styles.overviewPageButton}
            disabled={page === 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            上一页
          </Button>
          {[1, 2, 3, 4].map((value) => (
            <Button
              className={`${styles.overviewPageButton} ${page === value ? styles.overviewPageActive : ''}`}
              key={value}
              onClick={() => setPage(value)}
            >
              {value}
            </Button>
          ))}
          <Button
            className={styles.overviewPageButton}
            disabled={page === 4}
            onClick={() => setPage((current) => Math.min(4, current + 1))}
          >
            下一页
          </Button>
        </div>
      </section>

      <Card className={styles.overviewAnalytics}>
        <article>
          <h2>运行趋势</h2>
          <p>近 24h 1,284 次 · 成功率 91.4%</p>
          <p>P50 4.2s · P95 18.6s · P99 42.1s</p>
          <p>模型 Token 16.1M · 平均耗时 8.4s</p>
        </article>
        <article>
          <h2>失败原因分布</h2>
          <p>模型限制 42% · 工具超时 31%</p>
          <p>策略拒绝 18% · 其他 9%</p>
        </article>
        <article>
          <h2>健康与审计</h2>
          <p>工具 24 个 · 3 个高风险 · 知识库索引 92%</p>
          <p>最近管理操作 4 条待复核 · 取消率 2.1%</p>
        </article>
      </Card>
    </>
  );
}
