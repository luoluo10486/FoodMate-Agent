import { Button, Skeleton } from '@arco-design/web-react';
import { IconArrowRight } from '@arco-design/web-react/icon';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { TaskCard } from '../../components/common/TaskCard';
import { EmptyState } from '../../components/common/EmptyState';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { recommendedPrompts, taskCards } from '../../mock/sessions';
import styles from './HomePage.module.css';

export function HomePage() {
  return (
    <WorkspaceLayout activeModule="home">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.hero}>
          <BrandLogo size="hero" showTagline />
          <p>把模糊的饮食问题拆成可追踪的任务：先判断意图，再调用工具，最后给出带依据的结果。</p>
        </section>

        <section className={styles.tasks}>
          {taskCards.map((task) => (
            <TaskCard key={task.id} task={task} />
          ))}
        </section>

        <section className={styles.recommendations}>
          <div>
            <span>推荐任务</span>
            <strong>从这些高频问题开始</strong>
          </div>
          <div className={styles.promptGrid}>
            {recommendedPrompts.map((prompt) => (
              <Button key={prompt} className={styles.promptButton}>
                {prompt}
                <IconArrowRight />
              </Button>
            ))}
          </div>
        </section>

        <div className={styles.bottom}>
          <EmptyState />
          <Skeleton className={styles.skeleton} loading text={{ rows: 2 }} animation />
          <Composer toolsUsed={0} toolsTotal={6} agentsUsed={0} agentsTotal={1} />
        </div>
      </div>
    </WorkspaceLayout>
  );
}
