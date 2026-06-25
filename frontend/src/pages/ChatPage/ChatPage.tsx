import { useEffect, useRef } from 'react';
import { Card, Tag } from '@arco-design/web-react';
import { useParams, useSearchParams } from 'react-router-dom';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { AgentStatusStrip } from '../../components/agent/AgentStatusStrip';
import { CitationBlock } from '../../components/agent/CitationBlock';
import { ResultCard } from '../../components/agent/ResultCard';
import { ToolTraceItem } from '../../components/agent/ToolTraceItem';
import { ClarificationCard } from '../../components/agent/ClarificationCard';
import { ConfirmationCard } from '../../components/agent/ConfirmationCard';
import { ErrorState } from '../../components/common/ErrorState';
import { useMockAgentReplay } from '../../mock/agentReplay';
import styles from './ChatPage.module.css';

export function ChatPage() {
  const { sessionId } = useParams();
  const [searchParams] = useSearchParams();
  const agent = useMockAgentReplay(sessionId, searchParams.get('prompt'));
  const messagesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [agent.messages, agent.card]);

  return (
    <WorkspaceLayout activeModule="chat">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.workspace}>
          <div className={styles.center}>
            <AgentStatusStrip status={agent.run.status} />
            <div className={styles.messages} ref={messagesRef}>
              {agent.messages.map((message) => (
                <article className={`${styles.message} ${styles[message.role]}`} key={message.id}>
                  <Tag color={message.role === 'user' ? 'gray' : 'green'}>{message.role === 'user' ? '你' : 'FoodMate'}</Tag>
                  <p>{message.content}</p>
                  <span>{message.time}</span>
                </article>
              ))}
              {agent.card.type === 'result' ? (
                <ResultCard
                  label={agent.card.label}
                  title={agent.card.title}
                  description={agent.card.description}
                  primaryAction={agent.card.primaryAction}
                  secondaryAction={agent.card.secondaryAction}
                  onPrimary={agent.handleResultPrimary}
                  onSecondary={agent.handleResultSecondary}
                />
              ) : null}
              {agent.card.type === 'clarification' ? <ClarificationCard title={agent.card.title} options={agent.card.options} onSelect={agent.answerClarification} /> : null}
              {agent.card.type === 'confirmation' ? (
                <ConfirmationCard data={agent.card.data} onConfirm={agent.confirmWrite} onEdit={agent.editWrite} onCancel={agent.cancelWrite} />
              ) : null}
              {agent.card.type === 'error' ? <ErrorState message={agent.card.message} /> : null}
            </div>
          </div>

          <aside className={styles.tracePanel}>
            <Card className={styles.panelCard} bordered={false}>
              <div className={styles.panelHead}>
                <strong>工具与引用</strong>
                <Tag color="orange">
                  Tools（{agent.run.toolsUsed}/{agent.run.toolsTotal}）
                </Tag>
              </div>
              <div className={styles.traceList}>
                {agent.run.toolCalls.map((tool) => (
                  <ToolTraceItem key={tool.id} tool={tool} />
                ))}
              </div>
              <div className={styles.citationList}>
                {agent.run.citations.map((citation) => (
                  <CitationBlock citation={citation} key={citation.id} />
                ))}
              </div>
            </Card>
          </aside>
        </section>
        <Composer
          value={agent.input}
          running={agent.running}
          toolsUsed={agent.run.toolsUsed}
          toolsTotal={agent.run.toolsTotal}
          agentsUsed={agent.run.agentsUsed}
          agentsTotal={agent.run.agentsTotal}
          placeholder="输入任务，例如：给我做一周备餐计划 / 帮我记录今天午餐 / 分析最近一周蛋白质摄入..."
          onChange={agent.setInput}
          onSend={() => agent.send()}
          onStop={agent.stop}
        />
      </div>
    </WorkspaceLayout>
  );
}
