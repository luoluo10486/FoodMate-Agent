import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
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
import { cancelAgentRun, extendAgentRunBudget, openAgentRunStream, recoverAgentRun } from '../../services/agentRunService';
import styles from './ChatPage.module.css';

type TagProps = {
  color?: string;
  children: ReactNode;
};

function Tag({ color, children }: TagProps) {
  const variant = color === 'red' ? 'destructive' : color === 'orange' ? 'warning' : color === 'gray' ? 'secondary' : 'default';
  return <Badge variant={variant}>{children}</Badge>;
}

function displayRunStatus(status: string) {
  if (status === 'queued' || status === 'routed') return 'routing' as const;
  if (status === 'planning' || status === 'retrieving' || status === 'executing') return status === 'executing' ? 'executing_tools' as const : status as 'planning' | 'retrieving';
  if (status === 'validating') return 'validating' as const;
  if (status === 'waiting_user') return 'waiting_user' as const;
  if (status === 'failed' || status === 'cancelled' || status === 'completed' || status === 'superseded') return status;
  return 'routing' as const;
}

function runtimeErrorMessage(payload: { code?: string; error_message?: string; message?: string }) {
  if (payload.code === 'RUNTIME_COORDINATION_UNAVAILABLE') return '系统暂时异常，运行协调服务不可用，请稍后重试。';
  if (payload.code === 'RUNTIME_CAPACITY_EXCEEDED') return '当前运行队列已满，请稍后重试。';
  if (payload.code === 'RUNTIME_QUEUE_TIMEOUT') return '请求排队超时，请稍后重试。';
  if (payload.code === 'MODEL_PROVIDER_UNAVAILABLE') return '模型服务暂时不可用，请稍后重试。';
  return payload.error_message ?? payload.message ?? 'Agent 运行失败。';
}

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
  const [activeRunId, setActiveRunId] = useState<string>();
  const [runStatus, setRunStatus] = useState('idle');
  const [assistantText, setAssistantText] = useState('');
  const [error, setError] = useState<string>();
  const [budgetConfirmation, setBudgetConfirmation] = useState(false);
  const [checkpointAvailable, setCheckpointAvailable] = useState(false);
  const messagesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    // 切换会话时必须清理旧 Run，否则新会话会继续订阅上一会话的 SSE。
    setActiveRunId(undefined);
    setRunStatus('idle');
    setAssistantText('');
    setBudgetConfirmation(false);
    setCheckpointAvailable(false);
    if (!sessionId) { setLoading(false); return; }
    setLoading(true); setError(undefined);
    loadSessionMessages(sessionId).then((rows) => { if (!cancelled) setMessages(rows.sort((a, b) => a.sequence_no - b.sequence_no)); }).catch((reason) => { if (!cancelled) setError(reason instanceof Error ? reason.message : '消息加载失败'); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [sessionId]);

  useEffect(() => { messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' }); }, [messages]);

  useEffect(() => {
    if (!activeRunId) return undefined;
    setRunStatus('queued'); setAssistantText('');
    const stream = openAgentRunStream(activeRunId, (eventType, payload) => {
      if (eventType === 'run.answer_stream') { setRunStatus('validating'); setAssistantText((current) => current + (payload.text ?? '')); return; }
      if (eventType === 'run.completed') {
        setRunStatus('completed'); setCheckpointAvailable(false); setAssistantText(payload.answer ?? assistantText);
         setBudgetConfirmation(payload.result_type === 'safety_degraded' && (payload.requires_confirmation === true || payload.budget_actions?.requires_confirmation === true));
        return;
      }
      if (eventType === 'run.checkpoint_saved') {
        setRunStatus('waiting_user');
        setCheckpointAvailable(true);
        return;
      }
      if (eventType === 'run.failed') { setRunStatus('failed'); setCheckpointAvailable(false); setError(runtimeErrorMessage(payload)); return; }
      if (eventType === 'run.cancelled') { setRunStatus('cancelled'); setCheckpointAvailable(false); return; }
      if (eventType === 'run.superseded') { setRunStatus('superseded'); setCheckpointAvailable(false); return; }
      if (eventType === 'run.clarification_requested') { setRunStatus('waiting_user'); return; }
      setRunStatus(payload.status ?? eventType.replace('run.', ''));
    }, () => setError('运行事件连接中断，浏览器将自动重连。'));
    return () => stream.close();
  }, [activeRunId]);

  const send = async () => {
    const content = input.trim();
    if (!content || sending) return;
    setError(undefined); setSending(true);
    try {
      let target = sessionId;
      if (!target) { const created = await createSession(content.slice(0, 40)); target = String(created.session_id); navigate(`/chat/${target}`, { replace: true }); }
      const saved = await sendUserMessage(target, content);
      setMessages((current) => [...current, saved].sort((a, b) => a.sequence_no - b.sequence_no));
      if (saved.agent_run_id) setActiveRunId(String(saved.agent_run_id));
      setInput('');
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === 'FORBIDDEN') setError(reason.message);
      setError(reason instanceof Error ? reason.message : '消息发送失败');
    } finally { setSending(false); }
  };

  return (
    <WorkspaceLayout activeModule="chat">
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.workspace}>
          <div className={styles.center}>
            <AgentStatusStrip status={displayRunStatus(runStatus === 'idle' ? 'completed' : runStatus)} />
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
                {assistantText ? <article className={`${styles.message} ${styles.assistant}`}><Tag color="green">FoodMate Agent</Tag><p>{assistantText}</p></article> : null}
                {checkpointAvailable && activeRunId ? <ConfirmationCard
                  title="运行已暂停，可从检查点继续"
                  helperText="系统已保存运行进度。继续后会创建新的 dispatch attempt，不会重复已完成的工具调用。"
                  data={[{ label: '恢复方式', value: '从已校验 checkpoint 恢复' }, { label: '安全校验', value: 'Java 服务端完成' }]}
                  onConfirm={() => { void recoverAgentRun(activeRunId).then(() => { setCheckpointAvailable(false); setRunStatus('queued'); }).catch((reason) => setError(reason instanceof Error ? reason.message : '运行恢复失败')); }}
                  onEdit={() => setError('当前恢复入口不接受浏览器修改 checkpoint 内容。')}
                  onCancel={() => setCheckpointAvailable(false)}
                /> : null}
                {budgetConfirmation && activeRunId ? <ConfirmationCard
                title="本次运行已达到预算上限"
                helperText="继续执行会创建新的预算 revision，并接续当前 Run。"
                data={[{ label: '追加 Token', value: '30000' }, { label: '追加成本上限', value: '¥1.00' }]}
                onConfirm={() => { void extendAgentRunBudget(activeRunId, 30000, '1.00').then(() => setBudgetConfirmation(false)).catch((reason) => setError(reason instanceof Error ? reason.message : '预算追加失败')); }}
                onEdit={() => setError('当前开发版本使用固定追加额度。')}
                onCancel={() => setBudgetConfirmation(false)}
              /> : null}
            </div>
          </div>
          <aside className={styles.tracePanel}>
            <Card className={styles.panelCard}>
              <div className={styles.panelHead}><strong>Agent 运行</strong><Tag color="gray">Eval 后发布回答</Tag></div>
              <p>回答会先经过运行时校验；模型用量、预算状态和降级原因由服务端事件记录。</p>
            </Card>
          </aside>
        </section>
        <Composer value={input} running={runStatus !== 'idle' && !['completed', 'failed', 'cancelled', 'waiting_user', 'superseded'].includes(runStatus)} disabled={loading || sending} toolsUsed={0} toolsTotal={0} agentsUsed={0} agentsTotal={0} placeholder="输入要保存到会话中的内容..." onChange={setInput} onSend={() => void send()} onStop={() => { if (activeRunId) void cancelAgentRun(activeRunId); }} />
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
        <section className={styles.workspace}><div className={styles.center}><AgentStatusStrip status={agent.run.status} /><div className={styles.messages} ref={messagesRef}>{agent.messages.map((message) => <article className={`${styles.message} ${styles[message.role]}`} key={message.id}><Tag color={message.role === 'user' ? 'gray' : 'green'}>{message.role === 'user' ? '你' : 'FoodMate'}</Tag><p>{message.content}</p><span>{message.time}</span></article>)}{agent.card.type === 'result' ? <ResultCard label={agent.card.label} title={agent.card.title} description={agent.card.description} primaryAction={agent.card.primaryAction} secondaryAction={agent.card.secondaryAction} onPrimary={agent.handleResultPrimary} onSecondary={agent.handleResultSecondary} /> : null}{agent.card.type === 'clarification' ? <ClarificationCard title={agent.card.title} options={agent.card.options} fields={agent.card.fields} submitLabel={agent.card.submitLabel} onSelect={agent.answerClarification} onSubmit={agent.answerClarification} /> : null}{agent.card.type === 'confirmation' ? <ConfirmationCard title={agent.card.title} helperText={agent.card.helperText} data={agent.card.data} onConfirm={agent.confirmWrite} onEdit={agent.editWrite} onCancel={agent.cancelWrite} /> : null}{agent.card.type === 'error' ? <ErrorState message={agent.card.message} /> : null}</div></div><aside className={styles.tracePanel}><Card className={styles.panelCard}><div className={styles.panelHead}><strong>工具与引用</strong><Tag color="orange">Tools（{agent.run.toolsUsed}/{agent.run.toolsTotal}）</Tag></div><div className={styles.traceList}>{agent.run.toolCalls.map((tool) => <ToolTraceItem key={tool.id} tool={tool} />)}</div><div className={styles.citationList}>{agent.run.citations.map((citation) => <CitationBlock citation={citation} key={citation.id} />)}</div></Card></aside></section><Composer value={agent.input} running={agent.running} toolsUsed={agent.run.toolsUsed} toolsTotal={agent.run.toolsTotal} agentsUsed={agent.run.agentsUsed} agentsTotal={agent.run.agentsTotal} placeholder="输入任务，例如：给我做一周备餐计划 / 帮我记录今天午餐 / 分析最近一周蛋白质摄入..." onChange={agent.setInput} onSend={() => agent.send()} onStop={agent.stop} /></div>
    </WorkspaceLayout>
  );
}
