import { useRef, useState } from 'react';
import { Card, Select, Tag } from '@arco-design/web-react';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { MetricCard } from '../../components/common/MetricCard';
import {
  type AnalysisRange,
  analysisRangeOptions,
  getAnalysisInsights,
  getAnalysisMetrics,
  proteinGoal,
  proteinTargetMax,
  proteinTargetMin,
  proteinTrendByRange
} from '../../mock/analysis';
import styles from './AnalysisPage.module.css';

const Option = Select.Option;
gsap.registerPlugin(useGSAP);

const chartWidth = 760;
const chartHeight = 250;
const chartPadding = { top: 28, right: 28, bottom: 42, left: 48 };

export function AnalysisPage() {
  const chartRef = useRef<HTMLDivElement>(null);
  const [range, setRange] = useState<AnalysisRange>('7d');
  const proteinTrend = proteinTrendByRange[range];
  const analysisMetrics = getAnalysisMetrics(range, proteinTrend);
  const analysisInsights = getAnalysisInsights(proteinTrend);
  const values = proteinTrend.map((item) => item.protein);
  const yMax = Math.ceil(Math.max(proteinTargetMax, ...values) / 20) * 20;
  const plotWidth = chartWidth - chartPadding.left - chartPadding.right;
  const plotHeight = chartHeight - chartPadding.top - chartPadding.bottom;
  const getX = (index: number) =>
    proteinTrend.length > 1 ? chartPadding.left + (index / (proteinTrend.length - 1)) * plotWidth : chartPadding.left + plotWidth / 2;
  const getY = (value: number) => chartPadding.top + (1 - value / yMax) * plotHeight;
  const points = proteinTrend.map((item, index) => `${getX(index)},${getY(item.protein)}`).join(' ');
  const targetMinY = getY(proteinTargetMin);
  const targetMaxY = getY(proteinTargetMax);
  const targetBandHeight = targetMinY - targetMaxY;
  const ticks = [0, Math.round(yMax * 0.25), Math.round(yMax * 0.5), Math.round(yMax * 0.75), yMax];
  const targetLabel = `${proteinTargetMin}-${proteinTargetMax}g/天`;
  const multiplierLabel = `${proteinGoal.proteinMultiplierRange[0]}-${proteinGoal.proteinMultiplierRange[1]}`;
  const labelEvery = proteinTrend.length > 14 ? 5 : proteinTrend.length > 7 ? 2 : 1;
  const rangeLabel = analysisRangeOptions.find((option) => option.value === range)?.label ?? '最近 7 天';

  useGSAP(
    () => {
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const trendLine = chartRef.current?.querySelector(`.${styles.trendLine}`) as SVGPolylineElement | null;
      const lineLength = trendLine?.getTotalLength() ?? 0;

      if (trendLine && lineLength > 0) {
        gsap.set(trendLine, { strokeDasharray: lineLength, strokeDashoffset: lineLength });
      }

      const timeline = gsap.timeline({ defaults: { duration: reduceMotion ? 0 : 0.55, ease: 'power3.out' } });

      timeline
        .from(`.${styles.targetBand}`, { autoAlpha: 0, scaleY: 0.82, transformOrigin: '50% 50%' })
        .from(`.${styles.gridLine}`, { autoAlpha: 0, stagger: 0.035 }, '<0.05')
        .from(`.${styles.axisLabel}, .${styles.dayLabel}`, { autoAlpha: 0, y: 6, stagger: 0.015 }, '<0.05');

      if (trendLine && lineLength > 0) {
        timeline.to(trendLine, { strokeDashoffset: 0, duration: reduceMotion ? 0 : 0.7 }, '<0.05');
      }

      timeline.from(`.${styles.goodPoint}, .${styles.lowPoint}`, { autoAlpha: 0, scale: 0.45, transformOrigin: '50% 50%', stagger: 0.025 }, '<0.2');
      timeline.from(`.${styles.valueLabel}, .${styles.targetLabel}`, { autoAlpha: 0, y: -4, stagger: 0.02 }, '<0.1');
    },
    { dependencies: [range], scope: chartRef, revertOnUpdate: true }
  );

  return (
    <WorkspaceLayout activeModule="analysis" moduleLabel={<Tag color="arcoblue">数据分析</Tag>}>
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <h1>{rangeLabel}摄入复盘</h1>
          </div>
          <Select value={range} onChange={(value) => setRange(value as AnalysisRange)} className={styles.select}>
            {analysisRangeOptions.map((option) => (
              <Option value={option.value} key={option.value}>
                {option.label}
              </Option>
            ))}
          </Select>
        </section>

        <section className={styles.metrics}>
          {analysisMetrics.map((metric) => (
            <MetricCard key={metric.label} {...metric} />
          ))}
        </section>

        <section className={styles.body}>
          <Card className={styles.chartCard} bordered={false}>
            <div className={styles.cardHead}>
              <div>
                <strong>蛋白质趋势</strong>
                <span>按 {proteinGoal.weightKg}kg × {multiplierLabel}，推荐 {targetLabel}</span>
              </div>
              <Tag color="green">Tools（2/6）time_parser · database_query</Tag>
            </div>
            <div className={styles.chart} ref={chartRef}>
              {proteinTrend.length === 0 ? (
                <div className={styles.emptyChart}>当前时间范围暂无蛋白质记录</div>
              ) : (
                <svg
                  aria-label={`蛋白质趋势，推荐区间 ${targetLabel}`}
                  className={styles.trendSvg}
                  viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                  role="img"
                >
                  <title>{`蛋白质趋势，推荐区间 ${targetLabel}`}</title>
                  <rect
                    className={styles.targetBand}
                    x={chartPadding.left}
                    y={targetMaxY}
                    width={plotWidth}
                    height={targetBandHeight}
                    rx="10"
                  />
                  <text className={styles.targetLabel} x={chartPadding.left + 12} y={targetMaxY + 18}>
                    推荐 {targetLabel}
                  </text>
                  {ticks.map((tick) => {
                    const y = getY(tick);
                    return (
                      <g key={tick}>
                        <line className={styles.gridLine} x1={chartPadding.left} y1={y} x2={chartWidth - chartPadding.right} y2={y} />
                        <text className={styles.axisLabel} x={chartPadding.left - 12} y={y + 4} textAnchor="end">
                          {tick}g
                        </text>
                      </g>
                    );
                  })}
                  {proteinTrend.length > 1 ? <polyline className={styles.trendLine} points={points} /> : null}
                  {proteinTrend.map((item, index) => {
                    const x = getX(index);
                    const y = getY(item.protein);
                    const isLow = item.protein < proteinTargetMin;
                    const shouldShowLabel = index % labelEvery === 0 || index === proteinTrend.length - 1;

                    return (
                      <g key={item.day}>
                        {shouldShowLabel ? (
                          <text className={styles.valueLabel} x={x} y={y - 14} textAnchor="middle">
                            {item.protein}g
                          </text>
                        ) : null}
                        <circle className={isLow ? styles.lowPoint : styles.goodPoint} cx={x} cy={y} r={shouldShowLabel ? 6 : 4} />
                        {shouldShowLabel ? (
                          <text className={styles.dayLabel} x={x} y={chartHeight - 14} textAnchor="middle">
                            {item.day}
                          </text>
                        ) : null}
                      </g>
                    );
                  })}
                </svg>
              )}
            </div>
          </Card>

          <Card className={styles.insightCard} bordered={false}>
            <strong>异常与建议</strong>
            <div className={styles.insights}>
              {analysisInsights.map((item) => (
                <article className={`${styles.insight} ${styles[item.tone]}`} key={item.title}>
                  <strong>{item.title}</strong>
                  <span>{item.detail}</span>
                </article>
              ))}
            </div>
          </Card>
        </section>

        <Composer toolsUsed={2} toolsTotal={6} agentsUsed={1} agentsTotal={1} placeholder="继续追问分析口径，例如：按早餐/午餐拆开..." />
      </div>
    </WorkspaceLayout>
  );
}
