import {
  ArrowRight,
  BarChart3,
  CalendarDays,
  Calculator,
  Check,
  Paperclip,
  Search,
  SendHorizontal,
  Utensils,
} from 'lucide-react';
import { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { getAuthUser } from '../../services/authService';
import { getRecommendedPrompts, getSessions, getTaskCards } from '../../services/sessionService';
import styles from './HomePage.module.css';

const metricCards = [
  { label: '热量', value: '1,850', unit: '千卡', progress: '74', tone: 'green' },
  { label: '蛋白质', value: '120', unit: 'g', progress: '80', tone: 'purple' },
  { label: '碳水', value: '210', unit: 'g', progress: '65', tone: 'orange' },
  { label: '脂肪', value: '58', unit: 'g', progress: '55', tone: 'red' },
] as const;

const pendingItems = [
  {
    id: 'beef',
    title: '牛油果酸面包吐司',
    detail: '记录为 340 千卡 · 置信度：94%',
    prompt: '确认记录牛油果酸面包吐司',
  },
  { id: 'fish', title: '煎三文鱼碗', detail: '记录为 620 千卡 · 置信度：88%', prompt: '确认记录煎三文鱼碗' },
];

export function HomePage() {
  const navigate = useNavigate();
  const [prompt, setPrompt] = useState('');
  const [confirmedItems, setConfirmedItems] = useState<string[]>([]);
  const [attachmentName, setAttachmentName] = useState('');
  const attachmentInputRef = useRef<HTMLInputElement>(null);
  const currentUser = getAuthUser();
  const taskCards = getTaskCards();
  const recommendedPrompts = getRecommendedPrompts();
  const sessions = getSessions();

  const quickActions = useMemo(
    () => [
      { label: '记录饮食', prompt: recommendedPrompts[0], icon: Utensils, tone: 'green' },
      {
        label: '分析摄入',
        prompt: taskCards.find((task) => task.id === 'analysis')?.prompt ?? recommendedPrompts[2],
        icon: BarChart3,
        tone: 'purple',
      },
      {
        label: '创建计划',
        prompt: taskCards.find((task) => task.id === 'planning')?.prompt ?? recommendedPrompts[1],
        icon: CalendarDays,
        tone: 'red',
      },
      { label: '搜索知识', prompt: recommendedPrompts[3], icon: Search, tone: 'blue' },
      {
        label: '快速计算',
        prompt: taskCards.find((task) => task.id === 'calorie')?.prompt ?? '计算这份食物的热量',
        icon: Calculator,
        tone: 'orange',
      },
    ],
    [recommendedPrompts, taskCards],
  );

  const startPrompt = (value: string) => {
    const normalized = value.trim();
    if (!normalized) return;
    const target = import.meta.env.VITE_AGENT_MODE === 'real' ? '/chat' : '/chat/quick-start';
    navigate(`${target}?prompt=${encodeURIComponent(normalized)}`);
  };

  const confirmItem = (id: string, value: string) => {
    setConfirmedItems((items) => (items.includes(id) ? items.filter((item) => item !== id) : [...items, id]));
    startPrompt(value);
  };

  return (
    <WorkspaceLayout activeModule="home">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.intro}>
          <div>
            <h1>👋 早上好，{currentUser.displayName}！</h1>
            <p>今天是 2024年3月14日 星期二</p>
          </div>
          <span className={styles.environment}>生产环境</span>
        </section>

        <section className={styles.taskComposer} aria-label="开始一个新任务">
          <Button
            className={styles.attachmentButton}
            variant="ghost"
            size="icon"
            aria-label="添加附件"
            onClick={() => attachmentInputRef.current?.click()}
          >
            <Paperclip aria-hidden="true" />
          </Button>
          <input
            ref={attachmentInputRef}
            className={styles.fileInput}
            type="file"
            tabIndex={-1}
            aria-hidden="true"
            onChange={(event) => setAttachmentName(event.target.files?.[0]?.name ?? '')}
          />
          <Input
            className={styles.taskInput}
            value={prompt}
            placeholder="分析早餐照片，计算热量摄入并记录营养指标..."
            aria-label="任务内容"
            onChange={(event) => setPrompt(event.target.value)}
            onKeyDown={(event) => {
              const composing = event.nativeEvent.isComposing || event.keyCode === 229;
              if (event.key === 'Enter' && !event.shiftKey && !composing) {
                event.preventDefault();
                startPrompt(prompt);
              }
            }}
          />
          <Button
            className={styles.sendButton}
            size="icon"
            aria-label="发送任务"
            disabled={!prompt.trim()}
            onClick={() => startPrompt(prompt)}
          >
            <SendHorizontal aria-hidden="true" />
          </Button>
        </section>

        {attachmentName ? (
          <span className={styles.visuallyHidden} role="status">
            {attachmentName}
          </span>
        ) : null}

        <section className={styles.quickActions} aria-label="快速操作">
          {quickActions.map(({ icon: Icon, label, prompt: actionPrompt, tone }) => (
            <Button
              className={`${styles.quickButton} ${styles[`quick${tone[0].toUpperCase()}${tone.slice(1)}`]}`}
              key={label}
              variant="outline"
              onClick={() => setPrompt(actionPrompt)}
            >
              <Icon aria-hidden="true" />
              <span>{label}</span>
            </Button>
          ))}
        </section>

        <section className={styles.metrics} aria-label="今日营养指标">
          {metricCards.map((metric) => (
            <article className={styles.metricCard} key={metric.label}>
              <div>
                <span className={styles.metricLabel}>{metric.label}</span>
                <strong>
                  {metric.value}
                  <small>{metric.unit}</small>
                </strong>
              </div>
              <span
                className={`${styles.progress} ${styles[`progress${metric.tone[0].toUpperCase()}${metric.tone.slice(1)}`]}`}
              >
                <span>{metric.progress}%</span>
              </span>
            </article>
          ))}
        </section>

        <section className={styles.dashboardGrid}>
          <article className={styles.panel}>
            <div className={styles.panelHeader}>
              <h2>活跃会话</h2>
            </div>
            <div className={styles.sessionCards}>
              {sessions.map((session, index) => (
                <button
                  className={styles.sessionCard}
                  key={session.id}
                  type="button"
                  onClick={() => navigate(`/chat/${session.id}`)}
                >
                  <span className={`${styles.sessionDot} ${styles[`dot${index}`]}`} aria-hidden="true" />
                  <span>
                    <strong>{session.title}</strong>
                    <small>{session.subtitle}</small>
                  </span>
                  <ArrowRight aria-hidden="true" />
                </button>
              ))}
            </div>
          </article>

          <article className={styles.panel}>
            <div className={styles.panelHeader}>
              <h2>待确认队列</h2>
            </div>
            <div className={styles.pendingCards}>
              {pendingItems.map((item) => {
                const confirmed = confirmedItems.includes(item.id);
                return (
                  <div className={`${styles.pendingCard} ${confirmed ? styles.pendingConfirmed : ''}`} key={item.id}>
                    <span>
                      <strong>{item.title}</strong>
                      <small>{confirmed ? '已提交确认' : item.detail}</small>
                    </span>
                    <Button
                      className={styles.confirmButton}
                      size="sm"
                      onClick={() => confirmItem(item.id, item.prompt)}
                    >
                      {confirmed ? <Check aria-hidden="true" /> : null}
                      {confirmed ? '已确认' : '确认'}
                    </Button>
                  </div>
                );
              })}
            </div>
          </article>
        </section>

        <section className={styles.statusPanel} aria-labelledby="status-title">
          <h2 id="status-title">任务入口与状态</h2>
          <p>输入器：空输入时发送禁用 · 有内容时启用 · Agent 运行中切换为停止 · 附件解析中显示进度</p>
          <p>高频任务点击后带入输入器；继续任务打开原会话；查看全部进入会话列表。</p>
          <p className={styles.statusGreen}>
            Tools / Agents 面板可展开查看健康状态；待处理事项提醒写入确认、预算通知、记忆确认和失败任务。
          </p>
          <p className={styles.statusMuted}>摘要局部失败支持重试，不替换已有成功数据；空态不展示虚构营养或任务数据。</p>
        </section>
      </div>
    </WorkspaceLayout>
  );
}
