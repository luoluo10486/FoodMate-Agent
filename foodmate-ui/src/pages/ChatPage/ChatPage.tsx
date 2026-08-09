import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { AgentRunView, AgentDisplayStatus } from '../../types/agent';
import type { Message } from '../../types/session';
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
import {
  cancelAgentRun,
  extendAgentRunBudget,
  openAgentRunStream,
  recoverAgentRun,
} from '../../services/agentRunService';
import styles from './ChatPage.module.css';

type ChatMessage = {
  id: string;
  role: Message['role'];
  content: string;
  time: string;
};

function displayRunStatus(status: string): AgentDisplayStatus {
  if (status === 'queued' || status === 'routed') return 'routing';
  if (status === 'planning' || status === 'retrieving' || status === 'executing') {
    return status === 'executing' ? 'executing_tools' : status;
  }
  if (status === 'validating') return 'validating';
  if (status === 'waiting_user') return 'waiting_user';
  if (status === 'failed' || status === 'cancelled' || status === 'completed' || status === 'superseded') return status;
  return 'routing';
}

function runtimeErrorMessage(payload: { code?: string; error_message?: string; message?: string }) {
  if (payload.code === 'RUNTIME_COORDINATION_UNAVAILABLE') return '系统暂时异常，运行协调服务不可用，请稍后重试。';
  if (payload.code === 'RUNTIME_CAPACITY_EXCEEDED') return '当前运行队列已满，请稍后重试。';
  if (payload.code === 'RUNTIME_QUEUE_TIMEOUT') return '请求排队超时，请稍后重试。';
  if (payload.code === 'MODEL_PROVIDER_UNAVAILABLE') return '模型服务暂时不可用，请稍后重试。';
  return payload.error_message ?? payload.message ?? 'Agent 运行失败。';
}

function formatMessageTime(value: string) {
  if (!value.includes('-')) return value;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user';
  return (
    <article className={`${styles.message} ${isUser ? styles.user : styles.assistant}`}>
      {isUser ? (
        <>
          <div className={styles.userLine}>
            <div className={styles.messageBubble}>{message.content}</div>
            <span className={styles.srOnly}>你</span>
            <span className={styles.userAvatar} aria-hidden="true">
              梁
            </span>
          </div>
          <div className={styles.messageMeta}>Anddy · {formatMessageTime(message.time)} PM</div>
        </>
      ) : (
        <>
          <span className={styles.agentAvatar} aria-hidden="true">
            F
          </span>
          <div className={styles.assistantBody}>
            <div className={styles.messageBubble}>{message.content}</div>
            <div className={styles.messageMeta}>Fustat-v2 Agent · {formatMessageTime(message.time)} PM</div>
          </div>
        </>
      )}
    </article>
  );
}

function TraceRail({ run }: { run: AgentRunView }) {
  const [tab, setTab] = useState<'steps' | 'json'>('steps');
  return (
    <aside className={styles.tracePanel} aria-label="运行轨迹">
      <div className={styles.traceCard}>
        <header className={styles.traceHeader}>
          <strong>运行轨迹</strong>
          <span className={styles.srOnly}>工具与引用</span>
          <div className={styles.traceTabs} role="tablist" aria-label="运行轨迹视图">
            <button
              className={tab === 'steps' ? styles.traceTabActive : ''}
              type="button"
              onClick={() => setTab('steps')}
            >
              步骤
            </button>
            <button
              className={tab === 'json' ? styles.traceTabActive : ''}
              type="button"
              onClick={() => setTab('json')}
            >
              原始 JSON
            </button>
          </div>
        </header>
        {tab === 'steps' ? (
          <div className={styles.traceBody}>
            <span className={styles.runId}>RUN ID: {run.id}</span>
            {run.toolCalls.length ? (
              <div className={styles.traceList}>
                {run.toolCalls.map((tool) => (
                  <ToolTraceItem key={tool.id} tool={tool} />
                ))}
              </div>
            ) : (
              <div className={styles.traceEmpty}>等待运行事件...</div>
            )}
            {run.citations.length ? (
              <div className={styles.citationList}>
                {run.citations.map((citation) => (
                  <CitationBlock citation={citation} key={citation.id} />
                ))}
              </div>
            ) : null}
          </div>
        ) : (
          <pre className={styles.traceJson}>{JSON.stringify(run, null, 2)}</pre>
        )}
      </div>
    </aside>
  );
}

type ChatSurfaceProps = {
  run: AgentRunView;
  messagesRef: React.RefObject<HTMLDivElement>;
  children: ReactNode;
  input: string;
  running: boolean;
  disabled?: boolean;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  placeholder: string;
};

function ChatSurface({
  run,
  messagesRef,
  children,
  input,
  running,
  disabled,
  onChange,
  onSend,
  onStop,
  placeholder,
}: ChatSurfaceProps) {
  return (
    <WorkspaceLayout activeModule="chat" rightRail={<TraceRail run={run} />}>
      <div className={styles.page}>
        <section className={styles.workspace}>
          <div className={styles.center}>
            <AgentStatusStrip status={run.status} />
            <div className={styles.messages} ref={messagesRef}>
              {children}
            </div>
          </div>
        </section>
        <Composer
          value={input}
          running={running}
          disabled={disabled}
          toolsUsed={run.toolsUsed}
          toolsTotal={run.toolsTotal}
          agentsUsed={run.agentsUsed}
          agentsTotal={run.agentsTotal}
          placeholder={placeholder}
          onChange={onChange}
          onSend={onSend}
          onStop={onStop}
        />
      </div>
    </WorkspaceLayout>
  );
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
    setActiveRunId(undefined);
    setRunStatus('idle');
    setAssistantText('');
    setBudgetConfirmation(false);
    setCheckpointAvailable(false);
    if (!sessionId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(undefined);
    loadSessionMessages(sessionId)
      .then((rows) => {
        if (!cancelled) setMessages(rows.sort((a, b) => a.sequence_no - b.sequence_no));
      })
      .catch((reason) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : '消息加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  useEffect(() => {
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, assistantText]);

  useEffect(() => {
    if (!activeRunId) return undefined;
    setRunStatus('queued');
    setAssistantText('');
    const stream = openAgentRunStream(
      activeRunId,
      (eventType, payload) => {
        if (eventType === 'run.answer_stream') {
          setRunStatus('validating');
          setAssistantText((current) => current + (payload.text ?? ''));
          return;
        }
        if (eventType === 'run.completed') {
          setRunStatus('completed');
          setCheckpointAvailable(false);
          setAssistantText((current) => payload.answer ?? current);
          setBudgetConfirmation(
            payload.result_type === 'safety_degraded' &&
              (payload.requires_confirmation === true || payload.budget_actions?.requires_confirmation === true),
          );
          return;
        }
        if (eventType === 'run.checkpoint_saved') {
          setRunStatus('waiting_user');
          setCheckpointAvailable(true);
          return;
        }
        if (eventType === 'run.failed') {
          setRunStatus('failed');
          setCheckpointAvailable(false);
          setError(runtimeErrorMessage(payload));
          return;
        }
        if (eventType === 'run.cancelled') {
          setRunStatus('cancelled');
          setCheckpointAvailable(false);
          return;
        }
        if (eventType === 'run.superseded') {
          setRunStatus('superseded');
          setCheckpointAvailable(false);
          return;
        }
        if (eventType === 'run.clarification_requested') {
          setRunStatus('waiting_user');
          return;
        }
        setRunStatus(payload.status ?? eventType.replace('run.', ''));
      },
      () => setError('运行事件连接中断，浏览器将自动重连。'),
    );
    return () => stream.close();
  }, [activeRunId]);

  const send = async () => {
    const content = input.trim();
    if (!content || sending) return;
    setError(undefined);
    setSending(true);
    try {
      let target = sessionId;
      if (!target) {
        const created = await createSession(content.slice(0, 40));
        target = String(created.session_id);
        navigate(`/chat/${target}`, { replace: true });
      }
      const saved = await sendUserMessage(target, content);
      setMessages((current) => [...current, saved].sort((a, b) => a.sequence_no - b.sequence_no));
      if (saved.agent_run_id) setActiveRunId(String(saved.agent_run_id));
      setInput('');
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === 'FORBIDDEN') setError(reason.message);
      setError(reason instanceof Error ? reason.message : '消息发送失败');
    } finally {
      setSending(false);
    }
  };

  const realRun: AgentRunView = {
    id: activeRunId ?? '等待运行',
    status: displayRunStatus(runStatus === 'idle' ? 'completed' : runStatus),
    intent: 'planning',
    toolsUsed: 0,
    toolsTotal: 6,
    agentsUsed: 0,
    agentsTotal: 1,
    toolCalls: [],
    citations: [],
  };

  const mappedMessages: ChatMessage[] = messages.map((message) => ({
    id: message.message_id,
    role: message.role,
    content: message.content,
    time: message.created_at,
  }));

  return (
    <ChatSurface
      run={realRun}
      messagesRef={messagesRef}
      input={input}
      running={
        runStatus !== 'idle' && !['completed', 'failed', 'cancelled', 'waiting_user', 'superseded'].includes(runStatus)
      }
      disabled={loading || sending}
      onChange={setInput}
      onSend={() => void send()}
      onStop={() => {
        if (activeRunId) void cancelAgentRun(activeRunId);
      }}
      placeholder="输入要保存到会话中的内容..."
    >
      {loading ? <p className={styles.systemMessage}>正在加载消息...</p> : null}
      {!loading && mappedMessages.length === 0 ? (
        <p className={styles.systemMessage}>暂无消息，发送第一条内容开始会话。</p>
      ) : null}
      {error ? <ErrorState message={error} /> : null}
      {mappedMessages.map((message) => (
        <MessageBubble key={message.id} message={message} />
      ))}
      {assistantText ? (
        <MessageBubble message={{ id: 'assistant-stream', role: 'assistant', content: assistantText, time: '12:46' }} />
      ) : null}
      {checkpointAvailable && activeRunId ? (
        <div className={styles.cardWrap}>
          <ConfirmationCard
            title="运行已暂停，可从检查点继续"
            helperText="系统已保存运行进度。继续后会创建新的 dispatch attempt，不会重复已完成的工具调用。"
            data={[
              { label: '恢复方式', value: '从已校验 checkpoint 恢复' },
              { label: '安全校验', value: 'Java 服务端完成' },
            ]}
            onConfirm={() => {
              void recoverAgentRun(activeRunId)
                .then(() => {
                  setCheckpointAvailable(false);
                  setRunStatus('queued');
                })
                .catch((reason) => setError(reason instanceof Error ? reason.message : '运行恢复失败'));
            }}
            onEdit={() => setError('当前恢复入口不接受浏览器修改 checkpoint 内容。')}
            onCancel={() => setCheckpointAvailable(false)}
          />
        </div>
      ) : null}
      {budgetConfirmation && activeRunId ? (
        <div className={styles.cardWrap}>
          <ConfirmationCard
            title="本次运行已达到预算上限"
            helperText="继续执行会创建新的预算 revision，并接续当前 Run。"
            data={[
              { label: '追加 Token', value: '30000' },
              { label: '追加成本上限', value: '¥1.00' },
            ]}
            onConfirm={() => {
              void extendAgentRunBudget(activeRunId, 30000, '1.00')
                .then(() => setBudgetConfirmation(false))
                .catch((reason) => setError(reason instanceof Error ? reason.message : '预算追加失败'));
            }}
            onEdit={() => setError('当前开发版本使用固定追加额度。')}
            onCancel={() => setBudgetConfirmation(false)}
          />
        </div>
      ) : null}
    </ChatSurface>
  );
}

function MockChatPage() {
  const params = useParams();
  const sessionId = params.session_id;
  const [searchParams] = useSearchParams();
  const agent = useAgentReplay(sessionId, searchParams.get('prompt'));
  const messagesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [agent.messages, agent.card]);

  return (
    <ChatSurface
      run={agent.run}
      messagesRef={messagesRef}
      input={agent.input}
      running={agent.running}
      onChange={agent.setInput}
      onSend={() => agent.send()}
      onStop={agent.stop}
      placeholder="输入任务，例如：给我做一周备餐计划 / 帮我记录今天午餐 / 分析最近一周蛋白质摄入..."
    >
      {agent.messages.map((message) => (
        <MessageBubble key={message.id} message={message} />
      ))}
      {agent.card.type === 'result' ? (
        <div className={styles.cardWrap}>
          <ResultCard
            label={agent.card.label}
            title={agent.card.title}
            description={agent.card.description}
            primaryAction={agent.card.primaryAction}
            secondaryAction={agent.card.secondaryAction}
            onPrimary={agent.handleResultPrimary}
            onSecondary={agent.handleResultSecondary}
          />
        </div>
      ) : null}
      {agent.card.type === 'clarification' ? (
        <div className={styles.cardWrap}>
          <ClarificationCard
            title={agent.card.title}
            options={agent.card.options}
            fields={agent.card.fields}
            submitLabel={agent.card.submitLabel}
            onSelect={agent.answerClarification}
            onSubmit={agent.answerClarification}
          />
        </div>
      ) : null}
      {agent.card.type === 'confirmation' ? (
        <div className={styles.cardWrap}>
          <ConfirmationCard
            title={agent.card.title}
            helperText={agent.card.helperText}
            data={agent.card.data}
            onConfirm={agent.confirmWrite}
            onEdit={agent.editWrite}
            onCancel={agent.cancelWrite}
          />
        </div>
      ) : null}
      {agent.card.type === 'error' ? <ErrorState message={agent.card.message} /> : null}
      <section className={styles.messageActions} aria-label="消息操作">
        <h2>消息操作</h2>
        <p>用户消息：编辑 · 复制 · 重试（保留原消息并新建一次运行）</p>
        <p>Agent 回答：复制 · 查看引用 · 查看运行详情 · 继续提问</p>
        <p className={styles.actionGreen}>
          工具失败时显示重试；运行中发送按钮切换停止；写入确认/预算追加仍需确认后继续。
        </p>
        <p>右侧面板：运行 · 工具 · 引用 · 原始 JSON 默认折叠并隐藏敏感参数。</p>
      </section>
    </ChatSurface>
  );
}
