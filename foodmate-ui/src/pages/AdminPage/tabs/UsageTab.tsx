import { Copy } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import styles from '../AdminPage.module.css';
import { ModelGovernanceSection } from './ModelGovernanceTab';
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

function UsageStatus({ status }: { status: FigmaUsageRow['status'] }) {
  return (
    <span className={`${styles.usageStatus} ${status === 'failed' ? styles.usageStatusFailed : ''}`}>{status}</span>
  );
}

function UsageProvider({ provider, rowIndex }: { provider: FigmaUsageRow['provider']; rowIndex: number }) {
  const providerClass =
    provider === 'Anthropic'
      ? styles.usageProviderAnthropic
      : provider === 'Postgres'
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
      // Clipboard access can be unavailable in embedded or insecure preview contexts.
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
  onAction,
  refreshNonce,
}: {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce: number;
}) {
  if (import.meta.env.VITE_AGENT_MODE === 'real') {
    return <ModelGovernanceSection onAction={onAction} refreshNonce={refreshNonce} />;
  }

  return <FigmaUsageSection />;
}
