import { Button, Card, Input, Tag } from '@arco-design/web-react';
import { IconSearch } from '@arco-design/web-react/icon';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import styles from './KnowledgePage.module.css';

const knowledgeItems = [
  {
    title: '备餐指南',
    source: '内部知识库',
    snippet: '高蛋白备餐建议优先复用鸡蛋、豆腐、鸡胸肉等易保存食材，减少采购成本和食材浪费。',
    visibility: '用户可见'
  },
  {
    title: '蔬菜焯水与营养保留指南',
    source: '营养资料',
    snippet: '西兰花焯水通常控制在 60-90 秒，出锅后过冷水有助于保持口感和颜色。',
    visibility: '公开'
  },
  {
    title: '蛋白质推荐摄入说明',
    source: '目标配置',
    snippet: '当前 mock 口径按体重 70kg × 1.5-2.0g/kg/天，推荐区间为 105-140g/天。',
    visibility: '用户可见'
  }
];

export function KnowledgePage() {
  return (
    <WorkspaceLayout activeModule="knowledge" moduleLabel={<Tag color="green">知识库</Tag>}>
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <span>Knowledge Base</span>
            <h1>知识库检索占位</h1>
          </div>
          <Input.Search className={styles.search} prefix={<IconSearch />} placeholder="搜索食材、烹饪方式或营养规则" searchButton="检索" />
        </section>

        <section className={styles.summary}>
          <Card bordered={false} className={styles.summaryCard}>
            <strong>3</strong>
            <span>mock 文档</span>
          </Card>
          <Card bordered={false} className={styles.summaryCard}>
            <strong>ACL</strong>
            <span>预留用户可见性过滤</span>
          </Card>
          <Card bordered={false} className={styles.summaryCard}>
            <strong>RAG</strong>
            <span>等待后端知识库检索接口</span>
          </Card>
        </section>

        <section className={styles.list}>
          {knowledgeItems.map((item) => (
            <article className={styles.item} key={item.title}>
              <div>
                <Tag color={item.visibility === '公开' ? 'blue' : 'green'}>{item.visibility}</Tag>
                <h2>{item.title}</h2>
                <p>{item.snippet}</p>
              </div>
              <footer>
                <span>{item.source}</span>
                <Button size="small">查看引用</Button>
              </footer>
            </article>
          ))}
        </section>
      </div>
    </WorkspaceLayout>
  );
}
