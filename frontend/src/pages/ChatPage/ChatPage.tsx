import { useEffect, useRef, useState } from 'react';
import { Card, Message as ArcoMessage, Tag } from '@arco-design/web-react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { AgentStatusStrip } from '../../components/agent/AgentStatusStrip';
import { CitationBlock } from '../../components/agent/CitationBlock';
import { ResultCard } from '../../components/agent/ResultCard';
import { ToolTraceItem } from '../../components/agent/ToolTraceItem';
import { ClarificationCard } from '../../components/agent/ClarificationCard';
import { ConfirmationCard } from '../../components/agent/ConfirmationCard';
import { ErrorState } from '../../components/common/ErrorState';
import { useAgentReplay } from '../../services/agentService';
import { ApiError } from '../../services/apiClient';
import { createSession, loadSessionMessages, sendUserMessage, type RealMessage } from '../../services/sessionService';
import styles from './ChatPage.module.css';

export function ChatPage() {
  return import.meta.env.VITE_AGENT_MODE === 'real' ? <RealChatPage /> : <MockChatPage />;
}

function RealChatPage() {
  const { session_id: sessionId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [messages, setMessages] = useState<RealMessage[]>([]);
  const [input, setInput] = useState(searchParams.get('prompt') ?? '');
  const [loading, setLoading] = useState(Boolean(sessionId));
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string>();
  const messagesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    if (!sessionId) { setLoading(false); return; }
    setLoading(true); setError(undefined);
    loadSessionMessages(sessionId).then((rows) => { if (!cancelled) setMessages(rows.sort((a, b) => a.sequence_no - b.sequence_no)); }).catch((reason) => { if (!cancelled) setError(reason instanceof Error ? reason.message : '消息加载失败'); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [sessionId]);

  useEffect(() => { messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' }); }, [messages]);

  const send = async () => {
    const content = input.trim();
    if (!content || sending) return;
    setError(undefined); setSending(true);
    try {
      let target = sessionId;
      if (!target) { const created = await createSession(content.slice(0, 40)); target = String(created.session_id); navigate(`/chat/${target}`, { replace: true }); }
      const saved = await sendUserMessage(target, content);
      setMessages((current) => [...current, saved].sort((a, b) => a.sequence_no - b.sequence_no));
      setInput('');
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === 'FORBIDDEN') ArcoMessage.error(reason.message);
      setError(reason instanceof Error ? reason.message : '消息发送失败');
    } finally { setSending(false); }
  };

  return (
    <WorkspaceLayout activeModule="chat">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.workspace}>
          <div className={styles.center}>
            <AgentStatusStrip status="completed" />
            <div className={styles.messages} ref={messagesRef}>
              {loading ? <p>正在加载消息...</p> : null}
              {!loading && messages.length === 0 ? <p>暂无消息，发送第一条内容开始会话。</p> : null}
              {error ? <ErrorState message={error} /> : null}
              {messages.map((message) => (
                <article className={`${styles.message} ${styles.user}`} key={message.message_id}>
                  <Tag color="gray">你</Tag>
                  <p>{message.content}</p>
                  <span>{new Date(message.created_at).toLocaleString()}</span>
                </article>
              ))}
            </div>
          </div>
          <aside className={styles.tracePanel}>
            <Card className={styles.panelCard} bordered={false}>
              <div className={styles.panelHead}><strong>当前阶段</strong><Tag color="gray">不生成 AI 回复</Tag></div>
              <p>本阶段只保存用户消息，Agent、工具和流式回复将在后续阶段接入。</p>
            </Card>
          </aside>
        </section>
        <Composer value={input} running={false} disabled={loading || sending} toolsUsed={0} toolsTotal={0} agentsUsed={0} agentsTotal={0} placeholder="输入要保存到会话中的内容..." onChange={setInput} onSend={() => void send()} />
      </div>
    </WorkspaceLayout>
  );
}

function MockChatPage() {
  const params = useParams();
  const sessionId = params.session_id;
  const [searchParams] = useSearchParams();
  const agent = useAgentReplay(sessionId, searchParams.get('prompt'));
  const messagesRef = useRef<HTMLDivElement>(null);
  useEffect(() => { messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' }); }, [agent.messages, agent.card]);
  return (
    <WorkspaceLayout activeModule="chat">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.workspace}><div className={styles.center}><AgentStatusStrip status={agent.run.status} /><div className={styles.messages} ref={messagesRef}>{agent.messages.map((message) => <article className={`${styles.message} ${styles[message.role]}`} key={message.id}><Tag color={message.role === 'user' ? 'gray' : 'green'}>{message.role === 'user' ? '你' : 'FoodMate'}</Tag><p>{message.content}</p><span>{message.time}</span></article>)}{agent.card.type === 'result' ? <ResultCard label={agent.card.label} title={agent.card.title} description={agent.card.description} primaryAction={agent.card.primaryAction} secondaryAction={agent.card.secondaryAction} onPrimary={agent.handleResultPrimary} onSecondary={agent.handleResultSecondary} /> : null}{agent.card.type === 'clarification' ? <ClarificationCard title={agent.card.title} options={agent.card.options} fields={agent.card.fields} submitLabel={agent.card.submitLabel} onSelect={agent.answerClarification} onSubmit={agent.answerClarification} /> : null}{agent.card.type === 'confirmation' ? <ConfirmationCard title={agent.card.title} helperText={agent.card.helperText} data={agent.card.data} onConfirm={agent.confirmWrite} onEdit={agent.editWrite} onCancel={agent.cancelWrite} /> : null}{agent.card.type === 'error' ? <ErrorState message={agent.card.message} /> : null}</div></div><aside className={styles.tracePanel}><Card className={styles.panelCard} bordered={false}><div className={styles.panelHead}><strong>工具与引用</strong><Tag color="orange">Tools（{agent.run.toolsUsed}/{agent.run.toolsTotal}）</Tag></div><div className={styles.traceList}>{agent.run.toolCalls.map((tool) => <ToolTraceItem key={tool.id} tool={tool} />)}</div><div className={styles.citationList}>{agent.run.citations.map((citation) => <CitationBlock citation={citation} key={citation.id} />)}</div></Card></aside></section><Composer value={agent.input} running={agent.running} toolsUsed={agent.run.toolsUsed} toolsTotal={agent.run.toolsTotal} agentsUsed={agent.run.agentsUsed} agentsTotal={agent.run.agentsTotal} placeholder="输入任务，例如：给我做一周备餐计划 / 帮我记录今天午餐 / 分析最近一周蛋白质摄入..." onChange={agent.setInput} onSend={() => agent.send()} onStop={agent.stop} /></div>
    </WorkspaceLayout>
  );
}
