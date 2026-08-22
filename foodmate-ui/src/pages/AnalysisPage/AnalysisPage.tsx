import { useState } from 'react';
import { AlertTriangle, ChartColumn } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import styles from './AnalysisPage.module.css';

type RangeKey = '7d' | '30d' | '90d';
type AnalysisState = 'default' | 'loading' | 'empty' | 'error';

const ranges: Array<{ key: RangeKey; label: string }> = [
  { key: '7d', label: '7 天' },
  { key: '30d', label: '30 天' },
  { key: '90d', label: '90 天' },
];

const rangeData: Record<
  RangeKey,
  { calories: string; protein: string; activeDays: string; bars: number[]; miniBars: number[] }
> = {
  '7d': {
    calories: '1,940 kcal',
    protein: '114 g',
    activeDays: '6 / 7 Days',
    bars: [52, 78, 64, 96, 88, 112, 74],
    miniBars: [12, 18, 8, 22],
  },
  '30d': {
    calories: '1,896 kcal',
    protein: '109 g',
    activeDays: '26 / 30 Days',
    bars: [72, 86, 64, 104, 92, 118, 82],
    miniBars: [10, 16, 12, 22],
  },
  '90d': {
    calories: '1,872 kcal',
    protein: '106 g',
    activeDays: '79 / 90 Days',
    bars: [62, 94, 76, 110, 86, 116, 98],
    miniBars: [14, 20, 10, 22],
  },
};

function MiniBars({ bars }: { bars: number[] }) {
  return (
    <div className={styles.miniBars} aria-hidden="true">
      {bars.map((height, index) => (
        <span key={`${height}-${index}`} style={{ height: `${height}px` }} />
      ))}
    </div>
  );
}

function getAnalysisState(value: string | null): AnalysisState {
  return value === 'loading' || value === 'empty' || value === 'error' ? value : 'default';
}

function LoadingMetrics() {
  const skeletons = [
    { label: '日均能量', value: 'metricSkeletonWide', detail: 'metricDetailWide' },
    { label: '日均蛋白质', value: 'metricSkeletonMedium', detail: 'metricDetailMedium' },
    { label: '活跃记录天数', value: 'metricSkeletonNarrow', detail: 'metricDetailNarrow' },
  ] as const;

  return (
    <section className={styles.metrics} aria-label="分析摘要加载中" aria-busy="true">
      {skeletons.map((item) => (
        <article className={styles.metricCard} key={item.label}>
          <span>{item.label}</span>
          <Skeleton className={`${styles.metricSkeleton} ${styles[item.value]}`} />
          <Skeleton className={`${styles.metricDetailSkeleton} ${styles[item.detail]}`} />
        </article>
      ))}
    </section>
  );
}

function LoadingAnalysis() {
  return (
    <>
      <LoadingMetrics />
      <section className={styles.chartCard} aria-label="能量摄入分析加载中" aria-busy="true">
        <h2>能量摄入与目标对比</h2>
        <div className={styles.loadingChartArea}>
          <Skeleton className={styles.loadingChartSkeleton} />
        </div>
      </section>
      <section className={styles.insightCard} aria-label="营养洞察加载中" aria-busy="true">
        <h2>营养洞察（由 Agent 生成）</h2>
        <div className={styles.loadingInsightList}>
          {Array.from({ length: 3 }, (_, index) => (
            <div className={styles.loadingInsightRow} key={index}>
              <span className={styles.loadingInsightDot} />
              <Skeleton className={styles.loadingInsightSkeleton} />
            </div>
          ))}
        </div>
        <div className={styles.loadingInsightActions}>
          <Skeleton className={styles.loadingActionPrimary} />
          <Skeleton className={styles.loadingActionSecondary} />
        </div>
      </section>
    </>
  );
}

function EmptyAnalysis({ onRecord }: { onRecord: () => void }) {
  return (
    <>
      <section className={styles.metrics} aria-label="分析摘要">
        <article className={styles.metricCard}>
          <span>日均能量</span>
          <strong>-</strong>
        </article>
        <article className={styles.metricCard}>
          <span>日均蛋白质</span>
          <strong>-</strong>
        </article>
        <article className={styles.metricCard}>
          <span>活跃记录天数</span>
          <strong>0 / 7 Days</strong>
        </article>
      </section>
      <section className={styles.emptyChartCard} aria-labelledby="empty-analysis-title">
        <h2 id="empty-analysis-title">能量摄入与目标对比</h2>
        <div className={styles.emptyChartArea}>
          <div className={styles.emptyStateIcon}>
            <ChartColumn aria-hidden="true" />
          </div>
          <div className={styles.stateCopy}>
            <h3>数据不足，无法生成分析</h3>
            <p>至少需要 3 天的饮食记录才能生成趋势分析</p>
          </div>
          <Button className={styles.recordButton} onClick={onRecord}>
            去记录饮食
          </Button>
        </div>
      </section>
    </>
  );
}

function ErrorAnalysis({ onReload }: { onReload: () => void }) {
  return (
    <section className={styles.errorCard} role="alert" aria-label="分析数据加载失败">
      <div className={styles.errorStateIcon}>
        <AlertTriangle aria-hidden="true" />
      </div>
      <div className={styles.stateCopy}>
        <h3>分析数据加载失败</h3>
        <p>获取营养趋势数据时出错，请稍后重试</p>
      </div>
      <Button className={styles.reloadButton} variant="outline" onClick={onReload}>
        重新加载
      </Button>
    </section>
  );
}

export function AnalysisPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const analysisState = getAnalysisState(searchParams.get('state'));
  const isFigmaFixture = searchParams.get('state') === 'v2';
  const [range, setRange] = useState<RangeKey>('7d');
  const [notice, setNotice] = useState('');
  const data = rangeData[range];

  const exportCsv = () => {
    setNotice('分析报告已排队，完成后可下载 CSV。');
  };

  const reloadAnalysis = () => {
    setSearchParams({});
    setNotice('正在重新加载摄入分析。');
  };

  return (
    <WorkspaceLayout
      activeModule="analysis"
      displayNameOverride={isFigmaFixture ? 'Anddy' : undefined}
      profileIdOverride={isFigmaFixture ? '1234567' : undefined}
    >
      <div className={styles.page}>
        <section className={styles.analysisBody} aria-label="摄入分析">
          <header className={`${styles.filterRow} ${analysisState === 'loading' ? styles.stateFilterRow : ''}`}>
            <div className={styles.filters} role="tablist" aria-label="分析范围">
              {ranges.map((item) => (
                <Button
                  variant="ghost"
                  className={range === item.key ? styles.rangeActive : ''}
                  key={item.key}
                  type="button"
                  role="tab"
                  aria-selected={range === item.key}
                  onClick={() => setRange(item.key)}
                  disabled={analysisState === 'loading' || analysisState === 'error'}
                >
                  {item.label}
                </Button>
              ))}
              <button
                className={styles.filterPill}
                type="button"
                onClick={() => setNotice('自定义范围将在真实记录接入后启用。')}
                disabled={analysisState === 'loading' || analysisState === 'error'}
              >
                自定义范围
              </button>
              <button
                className={styles.filterPill}
                type="button"
                onClick={() => setNotice('当前分析覆盖全部餐次。')}
                disabled={analysisState === 'loading' || analysisState === 'error'}
              >
                全部餐次
              </button>
            </div>
            <Button
              className={styles.exportButton}
              variant="ghost"
              onClick={exportCsv}
              disabled={analysisState !== 'default'}
            >
              导出 CSV
            </Button>
          </header>

          {analysisState === 'loading' ? <LoadingAnalysis /> : null}
          {analysisState === 'empty' ? <EmptyAnalysis onRecord={() => navigate('/analysis?view=records')} /> : null}
          {analysisState === 'error' ? <ErrorAnalysis onReload={reloadAnalysis} /> : null}
          {analysisState === 'default' ? (
            <section className={styles.metrics} aria-label="分析摘要">
              <article className={styles.metricCard}>
                <span>日均能量</span>
                <div className={styles.metricValueRow}>
                  <strong>{data.calories}</strong>
                  <MiniBars bars={data.miniBars} />
                </div>
              </article>
              <article className={styles.metricCard}>
                <span>日均蛋白质</span>
                <strong>{data.protein}</strong>
              </article>
              <article className={styles.metricCard}>
                <span>活跃记录天数</span>
                <strong>{data.activeDays}</strong>
              </article>
            </section>
          ) : null}

          {analysisState === 'default' ? (
            <section className={styles.chartCard} aria-labelledby="calorie-chart-title">
              <h2 id="calorie-chart-title">能量摄入与目标对比</h2>
              <div className={styles.chartArea}>
                <div className={styles.legend} aria-label="图例">
                  <span>
                    <i className={styles.actualDot} />
                    实际摄入
                  </span>
                  <span>
                    <i className={styles.targetDot} />
                    目标对比
                  </span>
                </div>
                <div
                  className={styles.barChart}
                  role="img"
                  aria-label={`${ranges.find((item) => item.key === range)?.label}能量摄入柱状图`}
                >
                  {data.bars.map((height, index) => (
                    <span className={styles.bar} key={`${height}-${index}`} style={{ height }} />
                  ))}
                </div>
              </div>
            </section>
          ) : null}

          {analysisState === 'default' ? (
            <section className={styles.insightCard} aria-labelledby="insight-title">
              <h2 id="insight-title">营养洞察（由 Agent 生成）</h2>
              <div className={styles.insights}>
                <p>
                  <i className={styles.insightPurple} />
                  Protein distribution is heavily skewed toward dinner. Consider adding 15g to breakfast.
                </p>
                <p>
                  <i className={styles.insightBlue} />
                  Energy intake is consistently in your targeted deficit zone of 1,800 - 2,000 kcal.
                </p>
                <p>
                  <i className={styles.insightOrange} />
                  Sodium logging was omitted for 2 days. The Agent assumed average database values.
                </p>
              </div>
              <div className={styles.insightActions}>
                <Button
                  className={styles.interpretButton}
                  onClick={() => navigate('/chat/protein-review?prompt=请解读这份摄入分析')}
                >
                  让 Agent 解读
                </Button>
                <Button variant="outline" onClick={() => navigate('/planning')}>
                  基于分析制定计划
                </Button>
              </div>
            </section>
          ) : null}
        </section>

        {analysisState === 'default' ? (
          <section className={styles.qualityPanel} aria-label="分析维度与数据质量">
            <h2>分析维度与数据质量</h2>
            <p>趋势指标：能量 · 蛋白质 · 碳水 · 脂肪 · 对比：上一周期 / 不对比 · 餐次：全部餐次 / 指定餐次</p>
            <p>统计口径：7 天内有效记录 6 天 · 缺失 1 天 · 估算记录 2 / 7（28%） · 目标区间 1,800–2,000 kcal</p>
            <p className={styles.qualityNote}>
              异常点可打开当天饮食记录；洞察按事实 / 风险 / 建议分层展示，缺失数据不会伪造图表值。
            </p>
            <p className={styles.exportStatus}>
              导出报告：已排队 queued · 生成中 running · 可下载 completed · 失败可重新创建 failed
            </p>
            {notice ? (
              <p className={styles.notice} role="status">
                {notice}
              </p>
            ) : null}
          </section>
        ) : null}
      </div>
    </WorkspaceLayout>
  );
}
