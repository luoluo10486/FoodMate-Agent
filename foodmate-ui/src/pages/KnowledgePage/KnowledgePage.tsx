import { ArrowRight, Search } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import styles from './KnowledgePage.module.css';

type KnowledgeState = 'default' | 'empty' | 'search-failed' | 'source-unavailable';

type KnowledgeResult = {
  title: string;
  match: string;
  snippet: string;
  source: string;
  updated: string;
  sourceTone: 'green' | 'blue' | 'purple';
  topic: 'nutrition';
  details: {
    sourceName: string;
    documentId: string;
    access: string;
    quote: string;
  };
};

const knowledgeResults: KnowledgeResult[] = [
  {
    title: '烹饪温度对牛油果健康脂肪的影响',
    match: '98% Match',
    snippet: '120°C以上的高温处理会引发单不饱和油酸的轻度脂质过氧化。冷压或新鲜食用仍是系统性抗氧化吸收的最佳方式。',
    source: 'NIH §4.2',
    updated: '2天前更新',
    sourceTone: 'blue',
    topic: 'nutrition',
    details: {
      sourceName: 'NIH 研究实验室文献库',
      documentId: 'DOC ID: NIH-451992-B',
      access: 'Access: Open Access Dataset, last cached 12h ago.',
      quote:
        'Peroxidation of monounsaturated chains remains statistically minor compared to polyunsaturated chains under identical baking parameters...',
    },
  },
  {
    title: '藜麦与酸面包淀粉的血糖指数动态',
    match: '92% Match',
    snippet: '比较全籽白皂苷水洗藜麦与长发酵乳酸菌酸面包。藜麦因不溶性结构纤维，血糖负荷稳定在13。',
    source: 'USDA 数据库',
    updated: '1周前更新',
    sourceTone: 'green',
    topic: 'nutrition',
    details: {
      sourceName: 'USDA FoodData Central',
      documentId: 'DOC ID: USDA-GLYCEMIC-2204',
      access: 'Access: Open Access Dataset, last cached 1d ago.',
      quote: 'Quinoa retains a more stable glycemic load when its insoluble structural fiber remains intact...',
    },
  },
  {
    title: '运动后最佳蛋白质吸收窗口期',
    match: '89% Match',
    snippet: '肌肉蛋白质合成触发机制概述。氨基酸循环在运动后45-75分钟达峰时效率最高，有同行评审的运动营养数据支持。',
    source: 'PubMed Central',
    updated: '3天前更新',
    sourceTone: 'purple',
    topic: 'nutrition',
    details: {
      sourceName: 'PubMed Central research archive',
      documentId: 'DOC ID: PMC-PROTEIN-4571',
      access: 'Access: Open Access Dataset, last cached 6h ago.',
      quote: 'Amino acid circulation reaches peak efficiency during the 45-75 minute post-exercise window...',
    },
  },
];

const topics = [
  { icon: '🥑', title: '牛油果脂质', count: '12 篇引用' },
  { icon: '🍞', title: '酸面包淀粉', count: '8 篇引用' },
  { icon: '🥩', title: '氨基酸合成', count: '19 篇引用' },
];

const filterOptions = ['全部主题', '营养素', '仅引用', '近90天'];

function getKnowledgeState(value: string | null): KnowledgeState {
  return value === 'empty' || value === 'search-failed' || value === 'source-unavailable' ? value : 'default';
}

export function KnowledgePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const [selectedResultTitle, setSelectedResultTitle] = useState(knowledgeResults[0].title);
  const [activeFilter, setActiveFilter] = useState('全部主题');
  const knowledgeState = getKnowledgeState(searchParams.get('state'));

  const selected = knowledgeResults.find((item) => item.title === selectedResultTitle) ?? knowledgeResults[0];

  const visibleResults = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const filterMatches = (item: KnowledgeResult) => {
      if (activeFilter === '营养素') return item.topic === 'nutrition';
      return true;
    };

    if (!normalizedQuery) return knowledgeResults.filter(filterMatches);
    return knowledgeResults.filter(
      (item) =>
        filterMatches(item) && `${item.title} ${item.snippet} ${item.source}`.toLowerCase().includes(normalizedQuery),
    );
  }, [activeFilter, query]);

  const updateState = (state: KnowledgeState) => {
    const next = new URLSearchParams(searchParams);
    if (state === 'default') next.delete('state');
    else next.set('state', state);
    if (query.trim()) next.set('q', query.trim());
    else next.delete('q');
    setSearchParams(next);
  };

  const handleSearch = () => {
    if (!query.trim() || visibleResults.length === 0) {
      updateState('empty');
      return;
    }
    updateState('default');
  };

  const clearFilters = () => {
    setQuery('');
    setActiveFilter('全部主题');
    setSearchParams(new URLSearchParams());
  };

  return (
    <WorkspaceLayout
      activeModule="knowledge"
      pageOverlay={
        knowledgeState !== 'default' ? (
          <KnowledgeStateCard
            state={knowledgeState}
            onAction={knowledgeState === 'empty' ? clearFilters : () => updateState('default')}
          />
        ) : null
      }
    >
      <div className={`${styles.page} fm-enter`}>
        <main className={styles.resultsPanel} aria-label="知识库检索结果">
          <header className={styles.pageHeader}>
            <h1>知识库</h1>
            <form
              className={styles.searchForm}
              onSubmit={(event) => {
                event.preventDefault();
                handleSearch();
              }}
            >
              <Search aria-hidden="true" className={styles.searchIcon} />
              <Input
                aria-label="搜索食物知识、食材、烹饪技巧"
                className={styles.knowledgeSearch}
                placeholder="搜索食物知识、食材、烹饪技巧..."
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </form>
            <div className={styles.filters} aria-label="知识库筛选">
              {filterOptions.map((filter) => (
                <Button
                  className={`${styles.filter} ${activeFilter === filter ? styles.filterActive : ''}`}
                  key={filter}
                  onClick={() => setActiveFilter(filter)}
                  type="button"
                  variant="outline"
                  aria-pressed={activeFilter === filter}
                >
                  {filter}
                </Button>
              ))}
            </div>
          </header>

          <div className={styles.resultCount}>显示 {visibleResults.length === 0 ? 0 : 24} 条结果</div>

          <section className={styles.resultList} aria-label="知识库结果列表">
            {visibleResults.map((item) => (
              <Card
                aria-labelledby={`knowledge-result-${item.title}`}
                className={styles.resultCard}
                key={item.title}
                role="article"
              >
                <div className={styles.resultTitleRow}>
                  <h2 id={`knowledge-result-${item.title}`}>{item.title}</h2>
                  <Badge className={styles.matchBadge}>{item.match}</Badge>
                </div>
                <p className={styles.snippet}>{item.snippet}</p>
                <div className={styles.resultFooter}>
                  <div className={styles.resultMeta}>
                    <Badge className={`${styles.sourceBadge} ${styles[`source-${item.sourceTone}`]}`}>
                      {item.source}
                    </Badge>
                    <span>{item.updated}</span>
                  </div>
                  <div className={styles.resultActions}>
                    <Button
                      className={styles.citationButton}
                      onClick={() => setSelectedResultTitle(item.title)}
                      type="button"
                      variant="link"
                    >
                      查看引用
                    </Button>
                    <Button
                      className={styles.askButton}
                      onClick={() =>
                        navigate(
                          `/chat/knowledge?prompt=${encodeURIComponent(`请基于「${item.title}」为我解释相关营养知识`)}`,
                        )
                      }
                    >
                      就此提问
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </section>
        </main>

        <aside className={styles.detailsPanel} aria-label={`当前引用详情：${selected.title}`}>
          <h2>当前引用详情</h2>
          <Card className={styles.sourceCard}>
            <strong>{selected.details.sourceName}</strong>
            <span>{selected.details.documentId}</span>
            <p>{selected.details.access}</p>
          </Card>
          <blockquote aria-label={`引用原文：${selected.details.quote}`} className={styles.quote}>
            &quot;
            {selected.details.quote.length > 28 ? `${selected.details.quote.slice(0, 28)}...` : selected.details.quote}
            &quot;
          </blockquote>
          <Button
            className={styles.sourceLink}
            onClick={() => updateState('source-unavailable')}
            type="button"
            variant="link"
          >
            打开原始来源 <ArrowRight aria-hidden="true" />
          </Button>
          <div className={styles.divider} />
          <h3>推荐主题</h3>
          <div className={styles.topicList}>
            {topics.map((topic) => (
              <Button
                className={styles.topic}
                key={topic.title}
                onClick={() => setQuery(topic.title)}
                type="button"
                variant="ghost"
              >
                <span className={styles.topicIcon} aria-hidden="true">
                  {topic.icon}
                </span>
                <span>
                  <strong>{topic.title}</strong>
                  <small>{topic.count}</small>
                </span>
              </Button>
            ))}
          </div>
        </aside>
      </div>
    </WorkspaceLayout>
  );
}

function KnowledgeStateCard({ state, onAction }: { state: Exclude<KnowledgeState, 'default'>; onAction: () => void }) {
  const content = {
    empty: {
      label: 'EMPTY · NO MATCHES',
      title: '没有找到相关内容',
      body: '换一个关键词，或清除主题与来源筛选后重试。',
      action: '清除筛选',
    },
    'search-failed': {
      label: 'ERROR · RETRY AVAILABLE',
      title: '检索失败',
      body: '知识库服务暂时不可用，当前没有返回结果。请稍后重试。',
      detail: '错误码: KB_SEARCH_UNAVAILABLE · request_id: req_kb_73e2',
      action: '重新检索',
    },
    'source-unavailable': {
      label: 'PARTIAL ACCESS',
      title: '来源暂时不可访问',
      body: '当前结果仍可查看匹配片段，但原始来源暂时无法打开。',
      detail: '来源状态: unavailable · 已保留引用与文档 ID',
      action: '稍后重试',
    },
  }[state];

  return (
    <div className={`${styles.stateOverlay} ${styles[`state-${state}`]}`} role="presentation">
      <section aria-live="polite" className={styles.stateCard} role="alert">
        <span className={styles.stateStatus}>{content.label}</span>
        <h2>{content.title}</h2>
        <p>{content.body}</p>
        {content.detail ? <span className={styles.stateDetail}>{content.detail}</span> : null}
        <Button className={styles.stateAction} onClick={onAction} type="button" variant="link">
          {content.action}
        </Button>
      </section>
    </div>
  );
}
