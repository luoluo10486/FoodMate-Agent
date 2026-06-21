import { Card, Tag } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { AgentStatusStrip } from '../../components/agent/AgentStatusStrip';
import { CitationBlock } from '../../components/agent/CitationBlock';
import { ResultCard } from '../../components/agent/ResultCard';
import { ToolTraceItem } from '../../components/agent/ToolTraceItem';
import { ClarificationCard } from '../../components/agent/ClarificationCard';
import { ConfirmationCard } from '../../components/agent/ConfirmationCard';
import { chatMessages, planningRun } from '../../mock/sessions';
import styles from './ChatPage.module.css';

export function ChatPage() {
  return (
    <WorkspaceLayout activeModule="chat">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.workspace}>
          <div className={styles.center}>
            <AgentStatusStrip status={planningRun.status} />
            <div className={styles.messages}>
              {chatMessages.map((message) => (
                <article className={`${styles.message} ${styles[message.role]}`} key={message.id}>
                  <Tag color={message.role === 'user' ? 'gray' : 'green'}>{message.role === 'user' ? '你' : 'FoodMate'}</Tag>
                  <p>{message.content}</p>
                  <span>{message.time}</span>
                </article>
              ))}
              <ResultCard />
              <ClarificationCard />
              <ConfirmationCard />
            </div>
          </div>

          <aside className={styles.tracePanel}>
            <Card className={styles.panelCard} bordered={false}>
              <div className={styles.panelHead}>
                <strong>工具与引用</strong>
                <Tag color="orange">Tools（2/6）</Tag>
              </div>
              <div className={styles.traceList}>
                {planningRun.toolCalls.map((tool) => (
                  <ToolTraceItem key={tool.id} tool={tool} />
                ))}
              </div>
              <div className={styles.citationList}>
                {planningRun.citations.map((citation) => (
                  <CitationBlock citation={citation} key={citation.id} />
                ))}
              </div>
            </Card>
          </aside>
        </section>
        <Composer running toolsUsed={2} toolsTotal={6} agentsUsed={1} agentsTotal={1} placeholder="继续补充约束，或让 FoodMate 修改计划..." />
      </div>
    </WorkspaceLayout>
  );
}
