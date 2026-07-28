import { Button, Skeleton } from '@arco-design/web-react';
import { IconArrowRight } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { TaskCard } from '../../components/common/TaskCard';
import { EmptyState } from '../../components/common/EmptyState';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { getRecommendedPrompts, getTaskCards } from '../../services/sessionService';
import styles from './HomePage.module.css';

export function HomePage() {
  const navigate = useNavigate();
  const [prompt, setPrompt] = useState('');

  const startPrompt = (value: string) => {
    const normalized = value.trim();
    if (!normalized) return;
    // 真实模式必须先进入无会话路由，由 ChatPage 创建数据库会话；任务类型不能冒充 session_id。
    const target = import.meta.env.VITE_AGENT_MODE === 'real' ? '/chat' : '/chat/quick-start';
    navigate(`${target}?prompt=${encodeURIComponent(normalized)}`);
  };

  return (
    <WorkspaceLayout activeModule="home">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.hero}>
          <BrandLogo size="hero" showTagline />
          <p>把模糊的饮食问题拆成可追踪的任务：先判断意图，再调用工具，最后给出带依据的结果。</p>
        </section>

        <section className={styles.tasks}>
          {getTaskCards().map((task) => (
            <TaskCard key={task.id} task={task} />
          ))}
        </section>

        <section className={styles.recommendations}>
          <div>
            <span>推荐任务</span>
            <strong>从这些高频问题开始</strong>
          </div>
          <div className={styles.promptGrid}>
            {getRecommendedPrompts().map((prompt) => (
              <Button key={prompt} className={styles.promptButton} onClick={() => startPrompt(prompt)}>
                {prompt}
                <IconArrowRight />
              </Button>
            ))}
          </div>
        </section>

        <div className={styles.bottom}>
          <EmptyState />
          <Skeleton className={styles.skeleton} loading text={{ rows: 2 }} animation />
          <Composer
            value={prompt}
            toolsUsed={0}
            toolsTotal={6}
            agentsUsed={0}
            agentsTotal={1}
            onChange={setPrompt}
            onSend={() => startPrompt(prompt)}
          />
        </div>
      </div>
    </WorkspaceLayout>
  );
}
