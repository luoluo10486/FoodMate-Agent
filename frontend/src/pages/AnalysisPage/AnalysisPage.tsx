import { Card, Select, Tag } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { MetricCard } from '../../components/common/MetricCard';
import {
  analysisInsights,
  analysisMetrics,
  proteinGoal,
  proteinTargetMax,
  proteinTargetMin,
  proteinTrend
} from '../../mock/analysis';
import styles from './AnalysisPage.module.css';

const Option = Select.Option;
const chartWidth = 760;
const chartHeight = 250;
const chartPadding = { top: 28, right: 28, bottom: 42, left: 48 };

export function AnalysisPage() {
  const values = proteinTrend.map((item) => item.protein);
  const yMax = Math.ceil(Math.max(proteinTargetMax, ...values) / 20) * 20;
  const plotWidth = chartWidth - chartPadding.left - chartPadding.right;
  const plotHeight = chartHeight - chartPadding.top - chartPadding.bottom;
  const getX = (index: number) => chartPadding.left + (index / (proteinTrend.length - 1)) * plotWidth;
  const getY = (value: number) => chartPadding.top + (1 - value / yMax) * plotHeight;
  const points = proteinTrend.map((item, index) => `${getX(index)},${getY(item.protein)}`).join(' ');
  const targetMinY = getY(proteinTargetMin);
  const targetMaxY = getY(proteinTargetMax);
  const targetBandHeight = targetMinY - targetMaxY;
  const ticks = [0, Math.round(yMax * 0.25), Math.round(yMax * 0.5), Math.round(yMax * 0.75), yMax];
  const targetLabel = `${proteinTargetMin}-${proteinTargetMax}g/天`;
  const multiplierLabel = `${proteinGoal.proteinMultiplierRange[0]}-${proteinGoal.proteinMultiplierRange[1]}`;

  return (
    <WorkspaceLayout activeModule="analysis">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <Tag color="arcoblue">数据分析</Tag>
            <h1>最近一周摄入复盘</h1>
          </div>
          <Select defaultValue="7d" className={styles.select}>
            <Option value="7d">最近 7 天</Option>
            <Option value="14d">最近 14 天</Option>
            <Option value="30d">最近 30 天</Option>
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
            <div className={styles.chart} aria-label={`蛋白质趋势，推荐区间 ${targetLabel}`}>
              <svg className={styles.trendSvg} viewBox={`0 0 ${chartWidth} ${chartHeight}`} role="img">
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
                <polyline className={styles.trendLine} points={points} />
                {proteinTrend.map((item, index) => {
                  const x = getX(index);
                  const y = getY(item.protein);
                  const isLow = item.protein < proteinTargetMin;

                  return (
                    <g key={item.day}>
                      <text className={styles.valueLabel} x={x} y={y - 14} textAnchor="middle">
                        {item.protein}g
                      </text>
                      <circle className={isLow ? styles.lowPoint : styles.goodPoint} cx={x} cy={y} r="6" />
                      <text className={styles.dayLabel} x={x} y={chartHeight - 14} textAnchor="middle">
                        {item.day}
                      </text>
                    </g>
                  );
                })}
              </svg>
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
