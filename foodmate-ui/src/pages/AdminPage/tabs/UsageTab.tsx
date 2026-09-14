import { AlertCircle, Copy, LoaderCircle, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import styles from '../AdminPage.module.css';
import { loadAdminUsagePage, type AdminUsageRow } from '../../../services/adminService';
import { isAbortError } from '../../../services/apiClient';
import type { AdminActionPayload } from './types';

type FigmaUsageRow = {
  key: string;
  time: string;
  runId: string;
  status: 'completed' | 'failed';
  provider: 'OpenAI' | 'Anthropic' | 'Postgres';
  model: string;
  scene: string;
  tokens: string;
  cost: string;
  latency: string;
};

const figmaUsageRows: FigmaUsageRow[] = [
  {
    key: 'figma-usage-1',
    time: '10:24:12',
    runId: 'run_98218a',
    status: 'completed',
    provider: 'OpenAI',
    model: 'gpt-4o',
    scene: 'Agent',
    tokens: '28k',
    cost: '$0.045',
    latency: '12.4s',
  },
  {
    key: 'figma-usage-2',
    time: '10:19:04',
    runId: 'run_774x2',
    status: 'completed',
    provider: 'Anthropic',
    model: 'claude-3.5',
    scene: 'Agent',
    tokens: '18k',
    cost: '$0.031',
    latency: '8.6s',
  },
  {
    key: 'figma-usage-3',
    time: '09:58:31',
    runId: 'run_889a4',
    status: 'completed',
    provider: 'OpenAI',
    model: 'gpt-4o',
    scene: 'Planner',
    tokens: '14k',
    cost: '$0.018',
    latency: '4.1s',
  },
  {
    key: 'figma-usage-4',
    time: '09:44:08',
    runId: 'run_552b1',
    status: 'completed',
    provider: 'OpenAI',
    model: 'text-embed',
    scene: 'RAG',
    tokens: '9k',
    cost: '$0.006',
    latency: '1.9s',
  },
  {
    key: 'figma-usage-5',
    time: '09:31:16',
    runId: 'run_133c9',
    status: 'failed',
    provider: 'Postgres',
    model: 'gpt-4o-mini',
    scene: 'SQL',
    tokens: '6k',
    cost: '$0.055',
    latency: '21.0s',
  },
  {
    key: 'figma-usage-6',
    time: '09:12:44',
    runId: 'run_908d1',
    status: 'completed',
    provider: 'OpenAI',
    model: 'gpt-4o',
    scene: 'Memory',
    tokens: '4k',
    cost: '$0.012',
    latency: '3.2s',
  },
];

type UsageFilter = 'all' | 'completed' | 'failed';

function UsageFilterSelect({
  label,
  value,
  onValueChange,
  options,
  ariaLabel,
}: {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  options: Array<{ value: string; label: string }>;
  ariaLabel: string;
}) {
  return (
    <Select value={value} onValueChange={onValueChange}>
      <SelectTrigger className={styles.usageFilter} aria-label={ariaLabel}>
        <span className={styles.usageFilterLabel}>{label}:</span>
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

function emitUsageNotice(message: string) {
  window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone: 'info' } }));
}

function UsageStatus({ status }: { status: string }) {
  const normalizedStatus = status.toLowerCase();
  const isFailed = ['failed', 'timeout', 'cancelled'].includes(normalizedStatus);
  return <span className={`${styles.usageStatus} ${isFailed ? styles.usageStatusFailed : ''}`}>{status}</span>;
}

function UsageProvider({ provider, rowIndex }: { provider: string; rowIndex: number }) {
  const providerClass =
    provider.toLowerCase() === 'anthropic'
      ? styles.usageProviderAnthropic
      : provider.toLowerCase() === 'postgres'
        ? styles.usageProviderPostgres
        : rowIndex === 2 || rowIndex === 5
          ? styles.usageProviderOpenAiWarm
          : styles.usageProviderOpenAi;
  return <span className={`${styles.usageProvider} ${providerClass}`}>{provider}</span>;
}

function FigmaUsageSection() {
  const [result, setResult] = useState<UsageFilter>('all');
  const [provider, setProvider] = useState('all');
  const [model, setModel] = useState('all');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [copiedRunId, setCopiedRunId] = useState('');

  const filteredRows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return figmaUsageRows.filter((row) => {
      const matchesResult = result === 'all' || row.status === result;
      const matchesProvider = provider === 'all' || row.provider === provider;
      const matchesModel = model === 'all' || row.model === model;
      const matchesSearch =
        !query ||
        [row.time, row.runId, row.provider, row.model, row.scene].some((value) => value.toLowerCase().includes(query));
      return matchesResult && matchesProvider && matchesModel && matchesSearch;
    });
  }, [model, provider, result, search]);

  const pageSize = figmaUsageRows.length;
  const totalResultCount = 12480;
  const hasFilter = result !== 'all' || provider !== 'all' || model !== 'all' || Boolean(search.trim());
  const resultCount = hasFilter ? filteredRows.length : totalResultCount;
  const totalPages = Math.max(1, Math.ceil(resultCount / pageSize));
  const currentPage = Math.min(page, totalPages);
  const visibleRows = currentPage === 1 ? filteredRows : [];
  const rangeStart = resultCount === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const rangeEnd = Math.min(currentPage * pageSize, resultCount);

  const changeFilter = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(1);
  };

  const copyRunId = async (runId: string) => {
    try {
      await navigator.clipboard?.writeText(runId);
    } catch {
      // 嵌入式页面或非安全预览环境可能无法访问剪贴板。
    }
    setCopiedRunId(runId);
    window.setTimeout(() => setCopiedRunId(''), 1600);
  };

  return (
    <div className={styles.usageFigmaPage}>
      <section className={styles.usageFilters} aria-label="模型用量筛选">
        <div className={styles.usageFilterGroup}>
          <UsageFilterSelect
            label="结果"
            value={result}
            onValueChange={(value) => {
              setResult(value as UsageFilter);
              setPage(1);
            }}
            ariaLabel="结果筛选"
            options={[
              { value: 'all', label: '全部' },
              { value: 'completed', label: '完成' },
              { value: 'failed', label: '失败' },
            ]}
          />
          <UsageFilterSelect
            label="供应商"
            value={provider}
            onValueChange={changeFilter(setProvider)}
            ariaLabel="供应商筛选"
            options={[
              { value: 'all', label: '全部' },
              { value: 'OpenAI', label: 'OpenAI' },
              { value: 'Anthropic', label: 'Anthropic' },
              { value: 'Postgres', label: 'Postgres' },
            ]}
          />
          <UsageFilterSelect
            label="模型"
            value={model}
            onValueChange={changeFilter(setModel)}
            ariaLabel="模型筛选"
            options={[
              { value: 'all', label: '全部' },
              { value: 'gpt-4o', label: 'gpt-4o' },
              { value: 'claude-3.5', label: 'claude-3.5' },
              { value: 'text-embed', label: 'text-embed' },
              { value: 'gpt-4o-mini', label: 'gpt-4o-mini' },
            ]}
          />
        </div>
        <div className={styles.usageSearch}>
          <span className={styles.usageSearchIcon} data-figma-asset="admin-overview-search" aria-hidden="true" />
          <Input
            aria-label="时间 / 场景 / 模型 / Run ID..."
            className={styles.usageSearchInput}
            placeholder="时间 / 场景 / 模型 / Run ID..."
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
          />
        </div>
      </section>

      <section className={styles.usageStats} aria-label="模型用量统计">
        <Card className={styles.usageStatCard}>
          <span>输入 Token</span>
          <strong>12.4M</strong>
        </Card>
        <Card className={`${styles.usageStatCard} ${styles.usageStatOutput}`}>
          <span>输出 Token</span>
          <strong>3.7M</strong>
        </Card>
        <Card className={`${styles.usageStatCard} ${styles.usageStatCost}`}>
          <span>总成本</span>
          <strong>$128.45</strong>
        </Card>
      </section>

      <section className={styles.usageTableCard} aria-label="模型用量明细">
        <div className={styles.usageTableScroll}>
          <Table className={styles.usageTable}>
            <TableHeader>
              <TableRow>
                <TableHead>调用时间</TableHead>
                <TableHead>Run ID</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>供应商</TableHead>
                <TableHead>模型</TableHead>
                <TableHead>场景</TableHead>
                <TableHead>Token</TableHead>
                <TableHead>成本</TableHead>
                <TableHead>耗时</TableHead>
                <TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visibleRows.length ? (
                visibleRows.map((row, rowIndex) => (
                  <TableRow key={row.key}>
                    <TableCell>
                      <span className={styles.usageTimeCell}>
                        <strong>{row.time}</strong>
                        <Button
                          variant="ghost"
                          size="icon"
                          className={styles.usageCopyButton}
                          aria-label={`复制 ${row.runId}`}
                          onClick={() => void copyRunId(row.runId)}
                        >
                          <Copy aria-hidden="true" />
                        </Button>
                      </span>
                    </TableCell>
                    <TableCell className={styles.usageMonoMuted}>{row.runId}</TableCell>
                    <TableCell>
                      <UsageStatus status={row.status} />
                    </TableCell>
                    <TableCell>
                      <UsageProvider provider={row.provider} rowIndex={rowIndex} />
                    </TableCell>
                    <TableCell className={styles.usageMono}>{row.model}</TableCell>
                    <TableCell className={styles.usageCellMuted}>{row.scene}</TableCell>
                    <TableCell className={styles.usageMono}>{row.tokens}</TableCell>
                    <TableCell className={styles.usageCellMuted}>{row.cost}</TableCell>
                    <TableCell className={row.status === 'failed' ? styles.usageLatencyFailed : styles.usageLatency}>
                      {row.latency}
                    </TableCell>
                    <TableCell className={styles.usageActionCell}>
                      <Button
                        variant="outline"
                        className={styles.usageRunButton}
                        onClick={() => emitUsageNotice(`已选择 ${row.runId}，可从 Run 详情查看。`)}
                      >
                        查看 Run
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={10} className={styles.usageEmpty}>
                    没有匹配的模型用量记录
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </section>

      <nav className={styles.usagePagination} aria-label="模型用量分页">
        <p>
          显示第 {rangeStart} 到 {rangeEnd} 条，共 {totalResultCount.toLocaleString('en-US')} 条结果
        </p>
        <div className={styles.usagePageButtons}>
          <Button
            variant="outline"
            className={styles.usagePageButton}
            disabled={currentPage === 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            上一页
          </Button>
          {[1, 2, 3, 4].map((pageNumber) => (
            <Button
              key={pageNumber}
              variant={currentPage === pageNumber ? 'default' : 'outline'}
              className={`${styles.usagePageButton} ${currentPage === pageNumber ? styles.usagePageButtonActive : ''}`}
              onClick={() => setPage(pageNumber)}
            >
              {pageNumber}
            </Button>
          ))}
          <Button
            variant="outline"
            className={styles.usagePageButton}
            disabled={currentPage === totalPages}
            onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
          >
            下一页
          </Button>
        </div>
      </nav>

      <Card className={styles.usageAnalytics} aria-label="模型用量分析">
        <article>
          <h2>成本 / Token 趋势</h2>
          <p>输入 12.4M · 输出 3.7M</p>
          <p>本周成本 $682.40 · 环比 -8.2%</p>
        </article>
        <article>
          <h2>供应商占比</h2>
          <p>OpenAI 62% · Anthropic 28%</p>
          <p>Postgres / 其他 10% · 可按时间筛选</p>
        </article>
        <article>
          <h2>场景排行</h2>
          <p>Agent 54% · RAG 26%</p>
          <p>Planner 12% · SQL 8% · 1,420 次调用 · 均值 8.4s · 查看价格版本</p>
        </article>
      </Card>

      {copiedRunId ? (
        <p className={styles.usageCopyNotice} role="status">
          已复制 {copiedRunId}
        </p>
      ) : null}
    </div>
  );
}

export function UsageSection({
  refreshNonce,
}: {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce: number;
}) {
  if (import.meta.env.VITE_AGENT_MODE === 'real') {
    return <RealUsageSection refreshNonce={refreshNonce} />;
  }

  return <FigmaUsageSection />;
}

type RealUsageFilter = 'all' | 'success' | 'failed' | 'timeout' | 'cancelled';

function parseTokenValue(value: string) {
  const matched = value.trim().match(/^([\d,.]+)\s*([kKmMbB])?$/);
  if (!matched) return undefined;
  const number = Number(matched[1].replaceAll(',', ''));
  if (!Number.isFinite(number)) return undefined;
  const multiplier = matched[2]?.toLowerCase() === 'k' ? 1_000 : matched[2]?.toLowerCase() === 'm' ? 1_000_000 : 1;
  return number * multiplier;
}

function formatTokenTotal(rows: AdminUsageRow[]) {
  const values = rows.map((row) => parseTokenValue(row.tokens)).filter((value): value is number => value != null);
  if (!values.length) return '-';
  const total = values.reduce((sum, value) => sum + value, 0);
  if (total >= 1_000_000) return `${(total / 1_000_000).toFixed(2)}M`;
  if (total >= 1_000) return `${(total / 1_000).toFixed(2)}K`;
  return total.toLocaleString('en-US');
}

function formatCostTotal(rows: AdminUsageRow[]) {
  const values = rows.map((row) => Number(row.cost)).filter((value) => Number.isFinite(value));
  if (!values.length) return '-';
  return values.reduce((sum, value) => sum + value, 0).toFixed(2);
}

function pageNumbers(totalPages: number, currentPage: number) {
  const start = Math.min(Math.max(1, currentPage - 2), Math.max(1, totalPages - 4));
  const end = Math.min(totalPages, start + 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
}

function RealUsageSection({ refreshNonce = 0 }: { refreshNonce?: number }) {
  const pageSize = 20;
  const [rows, setRows] = useState<AdminUsageRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<RealUsageFilter>('all');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [retryNonce, setRetryNonce] = useState(0);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    // 每次筛选、分页或刷新都重新读取权威接口，失败时清空当前结果，禁止回退到 Fixture。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setLoadError('');
    void loadAdminUsagePage(
      {
        page,
        size: pageSize,
        query: search.trim() || undefined,
        status: result === 'all' ? undefined : result,
      },
      controller.signal,
    )
      .then((data) => {
        if (!active || controller.signal.aborted) return;
        setRows(data.items);
        setTotal(data.total);
        setPage(data.page);
      })
      .catch((error) => {
        if (!active || controller.signal.aborted || isAbortError(error)) return;
        setRows([]);
        setTotal(0);
        setLoadError(error instanceof Error ? error.message : '模型用量加载失败');
      })
      .finally(() => {
        if (active && !controller.signal.aborted) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [page, refreshNonce, result, retryNonce, search]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const currentPage = Math.min(page, totalPages);
  const rangeStart = total === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const rangeEnd = Math.min(currentPage * pageSize, total);
  const summary = useMemo(
    () => ({
      tokens: formatTokenTotal(rows),
      cost: formatCostTotal(rows),
      successCount: rows.filter((row) => ['success', 'completed'].includes(row.status.toLowerCase())).length,
      failedCount: rows.filter((row) => ['failed', 'timeout', 'cancelled'].includes(row.status.toLowerCase())).length,
    }),
    [rows],
  );

  const updateResult = (value: string) => {
    setResult(value as RealUsageFilter);
    setPage(1);
  };

  const updateSearch = (value: string) => {
    setSearch(value);
    setPage(1);
  };

  return (
    <div className={styles.usageFigmaPage} data-usage-mode="real">
      <section className={styles.usageFilters} aria-label="模型用量筛选">
        <div className={styles.usageFilterGroup}>
          <UsageFilterSelect
            label="结果"
            value={result}
            onValueChange={updateResult}
            ariaLabel="结果筛选"
            options={[
              { value: 'all', label: '全部' },
              { value: 'success', label: '成功' },
              { value: 'failed', label: '失败' },
              { value: 'timeout', label: '超时' },
              { value: 'cancelled', label: '已取消' },
            ]}
          />
        </div>
        <div className={styles.usageSearch}>
          <span className={styles.usageSearchIcon} data-figma-asset="admin-overview-search" aria-hidden="true" />
          <Input
            aria-label="供应商 / 模型 / 场景"
            className={styles.usageSearchInput}
            placeholder="供应商 / 模型 / 场景..."
            value={search}
            onChange={(event) => updateSearch(event.target.value)}
          />
        </div>
      </section>

      <section className={styles.usageStats} aria-label="模型用量统计">
        <Card className={styles.usageStatCard}>
          <span>当前页 Token</span>
          <strong>{summary.tokens}</strong>
        </Card>
        <Card className={`${styles.usageStatCard} ${styles.usageStatOutput}`}>
          <span>当前页成本</span>
          <strong>{summary.cost}</strong>
        </Card>
        <Card className={`${styles.usageStatCard} ${styles.usageStatCost}`}>
          <span>记录总数</span>
          <strong>{total.toLocaleString('zh-CN')}</strong>
        </Card>
      </section>

      <section className={styles.usageTableCard} aria-label="模型用量明细" aria-busy={loading}>
        <div className={styles.usageTableScroll}>
          <Table className={styles.usageTable}>
            <TableHeader>
              <TableRow>
                <TableHead>供应商</TableHead>
                <TableHead>模型</TableHead>
                <TableHead>场景</TableHead>
                <TableHead>Token</TableHead>
                <TableHead>成本</TableHead>
                <TableHead>耗时</TableHead>
                <TableHead>状态</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && !rows.length ? (
                <TableRow>
                  <TableCell colSpan={7} className={styles.usageEmpty}>
                    <span className={styles.usageLoadingState} role="status">
                      <LoaderCircle aria-hidden="true" />
                      正在加载模型用量...
                    </span>
                  </TableCell>
                </TableRow>
              ) : loadError ? (
                <TableRow>
                  <TableCell colSpan={7} className={styles.usageEmpty}>
                    <span className={styles.usageErrorState} role="alert">
                      <span className={styles.usageErrorMessage}>
                        <AlertCircle aria-hidden="true" />
                        {loadError}
                      </span>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setRetryNonce((value) => value + 1)}
                      >
                        <RefreshCw aria-hidden="true" />
                        重试
                      </Button>
                    </span>
                  </TableCell>
                </TableRow>
              ) : rows.length ? (
                rows.map((row, rowIndex) => (
                  <TableRow key={row.key}>
                    <TableCell>
                      <UsageProvider provider={row.provider} rowIndex={rowIndex} />
                    </TableCell>
                    <TableCell className={styles.usageMono}>{row.model}</TableCell>
                    <TableCell className={styles.usageCellMuted}>{row.scene}</TableCell>
                    <TableCell className={styles.usageMono}>{row.tokens}</TableCell>
                    <TableCell className={styles.usageCellMuted}>{row.cost}</TableCell>
                    <TableCell
                      className={
                        row.status.toLowerCase() === 'failed' ? styles.usageLatencyFailed : styles.usageLatency
                      }
                    >
                      {row.latencyMs > 0 ? `${row.latencyMs} ms` : '-'}
                    </TableCell>
                    <TableCell>
                      <UsageStatus status={row.status} />
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={7} className={styles.usageEmpty}>
                    没有匹配的模型用量记录
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </section>

      <nav className={styles.usagePagination} aria-label="模型用量分页">
        <p>
          显示第 {rangeStart} 到 {rangeEnd} 条，共 {total.toLocaleString('en-US')} 条结果
        </p>
        <div className={styles.usagePageButtons}>
          <Button
            variant="outline"
            className={styles.usagePageButton}
            aria-label="上一页"
            disabled={currentPage === 1 || loading}
            onClick={() => setPage((value) => Math.max(1, value - 1))}
          >
            上一页
          </Button>
          {pageNumbers(totalPages, currentPage).map((pageNumber) => (
            <Button
              key={pageNumber}
              variant={currentPage === pageNumber ? 'default' : 'outline'}
              className={`${styles.usagePageButton} ${currentPage === pageNumber ? styles.usagePageButtonActive : ''}`}
              aria-label={`第 ${pageNumber} 页`}
              disabled={loading}
              onClick={() => setPage(pageNumber)}
            >
              {pageNumber}
            </Button>
          ))}
          <Button
            variant="outline"
            className={styles.usagePageButton}
            aria-label="下一页"
            disabled={currentPage === totalPages || loading}
            onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
          >
            下一页
          </Button>
        </div>
      </nav>

      <Card className={styles.usageAnalytics} aria-label="模型用量分析">
        <article>
          <h2>当前查询 Token</h2>
          <p>
            {summary.tokens} · 当前页已返回 {rows.length} 条
          </p>
          <p>输入和输出拆分由后端明细接口决定，当前接口只提供总 Token。</p>
        </article>
        <article>
          <h2>状态分布</h2>
          <p>
            成功 {summary.successCount} 条 · 失败 {summary.failedCount} 条
          </p>
          <p>统计范围为当前页返回记录。</p>
        </article>
        <article>
          <h2>查询条件</h2>
          <p>
            {result === 'all' ? '全部结果' : result} · {search.trim() || '无关键词'}
          </p>
          <p>总计 {total.toLocaleString('zh-CN')} 条记录。</p>
        </article>
      </Card>
    </div>
  );
}
