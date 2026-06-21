import { Card, Select, Tag } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { MetricCard } from '../../components/common/MetricCard';
import { analysisInsights, analysisMetrics, proteinTrend } from '../../mock/analysis';
import styles from './AnalysisPage.module.css';

const Option = Select.Option;

export function AnalysisPage() {
  const max = Math.max(...proteinTrend);

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
              <strong>蛋白质趋势</strong>
              <Tag color="green">Tools（2/6）time_parser · database_query</Tag>
            </div>
            <div className={styles.chart}>
              {proteinTrend.map((value, index) => (
                <div className={styles.barWrap} key={index}>
                  <div className={styles.bar} style={{ height: `${(value / max) * 100}%` }} />
                  <span>周{index + 1}</span>
                </div>
              ))}
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
