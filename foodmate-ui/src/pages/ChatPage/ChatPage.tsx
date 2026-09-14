import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  CalendarDays,
  Check,
  ChartColumn,
  CircleSlash,
  LoaderCircle,
  MessageCircle,
  Pencil,
  RefreshCw,
  Search,
  Trash2,
  XCircle,
  X,
} from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import type {
  AgentRunView,
  AgentDisplayStatus,
  AgentStreamConnection,
  AgentStreamHandle,
  ToolCall,
} from '../../types/agent';
import type { Message } from '../../types/session';
import type { SessionSummary } from '../../types/session';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { AgentStatusStrip } from '../../components/agent/AgentStatusStrip';
import { CitationBlock } from '../../components/agent/CitationBlock';
import { AgentFeedback } from '../../components/agent/AgentFeedback';
import { ResultCard } from '../../components/agent/ResultCard';
import { ClarificationCard } from '../../components/agent/ClarificationCard';
import { ConfirmationCard } from '../../components/agent/ConfirmationCard';
import { ErrorState } from '../../components/common/ErrorState';
import { AvatarImage } from '../../components/common/AvatarImage';
import { FIXTURE_ACCOUNT_AVATAR, FIXTURE_CHAT_AVATAR_GENDERS, resolveAvatarUrl } from '../../lib/avatar';
import { flattenAgentEventPayload, resolveAgentEventType } from '../../lib/agentEvent';
import { getAuthUser } from '../../services/authService';
import { useAgentReplay } from '../../services/agentService';
import { useRealAgentReplay } from '../../services/realAgentService';
import { ApiError } from '../../services/apiClient';
import {
  createSession,
  deleteMessage,
  loadSessionMessages,
  sendUserMessage,
  updateMessage,
  type RealMessage,
} from '../../services/sessionService';
import {
  cancelAgentRun,
  createApprovalProposal,
  confirmAgentWrite,
  executeAgentWrite,
  extendAgentRunBudget,
  loadAgentRun,
  loadApprovalProposal,
  openAgentRunStream,
  recoverAgentRun,
  rejectAgentWrite,
  recoverAgentRunFromCheckpoint,
  retryAgentRun,
  type AgentRecoveryRequest,
  type AgentRunEvent,
} from '../../services/agentRunService';
import styles from './ChatPage.module.css';

// Chat Fixture 的人物身份只有一个示例账号，壳层和用户消息必须引用同一份登记头像。
const FIXTURE_CHAT_ACCOUNT_AVATAR = FIXTURE_ACCOUNT_AVATAR;

type ChatMessage = {
  id: string;
  role: Message['role'];
  content: string;
  time: string;
  source?: string;
  wide?: boolean;
  agentRunId?: string;
};

type ChatNavigationState = {
  sendError?: string;
  pendingPrompt?: string;
};

function readChatNavigationState(value: unknown): ChatNavigationState {
  if (!value || typeof value !== 'object') return {};
  const state = value as Record<string, unknown>;
  return {
    sendError: typeof state.sendError === 'string' ? state.sendError : undefined,
    pendingPrompt: typeof state.pendingPrompt === 'string' ? state.pendingPrompt : undefined,
  };
}

function displayRunStatus(status: string): AgentDisplayStatus {
  const normalized = status.trim().toLowerCase();
  if (normalized === 'queued' || normalized === 'dispatched' || normalized === 'routed') return 'routing';
  if (normalized === 'planning' || normalized === 'retrieving' || normalized === 'executing') {
    return normalized === 'executing' ? 'executing_tools' : normalized;
  }
  if (normalized === 'running') return 'executing_tools';
  if (normalized === 'validating') return 'validating';
  if (normalized === 'composing') return 'composing';
  if (normalized === 'waiting_user') return 'waiting_user';
  if (normalized === 'failed' || normalized === 'cancelled' || normalized === 'canceled')
    return 'failed' === normalized ? 'failed' : 'cancelled';
  if (normalized === 'completed' || normalized === 'succeeded') return 'completed';
  if (normalized === 'superseded') return 'superseded';
  return 'routing';
}

function runtimeErrorMessage(payload: { code?: string; error_message?: string; message?: string }) {
  if (payload.code === 'RUNTIME_COORDINATION_UNAVAILABLE') return '系统暂时异常，运行协调服务不可用，请稍后重试。';
  if (payload.code === 'RUNTIME_CAPACITY_EXCEEDED') return '当前运行队列已满，请稍后重试。';
  if (payload.code === 'RUNTIME_QUEUE_TIMEOUT') return '请求排队超时，请稍后重试。';
  if (payload.code === 'MODEL_PROVIDER_UNAVAILABLE') return '模型服务暂时不可用，请稍后重试。';
  return (
    payload.error_message ??
    payload.message ??
    (payload.code ? `运行失败（错误码：${payload.code}）。` : 'Agent 运行失败。')
  );
}

function messageMutationError(reason: unknown, fallback: string) {
  if (reason instanceof ApiError) {
    if (reason.status === 409 || ['CONFLICT', 'MESSAGE_CONFLICT', 'VERSION_CONFLICT'].includes(reason.code)) {
      return '这条消息已被其他操作更新，请重新加载会话后再试。';
    }
    if (reason.code === 'FORBIDDEN') return '当前账号无权操作这条消息。';
    return reason.message;
  }
  return reason instanceof Error ? reason.message : fallback;
}

function formatMessageTime(value: string) {
  if (!value.includes('-')) return value;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function normalizeRunIntent(value: string | undefined): AgentRunView['intent'] {
  if (value === 'calculation' || value === 'record' || value === 'analysis' || value === 'planning') return value;
  return 'knowledge_qna';
}

function toolDisplayName(name: string) {
  const labels: Record<string, string> = {
    calculator: '营养计算',
    database_query: '饮食数据查询',
    food_log_writer: '饮食记录写入',
    knowledge_search: '知识库检索',
    'meal_plan.save_plan': '餐食计划保存',
    plan_validator: '计划校验',
    time_parser: '时间范围解析',
  };
  return labels[name] ?? name;
}

function toolStatus(value: string | undefined): ToolCall['status'] {
  if (value === 'succeeded' || value === 'success' || value === 'completed') return 'success';
  if (value === 'confirmation_required' || value === 'pending') return 'pending';
  if (value === 'timeout' || value === 'timed_out') return 'timeout';
  if (value === 'cancelled' || value === 'canceled') return 'cancelled';
  if (value === 'failed' || value === 'error') return 'failed';
  return 'running';
}

function toolIdentity(payload: AgentRunEvent) {
  return payload.proposal_id || payload.invocation_id || payload.tool_name || `tool-${Date.now()}`;
}

function mergeToolCall(current: ToolCall[], payload: AgentRunEvent, phase: 'started' | 'finished') {
  const id = toolIdentity(payload);
  const name = payload.tool_name || payload.tool_type || 'unknown_tool';
  const index = current.findIndex((tool) => tool.id === id);
  const previous = index >= 0 ? current[index] : undefined;
  const next: ToolCall = {
    id,
    name,
    displayName: toolDisplayName(name),
    status: phase === 'started' ? 'running' : toolStatus(payload.status),
    latencyMs: payload.latency_ms ?? previous?.latencyMs,
    summary:
      phase === 'started'
        ? '正在执行'
        : payload.error_code
          ? `执行失败：${payload.error_code}`
          : payload.status === 'confirmation_required'
            ? '等待确认'
            : '已完成',
    error: payload.error_code,
  };
  if (index < 0) return [...current, next];
  return current.map((tool, itemIndex) => (itemIndex === index ? { ...tool, ...next } : tool));
}

function MessageBubble({
  message,
  children,
  userAvatarSrc,
  userAvatarGender,
  userActions,
  editing = false,
  editValue = '',
  editPending = false,
  onEditChange,
  onEditSave,
  onEditCancel,
}: {
  message: ChatMessage;
  children?: ReactNode;
  userAvatarSrc?: string;
  userAvatarGender?: string;
  userActions?: ReactNode;
  editing?: boolean;
  editValue?: string;
  editPending?: boolean;
  onEditChange?: (value: string) => void;
  onEditSave?: () => void;
  onEditCancel?: () => void;
}) {
  const isUser = message.role === 'user';
  const authUser = getAuthUser();
  // 消息头像也走统一解析，避免历史 Fixture 路径通过组件参数直接渲染。
  // 设计 Fixture 的消息性别可能与当前登录账号不同，优先采用消息自身登记的性别。
  const resolvedGender = userAvatarGender ?? authUser.gender;
  const userAvatar = resolveAvatarUrl(userAvatarSrc ?? authUser.avatarUrl, resolvedGender);
  const fixtureAvatar = import.meta.env.VITE_AGENT_MODE !== 'real' || Boolean(userAvatarSrc || userAvatarGender);
  return (
    <article className={`${styles.message} ${isUser ? styles.user : styles.assistant}`}>
      {isUser ? (
        <>
          <div className={styles.userLine}>
            <div className={styles.messageBubble}>
              {editing ? (
                <div className={styles.messageEditor}>
                  <Textarea
                    aria-label="编辑消息内容"
                    autoFocus
                    value={editValue}
                    disabled={editPending}
                    onChange={(event) => onEditChange?.(event.target.value)}
                  />
                  <div className={styles.messageEditorActions}>
                    <Button
                      aria-label="取消编辑消息"
                      title="取消编辑"
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={editPending}
                      onClick={onEditCancel}
                    >
                      <X aria-hidden="true" />
                    </Button>
                    <Button
                      aria-label="保存消息"
                      title="保存消息"
                      type="button"
                      variant="secondary"
                      size="icon"
                      disabled={editPending || !editValue.trim()}
                      onClick={onEditSave}
                    >
                      {editPending ? (
                        <LoaderCircle className="animate-spin" aria-hidden="true" />
                      ) : (
                        <Check aria-hidden="true" />
                      )}
                    </Button>
                  </div>
                </div>
              ) : (
                message.content
              )}
            </div>
            <span className={styles.srOnly}>你</span>
            <span className={styles.userAvatar} aria-hidden="true">
              <AvatarImage
                avatarUrl={fixtureAvatar ? undefined : userAvatar}
                allowUploaded={!fixtureAvatar}
                data-avatar-role={fixtureAvatar ? 'fixture-message' : 'authenticated-message'}
                defaultOnly={fixtureAvatar}
                gender={resolvedGender}
                alt=""
              />
            </span>
          </div>
          <div className={styles.messageMeta}>Anddy · {formatMessageTime(message.time)} PM</div>
          {userActions && !editing ? <div className={styles.userMessageActions}>{userActions}</div> : null}
        </>
      ) : (
        <>
          <AgentStatusMarker className={styles.agentAvatar} />
          <div className={`${styles.assistantBody} ${message.wide ? styles.assistantBodyWide : ''}`}>
            <div className={styles.messageBubble}>
              <p className={styles.messageText}>{message.content}</p>
              {message.source ? <div className={styles.source}>{message.source}</div> : null}
              {children}
            </div>
            <div className={styles.messageMeta}>Fustat-v2 Agent · {formatMessageTime(message.time)} PM</div>
          </div>
        </>
      )}
    </article>
  );
}

/** Figma 中的 Agent 状态块是绿色状态标记，不属于人物头像资源。 */
function AgentStatusMarker({ className }: { className: string }) {
  return (
    <span
      className={className}
      aria-hidden="true"
      data-agent-marker="figma-status-surface"
      data-visual-role="agent-status-marker"
    />
  );
}

function TraceRail({ run, designChat = false }: { run: AgentRunView; designChat?: boolean }) {
  const [tab, setTab] = useState<'steps' | 'json'>('steps');
  return (
    <aside className={`${styles.tracePanel} ${designChat ? styles.designTracePanel : ''}`} aria-label="运行轨迹">
      <div className={styles.traceTitle}>运行轨迹</div>
      <span className={styles.srOnly}>工具与引用</span>
      <Tabs value={tab} onValueChange={(value) => setTab(value as 'steps' | 'json')}>
        <TabsList className={styles.traceTabs} aria-label="运行轨迹视图">
          <TabsTrigger value="steps">步骤</TabsTrigger>
          <TabsTrigger value="json">原始 JSON</TabsTrigger>
        </TabsList>
        <TabsContent className={styles.traceBody} value="steps">
          <span className={styles.runId}>RUN ID: {run.id}</span>
          {run.toolCalls.length ? (
            <div className={styles.traceList}>
              {run.toolCalls.map((tool) => (
                <div
                  className={`${styles.traceStep} ${tool.status === 'running' ? styles.traceStepActive : ''} ${tool.status === 'pending' ? styles.traceStepPending : ''}`}
                  key={tool.id}
                >
                  <strong>{tool.displayName || tool.name}</strong>
                  <span>{tool.latencyMs ? `${tool.latencyMs}ms` : tool.status}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className={styles.traceEmpty}>等待运行事件...</div>
          )}
        </TabsContent>
        <TabsContent className={styles.traceBody} value="json">
          <pre className={styles.traceJson}>{JSON.stringify(run, null, 2)}</pre>
        </TabsContent>
      </Tabs>
    </aside>
  );
}

function CitationList({ citations }: { citations: AgentRunView['citations'] }) {
  if (!citations.length) return null;
  return (
    <div className={styles.citationList} aria-label="知识库引用">
      {citations.map((citation) => (
        <CitationBlock citation={citation} key={citation.id} />
      ))}
    </div>
  );
}

function InlineConfirmationCard({ onConfirm, onCancel }: { onConfirm: () => void; onCancel: () => void }) {
  return (
    <section className={styles.inlineConfirmation} aria-label="饮食记录确认">
      <h3>是否将此记录到你的周二饮食日志？</h3>
      <RadioGroup aria-label="饮食记录目标" className={styles.inlineConfirmationOptions} defaultValue="add-to-lunch">
        <div className={styles.inlineConfirmationOption}>
          <RadioGroupItem aria-label="是，添加到今天的午餐" id="meal-log-add-to-lunch" value="add-to-lunch" />
          <label htmlFor="meal-log-add-to-lunch">是，添加到今天的午餐</label>
        </div>
        <div className={styles.inlineConfirmationOption}>
          <RadioGroupItem aria-label="否，仅作为对话参考" id="meal-log-reference-only" value="reference-only" />
          <label htmlFor="meal-log-reference-only">否，仅作为对话参考</label>
        </div>
      </RadioGroup>
      <div className={styles.inlineConfirmationActions}>
        <Button type="button" onClick={onConfirm}>
          提交并继续
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          取消
        </Button>
      </div>
    </section>
  );
}

function MessageActionsPanel() {
  return (
    <section className={styles.messageActions} aria-labelledby="figma-message-actions-title" data-node-id="983:3">
      <h2 id="figma-message-actions-title">消息操作</h2>
      <p>用户消息：编辑 · 复制 · 重试（保留原消息并新建一次运行）</p>
      <p>Agent 回答：复制 · 查看引用 · 查看运行详情 · 继续提问</p>
      <p className={styles.messageActionsNote}>
        工具失败时显示重试；运行中发送按钮切换停止；写入确认 / 预算追加仍需确认后继续。
      </p>
      <p className={styles.messageActionsMuted}>右侧面板：运行 · 工具 · 引用 原始 JSON 默认折叠并隐藏敏感参数。</p>
    </section>
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
  showTrace?: boolean;
  avatarSrc?: string;
  sidebarAvatarSrc?: string;
  topAvatarSrc?: string;
  displayNameOverride?: string;
  profileIdOverride?: string;
  showKnowledgeTopNav?: boolean;
  designChat?: boolean;
  fixtureVariant?: 'chat';
  pageVariant?: 'completed-citations' | 'figma-default' | 'running-stop';
  statusForStrip?: AgentDisplayStatus;
  statusVisualState?: 'user-cancelled';
  pageOverlay?: ReactNode;
  sidebarFixture?: {
    sessions: SessionSummary[];
    currentPage: number;
    searchValue?: string;
  };
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
  showTrace = true,
  avatarSrc,
  sidebarAvatarSrc,
  topAvatarSrc,
  displayNameOverride,
  profileIdOverride,
  showKnowledgeTopNav,
  designChat,
  fixtureVariant,
  pageVariant,
  statusForStrip,
  statusVisualState,
  pageOverlay,
  sidebarFixture,
}: ChatSurfaceProps) {
  // 所有设计态 Chat 页面共享同一组 Figma 壳层资源和默认头像策略。
  const resolvedFixtureVariant = fixtureVariant ?? (designChat ? 'chat' : undefined);

  return (
    <WorkspaceLayout
      activeModule="chat"
      avatarSrc={avatarSrc}
      designChat={designChat}
      fixtureVariant={resolvedFixtureVariant}
      displayNameOverride={displayNameOverride}
      profileIdOverride={profileIdOverride}
      pageOverlay={pageOverlay}
      rightRail={showTrace ? <TraceRail run={run} designChat={designChat} /> : undefined}
      sidebarFixture={sidebarFixture}
      showKnowledgeTopNav={showKnowledgeTopNav}
      sidebarAvatarSrc={sidebarAvatarSrc}
      topAvatarSrc={topAvatarSrc}
    >
      <div
        className={`${styles.page} ${designChat ? styles.designChatPage : ''} ${pageVariant === 'completed-citations' ? styles.completedCitationsPage : ''} ${pageVariant === 'figma-default' ? styles.figmaDefaultPage : ''} ${pageVariant === 'running-stop' ? styles.runningStopPage : ''}`}
      >
        <section className={styles.workspace}>
          <div className={styles.center}>
            <AgentStatusStrip
              status={statusForStrip ?? run.status}
              failedStep={run.failedStep}
              preserveTones={designChat}
              visualState={statusVisualState}
            />
            <div className={styles.messages} ref={messagesRef}>
              {children}
              {pageVariant === 'running-stop' ? (
                <div className={styles.runningStatus} role="status" aria-live="polite">
                  执行中 · 可随时停止
                </div>
              ) : null}
            </div>
          </div>
        </section>
        <Composer
          value={input}
          running={running}
          disabled={disabled}
          placeholder={placeholder}
          onChange={onChange}
          onSend={onSend}
          onStop={onStop}
          runningLabel={pageVariant === 'running-stop' ? '停止运行' : undefined}
          fixtureVariant={resolvedFixtureVariant}
        />
      </div>
    </WorkspaceLayout>
  );
}

function approvalParameters(details: NonNullable<AgentRunEvent['details']>, resourceType?: string) {
  if (resourceType === 'meal_plan') return { plan: details.plan ?? {} };
  return {
    meal_time: details.meal_time,
    meal_type: details.meal_type,
    notes: details.notes ?? null,
    items: (details.items ?? []).map((item) => ({
      name: item.name,
      amount: item.amount,
      unit: item.unit,
    })),
  };
}

function approvalData(details: NonNullable<AgentRunEvent['details']>, resourceType?: string) {
  if (resourceType === 'meal_plan') {
    const plan = details.plan ?? {};
    return [
      { label: '计划名称', value: plan.plan_name ?? '未命名计划' },
      {
        label: '计划范围',
        value: `${plan.days ?? '未设置'} 天 · ${plan.people ?? '未设置'} 人`,
      },
      { label: '能量目标', value: plan.calorie_target == null ? '未设置' : `${plan.calorie_target} kcal/天` },
      { label: '蛋白质目标', value: plan.protein_target == null ? '未设置' : `${plan.protein_target} g/天` },
      { label: '预算', value: plan.budget == null ? '未设置' : `${plan.budget} 元/天` },
      { label: '过敏源', value: plan.allergens?.join('、') || '无' },
      { label: '忌口', value: plan.dislikes?.join('、') || '无' },
    ];
  }
  return [
    { label: '餐型', value: details.meal_type ?? '未识别' },
    { label: '时间', value: details.meal_time ?? '未识别' },
    {
      label: '食物',
      value: (details.items ?? [])
        .map((item) => `${item.name ?? '未命名'} ${item.amount ?? ''}${item.unit ?? ''}`)
        .join('、'),
    },
  ];
}

export function ChatPage() {
  const [searchParams] = useSearchParams();
  const realMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const auxiliaryState = getChatAuxState(searchParams.get('state'));
  // 这些状态只用于 Figma 视觉预览，真实模式必须回到后端驱动的 Chat 页面。
  if (!realMode && auxiliaryState) return <ChatAuxStatePage state={auxiliaryState} />;
  if (!realMode && searchParams.get('state') === 'empty') return <EmptyChatPage />;
  if (!realMode && searchParams.get('state') === 'planning') return <PlanningStatePage />;
  if (!realMode && searchParams.get('state') === 'tool-executing') return <ToolExecutingStatePage />;
  if (!realMode && searchParams.get('state') === 'awaiting-clarification') return <AwaitingClarificationStatePage />;
  if (searchParams.get('state') === 'write-confirmation') return <AgentStatePage state="write-confirmation" />;
  if (searchParams.get('state') === 'budget-limit') return <AgentStatePage state="budget-limit" />;
  if (searchParams.get('state') === 'tool-failed-retryable') return <AgentStatePage state="tool-failed-retryable" />;
  if (searchParams.get('state') === 'safety-degraded') return <AgentStatePage state="safety-degraded" />;
  if (searchParams.get('state') === 'user-cancelled') return <AgentStatePage state="user-cancelled" />;
  if (searchParams.get('state') === 'sse-reconnecting') return <AgentStatePage state="sse-reconnecting" />;
  if (searchParams.get('transport') === 'chat-run' && realMode) return <ChatRunPage />;
  return realMode ? <RealChatPage /> : <MockChatPage />;
}

const emptyPrompts = [
  {
    title: '分析我今天的饮食',
    description: '计算卡路里及三大营养素比例',
    prompt: '分析我今天的饮食，计算卡路里及三大营养素比例',
    icon: ChartColumn,
  },
  {
    title: '制定本周餐食计划',
    description: '根据我的减脂目标个性化定制',
    prompt: '根据我的减脂目标制定本周餐食计划',
    icon: CalendarDays,
  },
  {
    title: '查询食物营养成分',
    description: '快速查询牛油果/奇亚籽等营养价值',
    prompt: '查询牛油果和奇亚籽的营养成分',
    icon: Search,
  },
] as const;

function EmptyChatPage() {
  const navigate = useNavigate();
  const [input, setInput] = useState('');

  const send = () => {
    const prompt = input.trim();
    if (!prompt) return;
    navigate(`/chat?prompt=${encodeURIComponent(prompt)}`);
  };

  return (
    <WorkspaceLayout activeModule="home">
      <div className={styles.emptyPage}>
        <section className={styles.emptyBody} aria-labelledby="empty-chat-title">
          <div className={styles.emptyIntro}>
            <div className={styles.emptyIcon} aria-hidden="true">
              <MessageCircle />
            </div>
            <h1 id="empty-chat-title">开始新的对话</h1>
            <p>
              你可以询问任何关于营养、饮食和健康的问题。FoodMate 饮食管家已接入 Fustat-v2
              营养大模型，将为你提供专业支持。
            </p>
          </div>
          <div className={styles.emptyPrompts} aria-label="推荐问题">
            {emptyPrompts.map((item) => {
              const Icon = item.icon;
              return (
                <Button
                  className={styles.emptyPrompt}
                  variant="ghost"
                  key={item.title}
                  type="button"
                  onClick={() => setInput(item.prompt)}
                >
                  <span className={styles.emptyPromptIcon} aria-hidden="true">
                    <Icon />
                  </span>
                  <span className={styles.emptyPromptCopy}>
                    <strong>{item.title}</strong>
                    <span>{item.description}</span>
                  </span>
                </Button>
              );
            })}
          </div>
        </section>
        <Composer
          value={input}
          placeholder="输入消息或添加自定义指令..."
          onChange={setInput}
          onSend={send}
          onStop={() => undefined}
        />
      </div>
    </WorkspaceLayout>
  );
}

function PlanningStatePage() {
  const planningAvatarSrc = FIXTURE_CHAT_ACCOUNT_AVATAR;
  const planningLoaderSrc = '/assets/figma/agent-chat/planning-loader.svg';
  const planningSidebar = { ...historyFixture('history-page-2').sidebar, currentPage: 1 };
  const planningRun: AgentRunView = {
    id: 'run_planning_fixture',
    status: 'planning',
    intent: 'analysis',
    toolsUsed: 0,
    toolsTotal: 6,
    agentsUsed: 0,
    agentsTotal: 1,
    toolCalls: [],
    citations: [],
  };

  return (
    <ChatSurface
      run={planningRun}
      messagesRef={useRef<HTMLDivElement>(null)}
      input=""
      running
      disabled
      onChange={() => undefined}
      onSend={() => undefined}
      onStop={() => undefined}
      placeholder="正在规划任务流程，请稍候..."
      showTrace={false}
      avatarSrc={planningAvatarSrc}
      sidebarAvatarSrc={planningAvatarSrc}
      topAvatarSrc={planningAvatarSrc}
      displayNameOverride="Anddy"
      profileIdOverride="1234567"
      showKnowledgeTopNav={false}
      designChat
      sidebarFixture={planningSidebar}
    >
      <article className={styles.planningUserMessage}>
        <div className={styles.planningUserLine}>
          <div className={styles.planningUserBubble}>帮我分析这周的蛋白质摄入情况</div>
          <span className={styles.planningUserAvatar} aria-hidden="true">
            <AvatarImage avatarUrl={planningAvatarSrc} defaultOnly gender="男" alt="" />
          </span>
        </div>
        <div className={styles.planningMessageMeta}>Anddy · 12:45 PM</div>
      </article>
      <article className={styles.planningAssistantMessage}>
        <AgentStatusMarker className={styles.planningAgentAvatar} />
        <div className={styles.planningAssistantBody}>
          <div className={styles.planningBubble}>
            <div className={styles.planningTitle}>
              <img className={styles.planningLoader} src={planningLoaderSrc} alt="" />
              <strong>Planning...</strong>
            </div>
            <div className={styles.planningSteps}>
              <span className={styles.planningStepDone}>✓ 理解用户意图</span>
              <strong className={styles.planningStepActive}>● 制定分析方案...</strong>
              <span className={styles.planningStepPending}>○ 获取这周饮食数据</span>
              <span className={styles.planningStepPending}>○ 汇总并生成评估图表</span>
            </div>
          </div>
        </div>
      </article>
    </ChatSurface>
  );
}

const executingToolSteps = [
  {
    label: '向量索引检索 - 12ms ✓',
    status: 'success' as const,
    iconSrc: '/assets/figma/agent-chat/tool-executing-check.svg',
  },
  {
    label: '数据库调用 - running...',
    status: 'running' as const,
    iconSrc: '/assets/figma/agent-chat/tool-executing-loader-running.svg',
  },
  {
    label: '营养计算 - pending',
    status: 'pending' as const,
    iconSrc: '/assets/figma/agent-chat/tool-executing-minus.svg',
  },
];

function ToolExecutingStatePage() {
  const executingAvatarSrc = FIXTURE_CHAT_ACCOUNT_AVATAR;
  const executingSidebar = { ...historyFixture('history-page-2').sidebar, currentPage: 1 };
  const executingRun: AgentRunView = {
    id: 'fst_trace_9821aa',
    status: 'executing_tools',
    intent: 'analysis',
    toolsUsed: 1,
    toolsTotal: 3,
    agentsUsed: 0,
    agentsTotal: 1,
    toolCalls: [
      {
        id: 'intent-parse',
        name: 'intent_parse',
        displayName: '意图解析',
        status: 'success',
        latencyMs: 8,
        summary: '已完成意图解析',
      },
      {
        id: 'vector-search',
        name: 'vector_search',
        displayName: '向量检索: 蛋白质推荐',
        status: 'success',
        latencyMs: 12,
        summary: '正在读取蛋白质推荐索引',
      },
      {
        id: 'meal-log-query',
        name: 'meal_log_query',
        displayName: '数据库调用: 饮食日志表',
        status: 'running',
        summary: '正在读取饮食日志表',
      },
      {
        id: 'result-compose',
        name: 'result_compose',
        displayName: '结果合成',
        status: 'pending',
        summary: '等待前置工具完成',
      },
    ],
    citations: [],
  };

  return (
    <ChatSurface
      run={executingRun}
      messagesRef={useRef<HTMLDivElement>(null)}
      input=""
      running
      disabled
      onChange={() => undefined}
      onSend={() => undefined}
      onStop={() => undefined}
      placeholder="正在运行数据计算工具..."
      avatarSrc={executingAvatarSrc}
      sidebarAvatarSrc={executingAvatarSrc}
      topAvatarSrc={executingAvatarSrc}
      displayNameOverride="Anddy"
      profileIdOverride="1234567"
      showKnowledgeTopNav={false}
      designChat
      sidebarFixture={executingSidebar}
    >
      <article className={styles.executingUserMessage}>
        <div className={styles.executingUserLine}>
          <div className={styles.executingUserBubble}>帮我分析这周的蛋白质摄入情况</div>
          <span className={styles.executingUserAvatar} aria-hidden="true">
            <AvatarImage avatarUrl={executingAvatarSrc} defaultOnly gender="男" alt="" />
          </span>
        </div>
        <div className={styles.executingMessageMeta}>Anddy · 12:45 PM</div>
      </article>
      <article className={styles.executingAssistantMessage}>
        <AgentStatusMarker className={styles.executingAgentAvatar} />
        <div className={styles.executingAssistantBody}>
          <div className={styles.executingBubble}>
            <div className={styles.executingTitle}>
              <img className={styles.executingLoader} src="/assets/figma/agent-chat/tool-executing-loader.svg" alt="" />
              <strong>Executing Tools...</strong>
            </div>
            <div className={styles.executingToolSteps}>
              {executingToolSteps.map((step) => {
                return (
                  <div
                    className={`${styles.executingToolStep} ${styles[`executingToolStep${step.status}`]}`}
                    key={step.label}
                  >
                    <img className={styles.executingToolIcon} src={step.iconSrc} alt="" />
                    <span>{step.label}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </article>
    </ChatSurface>
  );
}

function AwaitingClarificationStatePage() {
  const awaitingMessageAvatarSrc = FIXTURE_CHAT_ACCOUNT_AVATAR;
  const awaitingSidebar = { ...historyFixture('history-page-2').sidebar, currentPage: 1 };
  const awaitingRun: AgentRunView = {
    id: 'run_awaiting_clarification_fixture',
    status: 'planning',
    intent: 'record',
    toolsUsed: 0,
    toolsTotal: 2,
    agentsUsed: 0,
    agentsTotal: 1,
    toolCalls: [],
    citations: [],
  };

  return (
    <ChatSurface
      run={awaitingRun}
      messagesRef={useRef<HTMLDivElement>(null)}
      input=""
      running={false}
      avatarSrc={awaitingMessageAvatarSrc}
      designChat
      displayNameOverride="Anddy"
      profileIdOverride="1234567"
      sidebarAvatarSrc={FIXTURE_CHAT_ACCOUNT_AVATAR}
      topAvatarSrc={FIXTURE_CHAT_ACCOUNT_AVATAR}
      onChange={() => undefined}
      onSend={() => undefined}
      onStop={() => undefined}
      placeholder="输入你的详细食物或份量，例如：150克野生三文鱼..."
      showKnowledgeTopNav={false}
      showTrace={false}
      sidebarFixture={awaitingSidebar}
    >
      <article className={styles.awaitingUserMessage}>
        <div className={styles.awaitingUserLine}>
          <div className={styles.awaitingUserBubble}>记录一下我的午餐</div>
          <span className={styles.awaitingUserAvatar} aria-hidden="true">
            <AvatarImage avatarUrl={awaitingMessageAvatarSrc} defaultOnly gender="男" alt="" />
          </span>
        </div>
        <div className={styles.awaitingMessageMeta}>Anddy · 12:45 PM</div>
      </article>
      <article className={styles.awaitingAssistantMessage}>
        <AgentStatusMarker className={styles.awaitingAgentAvatar} />
        <div className={styles.awaitingAssistantBody}>
          <ClarificationCard
            options={['补充食物和份量', '上传照片识别']}
            presentation="figma-compact"
            question="你的午餐具体包含哪些食物？"
            title="需要确认以下信息："
          />
        </div>
      </article>
    </ChatSurface>
  );
}

type AgentFixtureState =
  | 'write-confirmation'
  | 'budget-limit'
  | 'tool-failed-retryable'
  | 'safety-degraded'
  | 'user-cancelled'
  | 'sse-reconnecting';

type ChatAuxState =
  | 'completed-with-citations'
  | 'redesign-default'
  | 'nav-loading'
  | 'nav-hover-preview'
  | 'pagination'
  | 'history-page-2'
  | 'history-page-3'
  | 'search-results'
  | 'session-actions'
  | 'renamed'
  | 'archived'
  | 'trash'
  | 'running-stop';

function getChatAuxState(value: string | null): ChatAuxState | undefined {
  const states: ChatAuxState[] = [
    'completed-with-citations',
    'redesign-default',
    'nav-loading',
    'nav-hover-preview',
    'pagination',
    'history-page-2',
    'history-page-3',
    'search-results',
    'session-actions',
    'renamed',
    'archived',
    'trash',
    'running-stop',
  ];
  return value && states.includes(value as ChatAuxState) ? (value as ChatAuxState) : undefined;
}

type FixtureAction =
  | 'idle'
  | 'pending'
  | 'confirmed'
  | 'cancelled'
  | 'continued'
  | 'ended'
  | 'retried'
  | 'skipped'
  | 'restarted'
  | 'error';

function fixtureRun(state: AgentFixtureState): AgentRunView {
  const status: AgentRunView['status'] =
    state === 'tool-failed-retryable'
      ? 'failed'
      : state === 'safety-degraded'
        ? 'completed'
        : state === 'user-cancelled'
          ? 'cancelled'
          : state === 'sse-reconnecting'
            ? 'executing_tools'
            : 'composing';
  return {
    id: `fixture_${state}`,
    status,
    failedStep: state === 'tool-failed-retryable' ? 'executing_tools' : undefined,
    intent: state === 'write-confirmation' ? 'record' : state === 'budget-limit' ? 'analysis' : 'planning',
    toolsUsed: state === 'sse-reconnecting' ? 2 : state === 'tool-failed-retryable' ? 2 : 6,
    toolsTotal: 6,
    agentsUsed: 1,
    agentsTotal: 1,
    toolCalls: [],
    citations: [],
  };
}

type HistoryFixture = {
  prompt: string;
  response: string;
  source?: string;
  run: AgentRunView;
  sidebar: {
    sessions: SessionSummary[];
    currentPage: number;
    searchValue?: string;
  };
};

function historyFixture(
  state: Extract<ChatAuxState, 'history-page-2' | 'history-page-3' | 'search-results'>,
): HistoryFixture {
  const isSearch = state === 'search-results';
  const isPageThree = state === 'history-page-3';
  const baseSessions: SessionSummary[] = [
    { id: 'weekly-adjustment', title: '每周饮食微调', subtitle: '12:45', active: true, status: 'completed' },
    { id: 'pre-workout-snack', title: '运动前零食建议', subtitle: '12:45', status: 'completed' },
    { id: 'allergen-rules', title: '过敏原排除规则', subtitle: '12:45', status: 'completed' },
    { id: 'protein-supplement', title: '蛋白质补充方案', subtitle: '12:45', status: 'completed' },
    { id: 'bedtime-snack', title: '睡前加餐建议', subtitle: '12:45', status: 'completed' },
    { id: 'breakfast-carbs', title: '早餐碳水搭配', subtitle: '12:45', status: 'completed' },
    { id: 'dinner-protein', title: '晚餐蛋白质补充', subtitle: '12:45', status: 'completed' },
    { id: 'low-carb-diet', title: '低碳水饮食建议', subtitle: '12:45', status: 'completed' },
  ];
  const searchSessions: SessionSummary[] = [
    { id: 'protein-supplement', title: '蛋白质补充方案', subtitle: '12:45', active: true, status: 'completed' },
    { id: 'high-protein-breakfast', title: '高蛋白早餐建议', subtitle: '12:45', status: 'completed' },
    { id: 'dinner-protein', title: '晚餐蛋白质补充', subtitle: '12:45', status: 'completed' },
    { id: 'protein-supplement-history', title: '蛋白质补充方案', subtitle: '12:45', status: 'completed' },
    { id: 'bedtime-snack', title: '睡前加餐建议', subtitle: '12:45', status: 'completed' },
    { id: 'breakfast-carbs', title: '早餐碳水搭配', subtitle: '12:45', status: 'completed' },
    { id: 'dinner-protein-history', title: '晚餐蛋白质补充', subtitle: '12:45', status: 'completed' },
    { id: 'low-carb-diet', title: '低碳水饮食建议', subtitle: '12:45', status: 'completed' },
  ];
  return {
    prompt: '我午餐吃了一些野生三文鱼和藜麦，但我不确定具体的蛋白质含量。',
    response:
      'I have analyzed the typical values for wild salmon (150g) and cooked quinoa (100g). Together, they provide approximately 38g of high-quality protein.',
    source: 'Source: USDA FoodData Central Ref #451992',
    run: {
      id: 'fst_trace_88192a',
      status: 'executing_tools',
      intent: 'analysis',
      toolsUsed: 4,
      toolsTotal: 4,
      agentsUsed: 1,
      agentsTotal: 1,
      citations: [],
      toolCalls: [
        {
          id: 'query-expansion',
          name: 'query_expansion',
          displayName: '查询扩展',
          status: 'success',
          latencyMs: 12,
          summary: '已完成查询扩展',
        },
        {
          id: 'vector-search',
          name: 'vector_search',
          displayName: '向量索引检索',
          status: 'success',
          latencyMs: 184,
          summary: '已命中知识库向量索引',
        },
        {
          id: 'usda-lookup',
          name: 'usda_lookup',
          displayName: 'USDA 数据库调用',
          status: 'success',
          latencyMs: 92,
          summary: '已返回标准营养值',
        },
        {
          id: 'response-compose',
          name: 'response_compose',
          displayName: '响应合成',
          status: 'success',
          latencyMs: 45,
          summary: '已生成可追溯回答',
        },
      ],
    },
    sidebar: {
      sessions: isSearch ? searchSessions : baseSessions,
      currentPage: isSearch ? 1 : isPageThree ? 3 : 2,
      searchValue: isSearch ? '高蛋白' : undefined,
    },
  };
}

function navigationFixture(): HistoryFixture {
  const fixture = historyFixture('history-page-2');
  return {
    ...fixture,
    sidebar: {
      ...fixture.sidebar,
      currentPage: 1,
    },
    prompt: '我午餐吃了一些野生三文鱼和藜麦，但我不确定具体的蛋白质含量。',
    response:
      '我已为您分析了野生三文鱼（150克）和熟藜麦（100克）的标准营养价值。它们一共可提供大约 38 克的优质蛋白质。',
    source: '来源: USDA FoodData Central Ref #451992',
    run: {
      ...fixture.run,
      toolCalls: [
        {
          id: 'query-expansion',
          name: 'query_expansion',
          displayName: '查询扩展 (Query Expansion)',
          status: 'success',
          latencyMs: 12,
          summary: '已完成查询扩展',
        },
        {
          id: 'vector-search',
          name: 'vector_search',
          displayName: '向量索引检索 (RAG Search)',
          status: 'success',
          latencyMs: 184,
          summary: '已命中知识库向量索引',
        },
        {
          id: 'usda-lookup',
          name: 'usda_lookup',
          displayName: 'USDA 数据库调用 (API Call)',
          status: 'success',
          latencyMs: 92,
          summary: '已返回标准营养值',
        },
        {
          id: 'response-compose',
          name: 'response_compose',
          displayName: '响应合成 (Response Generation)',
          status: 'success',
          latencyMs: 45,
          summary: '已生成可追溯回答',
        },
      ],
    },
  };
}

function redesignDefaultFixture(): HistoryFixture {
  const fixture = historyFixture('history-page-2');
  return {
    ...fixture,
    sidebar: {
      ...fixture.sidebar,
      currentPage: 1,
    },
  };
}

function completedCitationsFixture(): HistoryFixture {
  const fixture = historyFixture('history-page-2');
  return {
    ...fixture,
    prompt: '分析我这周的蛋白质摄入',
    response:
      '我已对你本周的饮食记录进行了完整分析。整体来看，你的蛋白质摄入表现健康，有明显的规律性，但在周末略有下滑。',
    source: undefined,
    run: {
      ...fixture.run,
      id: 'fixture_completed_with_citations',
      status: 'completed',
      intent: 'analysis',
      toolsUsed: 4,
      toolsTotal: 4,
      citations: [
        {
          id: '4451002',
          title: 'USDA FoodData Central - Ref #4451002',
          snippet: '标准食物营养数据',
          source: 'USDA FoodData Central',
        },
        {
          id: 'meal-log-2024-03-08',
          title: '用户饮食记录 2024-03-08~03-14',
          snippet: '本周饮食记录',
          source: 'FoodMate 饮食记录',
        },
      ],
    },
    sidebar: {
      ...fixture.sidebar,
      currentPage: 1,
    },
  };
}

function ProteinAnalysisCard() {
  return (
    <>
      <section className={styles.completedAnalysisCard} aria-label="本周蛋白质摄入分析">
        <h2>本周蛋白质摄入分析</h2>
        <div className={styles.completedMetricGrid}>
          <div>
            <span>日均摄入</span>
            <strong>85g</strong>
          </div>
          <div>
            <span>目标达成率</span>
            <strong>78%</strong>
          </div>
          <div>
            <span>最低日</span>
            <strong>周三 62g</strong>
          </div>
        </div>
      </section>
      <div className={styles.completedCitations} aria-label="数据源引用">
        <strong>数据源引用：</strong>
        <div className={styles.completedCitationList}>
          <span>[1] USDA FoodData Central - Ref #4451002</span>
          <span>[2] 用户饮食记录 2024-03-08~03-14</span>
        </div>
      </div>
    </>
  );
}

type SessionOverlayState = Extract<ChatAuxState, 'session-actions' | 'renamed' | 'archived' | 'trash'>;

function SessionStateOverlay({
  state,
  onAction,
  onClose,
}: {
  state: SessionOverlayState;
  onAction: (message: string) => void;
  onClose: () => void;
}) {
  if (state === 'session-actions') {
    return (
      <div className={styles.sessionActionsBackdrop}>
        <Card className={styles.sessionActionsOverlay} role="dialog" aria-label="会话管理">
          <span className={styles.sessionActionsOverlayStatus}>操作</span>
          <Button
            size="icon"
            variant="ghost"
            className={styles.sessionActionsOverlayClose}
            aria-label="关闭会话管理"
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </Button>
          <h2>会话管理</h2>
          <p>选择一个会话后可重命名、归档，或移入回收站</p>
          <section className={styles.sessionActionsSelected} aria-label="当前会话">
            <span className={styles.sessionActionsRunningDot} aria-hidden="true" />
            <div>
              <strong>每周饮食微调</strong>
              <span>进行中 · 今天 12:45 更新</span>
            </div>
            <span className={styles.sessionActionsRunningStatus}>RUNNING</span>
          </section>
          <div className={styles.sessionActionsOverlayActions}>
            <Button size="sm" variant="ghost" onClick={() => onAction('已打开会话重命名入口。')}>
              重命名会话
            </Button>
            <Button size="sm" variant="ghost" onClick={() => onAction('会话已归档，可从归档列表恢复。')}>
              归档会话
            </Button>
            <Button size="sm" variant="ghost" onClick={() => onAction('会话已移入回收站，保留期内可恢复。')}>
              移入回收站
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  if (state === 'renamed') {
    return (
      <div className={styles.sessionRenamedBackdrop}>
        <Card className={styles.sessionRenamedCard} role="dialog" aria-modal="true" aria-label="会话已重命名">
          <span className={styles.sessionRenamedAccent} aria-hidden="true" />
          <span className={styles.sessionRenamedStatus}>SAVED</span>
          <Button
            size="icon"
            variant="ghost"
            className={styles.sessionRenamedClose}
            aria-label="关闭重命名结果"
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </Button>
          <h2>会话已重命名</h2>
          <p>“每周饮食微调”已更新为“本周饮食分析”。</p>
          <span className={styles.sessionRenamedSync}>列表已同步，可继续查看此会话</span>
          <Button
            size="sm"
            variant="ghost"
            className={styles.sessionRenamedBack}
            onClick={() => onAction('已返回会话列表。')}
          >
            返回会话列表
          </Button>
        </Card>
      </div>
    );
  }

  if (state === 'archived') {
    return (
      <div className={styles.sessionArchivedBackdrop}>
        <Card className={styles.sessionArchivedCard} role="dialog" aria-modal="true" aria-label="已归档会话">
          <span className={styles.sessionArchivedAccent} aria-hidden="true" />
          <span className={styles.sessionArchivedStatus}>ARCHIVED</span>
          <Button
            size="icon"
            variant="ghost"
            className={styles.sessionArchivedClose}
            aria-label="关闭归档结果"
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </Button>
          <h2>已归档会话</h2>
          <p>本页显示已归档的会话，可恢复到 Agent 对话列表。</p>
          <div className={styles.sessionArchivedItem}>每周饮食微调 · 归档于今天 12:45</div>
          <span className={styles.sessionArchivedSync}>保留会话内容，恢复后回到 Agent 对话列表</span>
          <Button
            size="sm"
            variant="ghost"
            className={styles.sessionArchivedRestore}
            onClick={() => onAction('已恢复会话，可从 Agent 对话列表继续查看。')}
          >
            恢复会话
          </Button>
        </Card>
      </div>
    );
  }

  if (state === 'trash') {
    return (
      <div className={styles.sessionTrashBackdrop}>
        <Card className={styles.sessionTrashCard} role="dialog" aria-modal="true" aria-label="会话回收站">
          <span className={styles.sessionTrashAccent} aria-hidden="true" />
          <span className={styles.sessionTrashStatus}>RECOVERABLE</span>
          <Button
            size="icon"
            variant="ghost"
            className={styles.sessionTrashClose}
            aria-label="关闭回收站结果"
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </Button>
          <h2>会话回收站</h2>
          <p>删除的会话将在保留期内可恢复，不提供永久删除入口。</p>
          <div className={styles.sessionTrashItem}>运动前零食建议 · 移入回收站于今天 12:45</div>
          <span className={styles.sessionTrashSync}>回收站仅支持恢复，不提供永久删除</span>
          <Button
            size="sm"
            variant="ghost"
            className={styles.sessionTrashRestore}
            onClick={() => onAction('已恢复回收站会话，可从 Agent 对话列表继续查看。')}
          >
            恢复会话
          </Button>
        </Card>
      </div>
    );
  }
}

function ChatAuxStatePage({ state }: { state: ChatAuxState }) {
  const messagesRef = useRef<HTMLDivElement>(null);
  const [input, setInput] = useState('');
  const [notice, setNotice] = useState('');
  const [sessionOverlayVisible, setSessionOverlayVisible] = useState(true);
  const isRunning = state === 'running-stop';
  const isCompletedCitations = state === 'completed-with-citations';
  const isHistoryState = state === 'history-page-2' || state === 'history-page-3' || state === 'search-results';
  const isSessionOverlayState =
    state === 'session-actions' || state === 'renamed' || state === 'archived' || state === 'trash';
  const isRedesignDefault = state === 'redesign-default';
  const isNavigationState = state === 'nav-loading' || state === 'nav-hover-preview' || state === 'pagination';
  const history = isCompletedCitations
    ? completedCitationsFixture()
    : isHistoryState
      ? historyFixture(state)
      : isSessionOverlayState
        ? historyFixture('search-results')
        : isRedesignDefault
          ? redesignDefaultFixture()
          : isNavigationState
            ? navigationFixture()
            : isRunning
              ? historyFixture('history-page-2')
              : undefined;
  const run = isRunning
    ? { ...(history?.run ?? fixtureRun('sse-reconnecting')), status: 'executing_tools' as const }
    : (history?.run ?? fixtureRun('tool-failed-retryable'));
  const labels: Record<ChatAuxState, string> = {
    'completed-with-citations': '分析已完成，以下内容包含可追溯引用。',
    'redesign-default': '我分析了野生三文鱼和熟藜麦的标准营养值。',
    'nav-loading': '正在载入会话与运行轨迹…',
    'nav-hover-preview': '会话预览：本周蛋白质补充方案',
    pagination: '当前显示会话列表第 1 / 3 页',
    'history-page-2': '会话历史第 2 页',
    'history-page-3': '会话历史第 3 页',
    'search-results': '会话搜索结果：蛋白质补充方案',
    'session-actions': '会话管理：可重命名、归档或移入回收站',
    renamed: '会话名称已更新：每周饮食微调',
    archived: '会话已归档，可从归档列表恢复',
    trash: '会话已移入回收站，30 天内可恢复',
    'running-stop': '正在执行早餐营养分析，可随时停止当前 Run',
  };
  return (
    <ChatSurface
      run={run}
      messagesRef={messagesRef}
      input={input}
      running={isRunning}
      disabled={false}
      onChange={setInput}
      onSend={() => setNotice('已保留输入内容，等待当前会话继续处理。')}
      onStop={() => setNotice('已请求停止当前 Run；已接收文本会保留。')}
      placeholder={isRunning ? '正在运行... 点击停止以中断此运行' : '追问或添加自定义指令...'}
      showTrace={!isCompletedCitations}
      designChat
      pageVariant={isRunning ? 'running-stop' : isCompletedCitations ? 'completed-citations' : undefined}
      displayNameOverride="Anddy"
      profileIdOverride="1234567"
      showKnowledgeTopNav={false}
      sidebarAvatarSrc={isCompletedCitations ? FIXTURE_CHAT_ACCOUNT_AVATAR : undefined}
      topAvatarSrc={isCompletedCitations ? FIXTURE_CHAT_ACCOUNT_AVATAR : undefined}
      pageOverlay={
        isSessionOverlayState && sessionOverlayVisible ? (
          <SessionStateOverlay
            state={state}
            onAction={setNotice}
            onClose={() => {
              setSessionOverlayVisible(false);
              setNotice(
                state === 'session-actions'
                  ? '已关闭会话管理面板。'
                  : state === 'renamed'
                    ? '已关闭重命名结果。'
                    : state === 'archived'
                      ? '已关闭归档结果。'
                      : '已关闭回收站结果。',
              );
            }}
          />
        ) : undefined
      }
      sidebarFixture={history?.sidebar}
    >
      {history ? (
        <>
          <MessageBubble
            message={{
              id: `${state}-user`,
              role: 'user',
              content: history.prompt,
              time: '12:45',
              wide: isNavigationState,
            }}
            userAvatarSrc={isCompletedCitations ? FIXTURE_CHAT_ACCOUNT_AVATAR : undefined}
            userAvatarGender="男"
          />
          <MessageBubble
            message={{
              id: `${state}-assistant`,
              role: 'assistant',
              content: history.response,
              source: history.source,
              time: '12:46',
              wide: isNavigationState,
            }}
          >
            {isCompletedCitations ? (
              <ProteinAnalysisCard />
            ) : (
              <InlineConfirmationCard
                onConfirm={() => setNotice('fixture 已记录写入确认；未调用任何后端写入接口。')}
                onCancel={() => setNotice('已保留本次分析，仅作为对话参考。')}
              />
            )}
          </MessageBubble>
          {isRedesignDefault ? <MessageActionsPanel /> : null}
        </>
      ) : (
        <>
          <article className={styles.fixtureUserMessage}>
            <div className={styles.fixtureUserBubble}>帮我分析这周的饮食与蛋白质摄入情况</div>
            <span className={styles.fixtureMessageMeta}>Anddy · 12:45 PM</span>
          </article>
          <article className={styles.fixtureAuxMessage}>
            <strong>{labels[state]}</strong>
            {state === 'completed-with-citations' ? (
              <span>来源：USDA FoodData Central Ref #451992 · PubMed Central</span>
            ) : null}
            {state === 'running-stop' ? (
              <Button variant="outline" onClick={() => setNotice('已请求停止当前 Run。')}>
                <CircleSlash aria-hidden="true" />
                停止
              </Button>
            ) : null}
          </article>
        </>
      )}
      {notice ? (
        <p className={styles.fixtureActionMessage} role="status">
          {notice}
        </p>
      ) : null}
    </ChatSurface>
  );
}

function AgentStatePage({ state }: { state: AgentFixtureState }) {
  const [searchParams] = useSearchParams();
  const messagesRef = useRef<HTMLDivElement>(null);
  const [action, setAction] = useState<FixtureAction>('idle');
  const [actionMessage, setActionMessage] = useState('');
  const [input, setInput] = useState('');
  const realMode = import.meta.env.VITE_AGENT_MODE === 'real';
  if (realMode) return <RealAgentStatePage state={state} />;
  const approvalId = searchParams.get('approval_id');
  const runId = searchParams.get('run_id');
  const run = fixtureRun(state);
  const isWriteConfirmation = state === 'write-confirmation';
  const fixtureSidebar = isWriteConfirmation
    ? { ...historyFixture('history-page-2').sidebar, currentPage: 1 }
    : undefined;
  // 所有 Agent 状态画板使用与工作区相同的登记头像来源，避免状态页和消息页头像漂移。
  const fixtureSidebarAvatarSrc = FIXTURE_CHAT_ACCOUNT_AVATAR;
  const fixtureTopAvatarSrc = FIXTURE_CHAT_ACCOUNT_AVATAR;
  // 安全降级只描述本次分析能力受限，不代表用户身份变化；消息继续使用工作区账号的男性默认头像。
  const fixtureMessageAvatarSrc = FIXTURE_CHAT_ACCOUNT_AVATAR;
  const fixtureMessageGender = FIXTURE_CHAT_AVATAR_GENDERS.agentStateMessage;

  const report = (nextAction: FixtureAction, message: string) => {
    setAction(nextAction);
    setActionMessage(message);
  };

  const confirmWrite = async () => {
    if (!realMode) {
      report('confirmed', 'fixture 已记录确认动作，真实写入仍需后端审批事件。');
      return;
    }
    if (!approvalId) {
      report('error', '真实模式缺少 approval_id，未执行任何写入。');
      return;
    }
    report('pending', '确认请求已提交，等待后端执行事件。');
    try {
      const parameters = { source: 'figma-write-confirmation' };
      const confirmed = await confirmAgentWrite(approvalId, parameters);
      if (confirmed.status && confirmed.status.toLowerCase() !== 'confirmed') {
        report('error', `后端未进入确认状态，当前状态：${confirmed.status}。`);
        return;
      }
      const executed = await executeAgentWrite(approvalId, parameters);
      report('confirmed', `后端已返回写入状态：${executed.status || '未知'}，页面不推断额外结果。`);
    } catch (reason) {
      report('error', reason instanceof Error ? reason.message : '写入确认失败，请稍后重试。');
    }
  };

  const cancelWrite = async () => {
    if (!realMode) {
      report('cancelled', '已取消写入，本次对话不会修改饮食记录。');
      return;
    }
    if (!approvalId) {
      report('error', '真实模式缺少 approval_id，未执行取消请求。');
      return;
    }
    report('pending', '取消请求已提交，等待后端确认。');
    try {
      const rejected = await rejectAgentWrite(approvalId, { source: 'figma-write-confirmation' });
      report('cancelled', `后端已返回审批状态：${rejected.status || '未知'}。`);
    } catch (reason) {
      report('error', reason instanceof Error ? reason.message : '取消写入失败，请稍后重试。');
    }
  };

  const extendBudget = async () => {
    if (!realMode) {
      report('continued', 'fixture 已记录追加预算动作，当前 Run 不会被伪造为新会话。');
      return;
    }
    if (!runId) {
      report('error', '真实模式缺少 run_id，未执行预算追加。');
      return;
    }
    report('pending', '预算追加请求已提交，等待当前 Run 的后续事件。');
    try {
      const result = await extendAgentRunBudget(runId, 20000, '0.15');
      report(
        'continued',
        `当前 Run 已返回预算追加状态：${result.status || '未知'}（dispatch attempt ${result.attempt}），未创建新会话。`,
      );
    } catch (reason) {
      report('error', reason instanceof Error ? reason.message : '预算追加失败，请稍后重试。');
    }
  };

  const endBudgetSession = async () => {
    if (!realMode) {
      report('ended', '已结束当前会话。');
      return;
    }
    if (!runId) {
      report('error', '真实模式缺少 run_id，未执行取消请求。');
      return;
    }
    report('pending', '结束请求已提交，等待当前 Run 的取消事件。');
    try {
      const result = await cancelAgentRun(runId);
      report('ended', `当前 Run 已返回结束状态：${result.status || '未知'}，终态以服务端事件为准。`);
    } catch (reason) {
      report('error', reason instanceof Error ? reason.message : '结束会话失败，请稍后重试。');
    }
  };

  const retryFailedTool = async () => {
    if (!realMode) {
      report('retried', 'fixture 已记录重试动作，等待新的工具事件。');
      return;
    }
    if (!runId) {
      report('error', '真实模式缺少 run_id，未执行重试。');
      return;
    }
    report('pending', '重试请求已提交，等待后端运行事件，当前页面不会伪造成功。');
    try {
      const result = await retryAgentRun(runId);
      report('retried', `后端已返回重试状态：${result.status || '未知'}（dispatch attempt ${result.attempt}）。`);
    } catch (reason) {
      report('error', reason instanceof Error ? reason.message : '重试请求失败，请稍后重试。');
    }
  };

  const skipFailedTool = () => {
    if (!realMode) {
      report('skipped', '已跳过此步骤，后续结果会明确标注数据范围受限。');
      return;
    }
    // 当前后端没有跳过单个工具步骤的公开接口，不能把本地提示当作运行状态。
    report('error', '当前后端未提供跳过此步骤接口，未发送请求；请等待真实 Run 返回后续事件。');
  };

  const content = (() => {
    if (state === 'write-confirmation') {
      return (
        <div className={styles.fixtureWriteRow}>
          <AgentStatusMarker className={styles.fixtureAgentAvatar} />
          <div className={styles.fixtureCardWrap}>
            <Card className={`${styles.fixtureCard} ${styles.fixtureWriteCard}`}>
              <div className={styles.fixtureCardHeader}>
                <h2>确认写入以下记录</h2>
                <span>目标对象: 饮食记录</span>
              </div>
              <dl className={styles.fixtureDetails}>
                <div>
                  <dt>分类</dt>
                  <dd>2024年3月14日 午餐</dd>
                </div>
                <div>
                  <dt>食物</dt>
                  <dd>三文鱼寿司 x6</dd>
                </div>
                <div>
                  <dt>热量</dt>
                  <dd>约 620 千卡</dd>
                </div>
                <div>
                  <dt>蛋白质</dt>
                  <dd>38g</dd>
                </div>
              </dl>
              <div className={styles.fixtureMeta}>
                <span>来源: USDA FoodData Central</span>
                <span>假设: 按标准份量估算</span>
              </div>
              <div className={styles.fixtureActions}>
                <Button disabled={action === 'pending'} onClick={() => void confirmWrite()}>
                  确认写入
                </Button>
                <Button disabled={action === 'pending'} variant="ghost" onClick={() => void cancelWrite()}>
                  取消
                </Button>
              </div>
            </Card>
          </div>
        </div>
      );
    }
    if (state === 'budget-limit') {
      return (
        <>
          <div className={styles.fixtureAssistantRow}>
            <AgentStatusMarker className={styles.fixtureAgentAvatar} />
            <p className={styles.fixtureBudgetIntro}>
              我已在后台调用历史数据解析服务。此分析需要读取超长数据块，将会消耗较多计算令牌。
            </p>
          </div>
          <div className={`${styles.fixtureAssistantRow} ${styles.fixtureBudgetRowWrap}`}>
            <AgentStatusMarker className={styles.fixtureAgentAvatar} />
            <Card className={`${styles.fixtureCard} ${styles.fixtureBudgetCard}`}>
              <div className={styles.fixtureBudgetTitle}>
                <AlertTriangle aria-hidden="true" />
                <h2 className={styles.fixtureBudgetTitleText}>已达到预算上限</h2>
              </div>
              <p className={styles.fixtureBudgetDescription}>
                本次会话已使用 50,000 tokens（单次会话预算上限）。为了保证资源分配合理及避免异常资费产生，你可以：
              </p>
              <div className={styles.fixtureChoiceList}>
                <span>
                  <strong>● 追加预算继续当前会话</strong>
                </span>
                <span>● 开始新会话 (之前的分析进度将会重置)</span>
              </div>
              <div className={styles.fixtureBudgetMeter}>
                <div className={styles.fixtureBudgetRow}>
                  <span>Token 用量 (100%)</span>
                  <strong>预计费用: $0.15</strong>
                </div>
                <div
                  aria-label="预算用量 100%"
                  aria-valuemax={100}
                  aria-valuemin={0}
                  aria-valuenow={100}
                  className={styles.fixtureBudgetProgress}
                  role="progressbar"
                >
                  <span />
                </div>
              </div>
              <div className={styles.fixtureActions}>
                <Button
                  className={styles.fixtureBudgetPrimaryButton}
                  disabled={action === 'pending'}
                  onClick={() => void extendBudget()}
                >
                  追加 20,000 tokens
                </Button>
                <Button
                  className={styles.fixtureBudgetSecondaryButton}
                  disabled={action === 'pending'}
                  variant="ghost"
                  onClick={() => void endBudgetSession()}
                >
                  结束会话
                </Button>
              </div>
            </Card>
          </div>
        </>
      );
    }
    if (state === 'tool-failed-retryable') {
      return (
        <div className={styles.fixtureAssistantRow}>
          <AgentStatusMarker className={styles.fixtureAgentAvatar} />
          <Card className={`${styles.fixtureCard} ${styles.fixtureFailureCard}`}>
            <div className={styles.fixtureStatusTitle}>
              <AlertTriangle aria-hidden="true" />
              <h2>工具执行失败</h2>
            </div>
            <div className={styles.fixtureFailureDetails}>
              <strong className={styles.fixtureErrorTitle}>数据库查询超时 (错误码: TOOL_TIMEOUT_001)</strong>
              <p className={styles.fixtureParagraph}>
                向量索引检索服务暂时不可用。FoodMate 代理在尝试读取外部知识库时失去连接。
              </p>
            </div>
            <div className={styles.fixtureActions}>
              <Button
                className={styles.fixtureRetryButton}
                disabled={action === 'pending'}
                onClick={() => void retryFailedTool()}
              >
                重试
              </Button>
              <Button
                className={styles.fixtureSkipButton}
                disabled={action === 'pending'}
                variant="outline"
                onClick={skipFailedTool}
              >
                跳过此步骤
              </Button>
            </div>
          </Card>
        </div>
      );
    }
    if (state === 'safety-degraded') {
      return (
        <div className={styles.fixtureSafetyBlock}>
          <div className={styles.fixtureSafetyTopRow}>
            <div className={styles.fixtureSafetyIdentity}>
              <AgentStatusMarker className={styles.fixtureAgentAvatar} />
              <span className={styles.fixtureSafetyLabel}>安全降级</span>
            </div>
            <div className={`${styles.fixtureSafetyBody} ${styles.fixtureSafetyBodyAligned}`}>
              <Alert variant="warning" className={styles.fixtureSafetyAlert}>
                <AlertTitle>⚠️ 安全降级提示</AlertTitle>
                <AlertDescription>
                  由于部分工具不可用，以下回答基于有限数据生成，可能不够完整。建议稍后重试以获取完整分析。
                </AlertDescription>
              </Alert>
              <div className={styles.fixtureSafetyResponse}>
                <p className={styles.fixtureSafetyIntro}>
                  由于无法连接到本地营养配方数据库，以下为您推荐基础低钠食谱：
                </p>
                <div className={styles.fixtureSafetyDetails}>
                  <p>1. **清蒸鳕鱼配西兰花**（预计钠含量：120mg）</p>
                  <p>2. **香草烤鸡胸肉配糙米饭**（预计钠含量：150mg）</p>
                  <p>注意：由于当前未结合您的个人高血压排除条件，请谨慎添加额外酱料。</p>
                </div>
              </div>
              <p className={styles.fixtureSafetyMeta}>Fustat-v2 Agent · 1:31 PM</p>
            </div>
          </div>
        </div>
      );
    }
    if (state === 'user-cancelled') {
      return (
        <div className={`${styles.fixtureCancelledWrap} ${styles.fixtureCancelledWrapAligned}`}>
          <div className={`${styles.fixtureCancelledAssistantRow} ${styles.fixtureCancelledAssistantRowAligned}`}>
            <AgentStatusMarker className={styles.fixtureAgentAvatar} />
            <div className={styles.fixtureCancelledAssistantBody}>
              <p className={styles.fixtureAssistantText}>
                正在为您生成减脂餐计划... 已检索到您历史减脂卡路里基准为 1600kcal...
              </p>
            </div>
          </div>
          <div className={`${styles.fixtureCancelledNotice} ${styles.fixtureCancelledNoticeAligned}`}>
            <img src="/assets/figma/agent-chat/cancel-slash.svg" alt="" />
            <span>用户已取消此次运行 · 2:16 PM</span>
          </div>
          <p className={styles.fixtureCenteredText}>你可以重新提问或开始新的对话</p>
        </div>
      );
    }
    return (
      <div className={styles.fixtureReconnectWrap}>
        <div className={styles.fixtureReconnectAssistantRow}>
          <AgentStatusMarker className={styles.fixtureAgentAvatar} />
          <div className={styles.fixtureReconnectAssistantBody}>
            <p className={styles.fixtureAssistantText}>
              正在查询水果数据库，提取符合低生糖指数（GI &lt; 55）的食材列表...
            </p>
          </div>
        </div>
        <div className={styles.fixtureReconnectBottom}>
          <div className={`${styles.fixtureReconnectNotice} ${styles.fixtureReconnectNoticeFigma}`}>
            <img src="/assets/figma/agent-chat/tool-executing-loader-running.svg" alt="" />
            <div>
              <strong>连接已中断，正在重新连接...</strong>
              <span>第 2 次重连尝试 (最多 5 次)</span>
            </div>
          </div>
          <p className={styles.fixtureCenteredText}>如果持续失败，请刷新页面</p>
        </div>
      </div>
    );
  })();

  return (
    <ChatSurface
      run={run}
      messagesRef={messagesRef}
      input={input}
      // 重连期间只锁定 Composer，不显示“停止运行”按钮，保持 Figma 的禁用发送态。
      running={false}
      disabled={state === 'budget-limit' || state === 'sse-reconnecting'}
      statusForStrip={state === 'user-cancelled' ? 'planning' : undefined}
      statusVisualState={state === 'user-cancelled' ? 'user-cancelled' : undefined}
      onChange={setInput}
      onSend={() => {
        if (state === 'safety-degraded' && input.trim())
          setActionMessage('已保留追问入口；真实模式下将由当前 Run 继续处理。');
        if (state === 'user-cancelled' && input.trim())
          report('restarted', '已准备重新开始；真实运行需要由后端创建新的 Run。');
      }}
      onStop={() => setActionMessage('取消状态会保留已接收文本，真实取消请求需要绑定具体 run_id。')}
      placeholder={
        state === 'write-confirmation'
          ? '请确认上述饮食数据是否正确...'
          : state === 'budget-limit'
            ? '追加预算以继续当前会话...'
            : state === 'sse-reconnecting'
              ? '等待重新连接...'
              : state === 'user-cancelled'
                ? '重新开始提问...'
                : '追问或添加自定义指令...'
      }
      showTrace={false}
      designChat
      displayNameOverride="Anddy"
      profileIdOverride="1234567"
      showKnowledgeTopNav={false}
      sidebarAvatarSrc={fixtureSidebarAvatarSrc}
      topAvatarSrc={fixtureTopAvatarSrc}
      sidebarFixture={fixtureSidebar}
    >
      <article className={styles.fixtureUserMessage}>
        <div className={styles.fixtureUserLine}>
          <div className={styles.fixtureUserBubble}>
            {state === 'write-confirmation'
              ? '把刚才吃的三文鱼寿司记录到午餐里吧'
              : state === 'budget-limit'
                ? '帮我导出2023整年每个月的膳食结构趋势报告'
                : state === 'tool-failed-retryable'
                  ? '查询我今天晚餐的热量'
                  : state === 'safety-degraded'
                    ? '推荐一份低钠晚餐食谱'
                    : state === 'user-cancelled'
                      ? '生成下周的减脂餐食规划'
                      : '推荐低GI的水果'}
          </div>
          {fixtureMessageAvatarSrc ? (
            <span className={styles.fixtureUserAvatar} aria-hidden="true">
              <AvatarImage
                avatarUrl={fixtureMessageAvatarSrc}
                data-avatar-role="fixture-message"
                defaultOnly
                gender={fixtureMessageGender}
                alt=""
              />
            </span>
          ) : null}
        </div>
        <span className={styles.fixtureMessageMeta}>
          Anddy ·{' '}
          {state === 'user-cancelled'
            ? '02:15 PM'
            : state === 'safety-degraded'
              ? '01:30 PM'
              : state === 'sse-reconnecting'
                ? '03:00 PM'
                : '12:45 PM'}
        </span>
      </article>
      {content}
      {actionMessage ? (
        <p className={styles.fixtureActionMessage} role="status">
          {actionMessage}
        </p>
      ) : null}
    </ChatSurface>
  );
}

type RealAgentActionState = 'idle' | 'loading' | 'success' | 'error';

type RealProposalDraft = {
  operation: string;
  resourceType: string;
  resourceId?: string | number;
  parameters: Record<string, unknown>;
};

type RealBudgetFacts = {
  usedTokens?: number;
  maxTokens?: number;
  usedCostCny?: string;
  maxCostCny?: string;
  ratio?: number;
  additionalTokens?: number;
  additionalCostCny?: string;
};

type ApprovalProposalView = Awaited<ReturnType<typeof loadApprovalProposal>>;

function parseJsonRecord(value: string | null): Record<string, unknown> | undefined {
  if (!value?.trim()) return undefined;
  try {
    const parsed: unknown = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : undefined;
  } catch {
    return undefined;
  }
}

function parseRealProposalDraft(searchParams: URLSearchParams): RealProposalDraft | undefined {
  const operation = searchParams.get('operation')?.trim();
  const resourceType = searchParams.get('resource_type')?.trim();
  const parameters = parseJsonRecord(searchParams.get('parameters'));
  if (!operation || !resourceType || !parameters) return undefined;
  const resourceId = searchParams.get('resource_id')?.trim();
  return {
    operation,
    resourceType,
    resourceId: resourceId && /^\d+$/.test(resourceId) ? Number(resourceId) : resourceId || undefined,
    parameters,
  };
}

function parseJsonStringArray(value: string | null): string[] | undefined {
  if (!value?.trim()) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) && parsed.every((item) => typeof item === 'string') ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function parseExplicitRecoveryRequest(searchParams: URLSearchParams): AgentRecoveryRequest | undefined {
  const checkpointVersion = queryNumber(searchParams.get('checkpoint_version'));
  const checkpointDigest = searchParams.get('checkpoint_digest')?.trim();
  const completedInvocationIds = parseJsonStringArray(searchParams.get('completed_invocation_ids'));
  if (checkpointVersion === undefined || !checkpointDigest || completedInvocationIds === undefined) return undefined;
  return { checkpointVersion, checkpointDigest, completedInvocationIds };
}

function recoveryRequestFromEvent(payload: AgentRunEvent): AgentRecoveryRequest | undefined {
  const checkpointVersion = payload.checkpoint_version;
  const checkpointDigest = payload.checkpoint_digest?.trim();
  const completedInvocationIds = payload.completed_invocation_ids ?? [];
  if (
    checkpointVersion === undefined ||
    !Number.isInteger(checkpointVersion) ||
    checkpointVersion < 1 ||
    !checkpointDigest ||
    !completedInvocationIds.every((item) => typeof item === 'string')
  )
    return undefined;
  return { checkpointVersion, checkpointDigest, completedInvocationIds };
}

function queryNumber(value: string | null): number | undefined {
  if (!value?.trim() || !/^\d+$/.test(value.trim())) return undefined;
  return Number(value);
}

function realEventDisplayStatus(eventType: string, payload: AgentRunEvent): AgentDisplayStatus {
  const normalizedEventType = resolveAgentEventType(eventType, payload as unknown as Record<string, unknown>);
  if (normalizedEventType === 'run.completed') return 'completed';
  if (normalizedEventType === 'run.failed') return 'failed';
  if (normalizedEventType === 'run.cancelled') return 'cancelled';
  if (normalizedEventType === 'run.superseded') return 'superseded';
  if (normalizedEventType === 'run.routed') return 'routing';
  if (normalizedEventType === 'run.planned') return 'planning';
  if (
    normalizedEventType === 'run.retrieval_started' ||
    normalizedEventType === 'run.retrieval_finished' ||
    normalizedEventType === 'run.context_assembled'
  )
    return 'retrieving';
  if (normalizedEventType === 'run.tool_started' || normalizedEventType === 'run.tool_finished')
    return 'executing_tools';
  if (normalizedEventType === 'run.eval_decided') return 'validating';
  if (normalizedEventType === 'run.model_usage' || normalizedEventType === 'run.answer_stream') return 'composing';
  if (normalizedEventType === 'run.clarification_requested' || normalizedEventType === 'run.checkpoint_saved')
    return 'waiting_user';
  return displayRunStatus(payload.status ?? payload.state ?? normalizedEventType.replace('run.', ''));
}

function realApprovalData(
  proposal: ApprovalProposalView,
  details?: NonNullable<AgentRunEvent['details']>,
  parameters?: Record<string, unknown>,
) {
  if (details) return approvalData(details, proposal.resource_type);
  const parameterKeys = parameters ? Object.keys(parameters) : [];
  return [
    { label: '操作', value: proposal.operation || '未返回' },
    { label: '资源类型', value: proposal.resource_type || '未返回' },
    { label: '资源 ID', value: proposal.resource_id == null ? '新资源' : String(proposal.resource_id) },
    { label: '参数摘要', value: proposal.parameters_digest || '未返回' },
    { label: '参数字段', value: parameterKeys.length ? parameterKeys.join('、') : '未随页面返回' },
  ];
}

function readRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function readNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value);
  return undefined;
}

function readString(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) return value;
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
}

function budgetFactsFromEvent(payload: AgentRunEvent): RealBudgetFacts {
  const raw = payload as unknown as Record<string, unknown>;
  const usage = readRecord(raw.usage);
  const budget = readRecord(raw.budget) ?? readRecord(raw.budget_snapshot);
  const actions = readRecord(raw.budget_actions);
  const usedTokens = readNumber(usage?.total_tokens ?? usage?.tokens ?? raw.tokens);
  const maxTokens = readNumber(budget?.max_total_tokens ?? budget?.max_tokens);
  const usedCostCny = readString(usage?.cost_cny ?? readRecord(raw.cost)?.amount ?? raw.cost_cny);
  const maxCostCny = readString(budget?.max_cost_cny ?? budget?.max_cost);
  const ratio =
    readNumber(raw.ratio ?? raw.budget_ratio) ?? (usedTokens != null && maxTokens ? usedTokens / maxTokens : undefined);
  const additionalTokens = readNumber(actions?.additional_tokens ?? actions?.tokens);
  const additionalCostCny = readString(actions?.additional_cost_cny ?? actions?.cost_cny ?? actions?.cost);
  return { usedTokens, maxTokens, usedCostCny, maxCostCny, ratio, additionalTokens, additionalCostCny };
}

function mergeBudgetFacts(current: RealBudgetFacts, next: RealBudgetFacts): RealBudgetFacts {
  return {
    usedTokens: next.usedTokens ?? current.usedTokens,
    maxTokens: next.maxTokens ?? current.maxTokens,
    usedCostCny: next.usedCostCny ?? current.usedCostCny,
    maxCostCny: next.maxCostCny ?? current.maxCostCny,
    ratio: next.ratio ?? current.ratio,
    additionalTokens: next.additionalTokens ?? current.additionalTokens,
    additionalCostCny: next.additionalCostCny ?? current.additionalCostCny,
  };
}

function budgetConfirmationRequested(payload: AgentRunEvent) {
  return payload.requires_confirmation === true || payload.budget_actions?.requires_confirmation === true;
}

function RealAgentStatePage({ state }: { state: AgentFixtureState }) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryKey = searchParams.toString();
  const runId = searchParams.get('run_id')?.trim() || undefined;
  const sessionId = searchParams.get('session_id')?.trim() || undefined;
  const requestedApprovalId = searchParams.get('approval_id')?.trim() || undefined;
  const explicitRecoveryRequest = useMemo(() => parseExplicitRecoveryRequest(searchParams), [searchParams]);
  const [messages, setMessages] = useState<RealMessage[]>([]);
  const [runStatus, setRunStatus] = useState<AgentDisplayStatus>('routing');
  const [rawRunStatus, setRawRunStatus] = useState('未加载');
  const [acceptedEventCount, setAcceptedEventCount] = useState(0);
  const [runIntent, setRunIntent] = useState<AgentRunView['intent']>('knowledge_qna');
  const [toolCalls, setToolCalls] = useState<ToolCall[]>([]);
  const [citations, setCitations] = useState<AgentRunView['citations']>([]);
  const [assistantText, setAssistantText] = useState('');
  const [approvalId, setApprovalId] = useState(requestedApprovalId);
  const [approval, setApproval] = useState<ApprovalProposalView>();
  const [approvalDetails, setApprovalDetails] = useState<NonNullable<AgentRunEvent['details']>>();
  const [proposalParameters, setProposalParameters] = useState<Record<string, unknown>>();
  const [proposalDraft, setProposalDraft] = useState<RealProposalDraft | undefined>(() =>
    parseRealProposalDraft(searchParams),
  );
  const [budgetFacts, setBudgetFacts] = useState<RealBudgetFacts>({});
  const [retryable, setRetryable] = useState(false);
  const [checkpointAvailable, setCheckpointAvailable] = useState(Boolean(runId && explicitRecoveryRequest));
  const [checkpointRecovery, setCheckpointRecovery] = useState<AgentRecoveryRequest | undefined>(
    explicitRecoveryRequest,
  );
  const [safetyDegraded, setSafetyDegraded] = useState(false);
  const [cancelReason, setCancelReason] = useState<string>();
  const [loading, setLoading] = useState(Boolean(runId || sessionId || requestedApprovalId));
  const [actionState, setActionState] = useState<RealAgentActionState>('idle');
  const [actionMessage, setActionMessage] = useState('');
  const [error, setError] = useState<string>();
  const [connection, setConnection] = useState<AgentStreamConnection>({ state: 'closed', attempt: 0, maxAttempts: 5 });
  const [streamRevision, setStreamRevision] = useState(0);
  const [input, setInput] = useState('');
  const messagesRef = useRef<HTMLDivElement>(null);
  const streamRef = useRef<AgentStreamHandle>();
  const mountedRef = useRef(true);
  const seenEventIdsRef = useRef(new Set<string>());
  const proposalKeyRef = useRef<string>();
  const streamResumeRef = useRef<{ lastEventId?: string }>({});

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      streamRef.current?.close();
      streamRef.current = undefined;
    };
  }, []);

  useEffect(() => {
    // 查询参数变化时清理旧状态，避免上一条 Run 的事件泄漏到当前状态页。
    streamRef.current?.close();
    streamRef.current = undefined;
    seenEventIdsRef.current = new Set();
    streamResumeRef.current = {};
    // 查询参数切换代表真实资源切换，必须在开始新请求前清理上一条 Run 的本地投影。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMessages([]);
    setRunStatus('routing');
    setRawRunStatus('未加载');
    setAcceptedEventCount(0);
    setRunIntent('knowledge_qna');
    setToolCalls([]);
    setCitations([]);
    setAssistantText('');
    setApprovalId(requestedApprovalId);
    setApproval(undefined);
    setApprovalDetails(undefined);
    setProposalParameters(undefined);
    setProposalDraft(parseRealProposalDraft(searchParams));
    setBudgetFacts({});
    setRetryable(false);
    setCheckpointAvailable(Boolean(runId && explicitRecoveryRequest));
    setCheckpointRecovery(explicitRecoveryRequest);
    setSafetyDegraded(false);
    setCancelReason(undefined);
    setError(undefined);
    setActionState('idle');
    setActionMessage('');
    setConnection({ state: 'closed', attempt: 0, maxAttempts: 5 });
    setLoading(Boolean(runId || sessionId || requestedApprovalId));

    let cancelled = false;
    const loads: Promise<void>[] = [];
    if (sessionId) {
      loads.push(
        loadSessionMessages(sessionId)
          .then((rows) => {
            if (!cancelled && mountedRef.current)
              setMessages([...rows].sort((left, right) => left.sequence_no - right.sequence_no));
          })
          .catch((reason) => {
            if (!cancelled && mountedRef.current)
              setError(reason instanceof Error ? reason.message : '会话消息加载失败。');
          }),
      );
    }
    if (runId) {
      loads.push(
        loadAgentRun(runId)
          .then((run) => {
            if (cancelled || !mountedRef.current) return;
            setRawRunStatus(run.status);
            setRunStatus(displayRunStatus(run.status));
            setAcceptedEventCount(run.accepted_event_count);
          })
          .catch((reason) => {
            if (!cancelled && mountedRef.current)
              setError(reason instanceof Error ? reason.message : '运行状态加载失败。');
          }),
      );
    }
    if (requestedApprovalId) {
      loads.push(
        loadApprovalProposal(requestedApprovalId)
          .then((proposal) => {
            if (!cancelled && mountedRef.current) setApproval(proposal);
          })
          .catch((reason) => {
            if (!cancelled && mountedRef.current)
              setError(reason instanceof Error ? reason.message : '写入提案加载失败。');
          }),
      );
    }
    Promise.all(loads).finally(() => {
      if (!cancelled && mountedRef.current) setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [explicitRecoveryRequest, requestedApprovalId, runId, searchParams, sessionId]);

  useEffect(() => {
    if (!runId) return undefined;
    let active = true;
    const stream = openAgentRunStream(
      runId,
      (eventType, payload, eventId) => {
        if (!active || !mountedRef.current) return;
        const rawPayload = payload as unknown as Record<string, unknown>;
        const normalizedPayload = flattenAgentEventPayload(rawPayload) as AgentRunEvent;
        const normalizedEventType = resolveAgentEventType(eventType, rawPayload);
        const identity =
          eventId ||
          normalizedPayload.sse_event_id ||
          normalizedPayload.event_id ||
          `${eventType}:${normalizedPayload.checkpoint_version ?? ''}`;
        if (identity && seenEventIdsRef.current.has(identity)) return;
        if (identity) seenEventIdsRef.current.add(identity);
        setAcceptedEventCount((current) => current + 1);
        setRawRunStatus(normalizedPayload.status ?? normalizedPayload.state ?? normalizedEventType);
        setRunStatus(realEventDisplayStatus(normalizedEventType, normalizedPayload));
        setBudgetFacts((current) => mergeBudgetFacts(current, budgetFactsFromEvent(normalizedPayload)));
        if (normalizedEventType === 'run.routed') setRunIntent(normalizeRunIntent(normalizedPayload.intent));
        if (normalizedEventType === 'run.tool_started')
          setToolCalls((current) => mergeToolCall(current, normalizedPayload, 'started'));
        if (normalizedEventType === 'run.tool_finished')
          setToolCalls((current) => mergeToolCall(current, normalizedPayload, 'finished'));
        if (normalizedEventType === 'run.answer_stream')
          setAssistantText((current) => current + (normalizedPayload.text ?? ''));
        if (normalizedEventType === 'run.completed') {
          setSafetyDegraded(normalizedPayload.result_type === 'safety_degraded');
          setRetryable(false);
          setCheckpointAvailable(false);
          setCheckpointRecovery(undefined);
          setCitations(
            normalizedPayload.result_type === 'safety_degraded'
              ? []
              : (normalizedPayload.citations ?? []).map((citation) => ({
                  id: citation.citation_id,
                  title: citation.title,
                  snippet: citation.snippet,
                  source: [citation.version, citation.section_path].filter(Boolean).join(' · '),
                })),
          );
        }
        if (normalizedEventType === 'run.failed') {
          setRetryable(normalizedPayload.retryable === true);
          setError(runtimeErrorMessage(normalizedPayload));
        }
        if (normalizedEventType === 'run.cancelled') setCancelReason(normalizedPayload.reason);
        if (normalizedEventType === 'run.checkpoint_saved') {
          setCheckpointRecovery(
            normalizedPayload.approval_request_id ? undefined : recoveryRequestFromEvent(normalizedPayload),
          );
          setCheckpointAvailable(!normalizedPayload.approval_request_id);
          if (normalizedPayload.approval_request_id) setApprovalId(String(normalizedPayload.approval_request_id));
        }
        if (normalizedEventType === 'run.clarification_requested' || normalizedEventType === 'run.checkpoint_saved') {
          if (normalizedPayload.details) {
            setApprovalDetails(normalizedPayload.details);
            setProposalParameters(approvalParameters(normalizedPayload.details, normalizedPayload.resource_type));
          }
          if (
            normalizedPayload.operation &&
            normalizedPayload.resource_type &&
            normalizedPayload.details &&
            !normalizedPayload.approval_request_id
          ) {
            setProposalDraft({
              operation: normalizedPayload.operation,
              resourceType: normalizedPayload.resource_type,
              parameters: approvalParameters(normalizedPayload.details, normalizedPayload.resource_type),
            });
          }
          if (normalizedPayload.approval_request_id) {
            const nextApprovalId = String(normalizedPayload.approval_request_id);
            void loadApprovalProposal(nextApprovalId)
              .then((proposal) => {
                if (active && mountedRef.current) setApproval(proposal);
              })
              .catch((reason) => {
                if (active && mountedRef.current)
                  setError(reason instanceof Error ? reason.message : '写入提案加载失败。');
              });
          }
        }
      },
      {
        maxAttempts: 5,
        lastEventId: streamResumeRef.current.lastEventId,
        onStateChange: (nextConnection) => {
          if (active && mountedRef.current) setConnection(nextConnection);
        },
        onError: (nextConnection) => {
          if (active && mountedRef.current && nextConnection.state === 'exhausted')
            setError('SSE 连接重试已耗尽，请刷新页面。');
        },
      },
    );
    streamRef.current = stream;
    return () => {
      active = false;
      stream.close();
      if (streamRef.current === stream) streamRef.current = undefined;
    };
  }, [queryKey, runId, streamRevision]);

  const refreshApproval = async (targetId: string) => {
    const next = await loadApprovalProposal(targetId);
    if (mountedRef.current) setApproval(next);
    return next;
  };

  const closeRunStream = () => {
    const current = streamRef.current;
    streamResumeRef.current = {
      lastEventId: current?.getConnection().lastEventId ?? connection.lastEventId,
    };
    current?.close();
    streamRef.current = undefined;
  };

  const resumeRunStream = () => {
    if (!runId) return;
    setStreamRevision((revision) => revision + 1);
  };

  const executeProposal = async (mode: 'confirm' | 'reject') => {
    if (!approvalId || actionState === 'loading') return;
    const parameters = approvalDetails
      ? approvalParameters(approvalDetails, approval?.resource_type)
      : (proposalParameters ?? proposalDraft?.parameters);
    if (!parameters) {
      setActionState('error');
      setActionMessage('后端没有返回可校验的提案参数，未发送写入请求。');
      return;
    }
    setActionState('loading');
    setActionMessage(
      mode === 'confirm' ? '确认请求已提交，等待后端返回执行状态。' : '取消请求已提交，等待后端返回审批状态。',
    );
    try {
      if (mode === 'reject') {
        const rejected = await rejectAgentWrite(approvalId, parameters);
        setApproval(rejected);
        setActionState('success');
        setActionMessage(`后端已返回审批状态：${rejected.status || '未知'}。`);
        return;
      }
      const confirmed = await confirmAgentWrite(approvalId, parameters);
      setApproval(confirmed);
      if (!['confirmed', 'executed'].includes(confirmed.status.toLowerCase())) {
        setActionState('error');
        setActionMessage(`后端未进入可执行状态，当前状态：${confirmed.status || '未知'}。`);
        return;
      }
      const executed = await executeAgentWrite(approvalId, parameters);
      setActionState('success');
      setActionMessage(`后端已返回执行状态：${executed.status || '未知'}，页面不推断额外业务结果。`);
      await refreshApproval(approvalId);
    } catch (reason) {
      setActionState('error');
      setActionMessage(reason instanceof Error ? reason.message : '写入提案操作失败，请稍后重试。');
    }
  };

  const createProposal = async () => {
    if (!proposalDraft || actionState === 'loading') return;
    setActionState('loading');
    setActionMessage('正在创建写入提案，等待后端返回提案状态。');
    try {
      const created = await createApprovalProposal({
        sessionId: queryNumber(sessionId ?? null) ?? sessionId,
        agentRunId: queryNumber(runId ?? null) ?? runId,
        operation: proposalDraft.operation,
        resourceType: proposalDraft.resourceType,
        resourceId: proposalDraft.resourceId,
        parameters: proposalDraft.parameters,
        idempotencyKey:
          proposalKeyRef.current ??
          (proposalKeyRef.current = `real-agent-state-${runId ?? sessionId ?? requestedApprovalId ?? 'proposal'}`),
      });
      setApprovalId(created.approval_request_id);
      setApproval(created);
      setProposalParameters(proposalDraft.parameters);
      setActionState('success');
      setActionMessage(`提案已创建：${created.approval_request_id}，当前状态为 ${created.status || '未知'}。`);
    } catch (reason) {
      setActionState('error');
      setActionMessage(reason instanceof Error ? reason.message : '创建写入提案失败，请稍后重试。');
    }
  };

  const cancelRun = async () => {
    if (!runId || actionState === 'loading') return;
    // 取消请求期间先关闭旧连接，避免取消前后的事件被两个订阅重复消费。
    closeRunStream();
    setActionState('loading');
    setActionMessage('取消请求已提交，终态以服务端 cancelled 事件为准。');
    try {
      const result = await cancelAgentRun(runId);
      setActionState('success');
      setActionMessage(`后端已接受取消请求：${result.status || '未知'}。`);
      resumeRunStream();
    } catch (reason) {
      setActionState('error');
      setActionMessage(reason instanceof Error ? reason.message : '取消运行失败，请稍后重试。');
      resumeRunStream();
    }
  };

  const extendBudget = async () => {
    if (!runId || actionState === 'loading' || budgetFacts.additionalTokens == null || !budgetFacts.additionalCostCny)
      return;
    closeRunStream();
    setActionState('loading');
    setActionMessage('预算追加请求已提交，当前 Run 将继续等待后端事件。');
    try {
      const result = await extendAgentRunBudget(runId, budgetFacts.additionalTokens, budgetFacts.additionalCostCny);
      setActionState('success');
      setActionMessage(
        `当前 Run 已返回预算追加状态：${result.status || '未知'}（dispatch attempt ${result.attempt}）。`,
      );
      setRunStatus('routing');
      setBudgetFacts((current) => ({ ...current, additionalTokens: undefined, additionalCostCny: undefined }));
      resumeRunStream();
    } catch (reason) {
      setActionState('error');
      setActionMessage(reason instanceof Error ? reason.message : '预算追加失败，请稍后重试。');
      resumeRunStream();
    }
  };

  const retryRun = async () => {
    if (!runId || !retryable || actionState === 'loading') return;
    closeRunStream();
    setActionState('loading');
    setActionMessage('重试请求已提交，等待后端运行事件。');
    try {
      const result = await retryAgentRun(runId);
      setActionState('success');
      setActionMessage(`后端已返回重试状态：${result.status || '未知'}（dispatch attempt ${result.attempt}）。`);
      setRetryable(false);
      setRunStatus('routing');
      resumeRunStream();
    } catch (reason) {
      setActionState('error');
      setActionMessage(reason instanceof Error ? reason.message : '重试请求失败，请稍后重试。');
      resumeRunStream();
    }
  };

  const recoverRun = async () => {
    if (!runId || !checkpointAvailable || actionState === 'loading') return;
    closeRunStream();
    setActionState('loading');
    setActionMessage(
      checkpointRecovery
        ? '正在提交 checkpoint 元数据，等待后端校验并恢复当前 Run。'
        : '正在请求从已持久化 checkpoint 恢复当前 Run。',
    );
    try {
      const result = checkpointRecovery
        ? await recoverAgentRun(runId, checkpointRecovery)
        : await recoverAgentRunFromCheckpoint(runId);
      setCheckpointAvailable(false);
      setCheckpointRecovery(undefined);
      setActionState('success');
      setActionMessage(`后端已返回恢复状态：${result.status || '未知'}（dispatch attempt ${result.attempt}）。`);
      setRunStatus('routing');
      resumeRunStream();
    } catch (reason) {
      setActionState('error');
      setActionMessage(reason instanceof Error ? reason.message : '运行恢复失败，请稍后重试。');
      resumeRunStream();
    }
  };

  const sendFollowUp = () => {
    const prompt = input.trim();
    if (!prompt) return;
    if (!sessionId) {
      setActionState('error');
      setActionMessage('当前状态页缺少 session_id，未创建新的 Run。');
      return;
    }
    navigate(`/chat/${encodeURIComponent(sessionId)}?transport=chat-run&prompt=${encodeURIComponent(prompt)}`);
  };

  const realRun: AgentRunView = {
    id: runId ?? '未绑定运行',
    status: runStatus,
    intent: runIntent,
    toolsUsed: toolCalls.filter((tool) => tool.status === 'success').length,
    toolsTotal: toolCalls.length,
    agentsUsed: runId ? 1 : 0,
    agentsTotal: runId ? 1 : 0,
    toolCalls,
    citations,
    connection,
  };
  const terminal = ['completed', 'failed', 'cancelled', 'superseded'].includes(runStatus);
  const mappedMessages: ChatMessage[] = messages.map((message) => ({
    id: message.message_id,
    role: message.role,
    content: message.content,
    time: message.created_at,
    agentRunId: message.agent_run_id,
  }));
  const proposalParametersValue = approvalDetails
    ? approvalParameters(approvalDetails, approval?.resource_type)
    : (proposalParameters ?? proposalDraft?.parameters);
  const budgetPercent =
    budgetFacts.ratio == null ? undefined : Math.max(0, Math.min(100, Math.round(budgetFacts.ratio * 100)));

  return (
    <ChatSurface
      run={realRun}
      messagesRef={messagesRef}
      input={input}
      running={Boolean(runId) && !terminal && connection.state !== 'exhausted' && actionState !== 'loading'}
      disabled={loading || actionState === 'loading' || state === 'sse-reconnecting'}
      onChange={setInput}
      onSend={sendFollowUp}
      onStop={() => void cancelRun()}
      placeholder={
        state === 'user-cancelled' || safetyDegraded ? '继续追问或重新开始...' : '当前状态页只展示真实运行事实...'
      }
      showTrace={Boolean(runId)}
      showKnowledgeTopNav={false}
    >
      {loading ? <p className={styles.systemMessage}>正在加载真实运行状态...</p> : null}
      {!runId && !sessionId && !requestedApprovalId ? (
        <Alert className={styles.realStateNotice} role="alert" variant="warning">
          <AlertTitle>真实状态页缺少运行标识</AlertTitle>
          <AlertDescription>
            请从真实会话携带 run_id、session_id 或 approval_id 进入；当前页面不会显示 Fixture 数据。
          </AlertDescription>
        </Alert>
      ) : null}
      {error ? (
        <Alert className={styles.realStateNotice} role="alert" variant="destructive">
          <AlertTitle>真实状态加载失败</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}
      {runId ? (
        <section className={styles.realStateSummary} aria-label="真实运行摘要">
          <div>
            <strong>RUN ID</strong>
            <span>{runId}</span>
          </div>
          <div>
            <strong>服务端状态</strong>
            <span>{rawRunStatus}</span>
          </div>
          <div>
            <strong>已接收事件</strong>
            <span>{acceptedEventCount}</span>
          </div>
        </section>
      ) : null}
      {mappedMessages.map((message) => (
        <MessageBubble key={message.id} message={message} />
      ))}
      {assistantText &&
      !messages.some((message) => message.role === 'assistant' && (!runId || message.agent_run_id === runId)) ? (
        <MessageBubble
          message={{
            id: 'real-state-assistant-stream',
            role: 'assistant',
            content: assistantText,
            time: new Date().toISOString(),
          }}
        />
      ) : null}
      {state === 'write-confirmation' ? (
        <Card className={styles.realStateCard}>
          <h2>写入提案</h2>
          {approval ? (
            <>
              <dl className={styles.realStateDetails}>
                {realApprovalData(approval, approvalDetails, proposalParametersValue).map((item) => (
                  <div key={item.label}>
                    <dt>{item.label}</dt>
                    <dd>{item.value}</dd>
                  </div>
                ))}
              </dl>
              <p className={styles.realStateMeta}>
                后端状态：{approval.status || '未返回'} · 过期时间：{approval.expires_at || '未返回'}
              </p>
              <div className={styles.realStateActions}>
                <Button
                  disabled={
                    actionState === 'loading' ||
                    !proposalParametersValue ||
                    ['rejected', 'executed'].includes(approval.status.trim().toLowerCase())
                  }
                  onClick={() => void executeProposal('confirm')}
                >
                  确认并执行
                </Button>
                <Button
                  variant="outline"
                  disabled={
                    actionState === 'loading' ||
                    !proposalParametersValue ||
                    ['rejected', 'executed'].includes(approval.status.trim().toLowerCase())
                  }
                  onClick={() => void executeProposal('reject')}
                >
                  拒绝写入
                </Button>
              </div>
            </>
          ) : proposalDraft ? (
            <>
              <p className={styles.realStateMeta}>页面收到待创建的真实提案参数，创建操作将由后端生成摘要和过期时间。</p>
              <Button disabled={actionState === 'loading'} onClick={() => void createProposal()}>
                创建写入提案
              </Button>
            </>
          ) : (
            <p className={styles.realStateMeta}>
              等待后端返回 approval_id 和提案参数；未满足校验条件前不会发送写入请求。
            </p>
          )}
        </Card>
      ) : null}
      {state === 'budget-limit' ? (
        <Card className={styles.realStateCard}>
          <h2>预算状态</h2>
          <dl className={styles.realStateDetails}>
            <div>
              <dt>已用 Token</dt>
              <dd>{budgetFacts.usedTokens == null ? '后端未返回' : budgetFacts.usedTokens.toLocaleString('en-US')}</dd>
            </div>
            <div>
              <dt>Token 上限</dt>
              <dd>{budgetFacts.maxTokens == null ? '后端未返回' : budgetFacts.maxTokens.toLocaleString('en-US')}</dd>
            </div>
            <div>
              <dt>使用比例</dt>
              <dd>{budgetPercent == null ? '后端未返回' : `${budgetPercent}%`}</dd>
            </div>
            <div>
              <dt>已用成本</dt>
              <dd>{budgetFacts.usedCostCny == null ? '后端未返回' : `¥${budgetFacts.usedCostCny}`}</dd>
            </div>
          </dl>
          {budgetFacts.additionalTokens != null && budgetFacts.additionalCostCny ? (
            <div className={styles.realStateActions}>
              <Button disabled={actionState === 'loading'} onClick={() => void extendBudget()}>
                追加 {budgetFacts.additionalTokens.toLocaleString('en-US')} tokens
              </Button>
              <Button variant="outline" disabled={actionState === 'loading'} onClick={() => void cancelRun()}>
                结束当前 Run
              </Button>
            </div>
          ) : (
            <>
              <p className={styles.realStateMeta}>后端未返回可确认的追加额度，页面不会猜测费用或 Token 数。</p>
              {runId ? (
                <Button variant="outline" disabled={actionState === 'loading'} onClick={() => void cancelRun()}>
                  结束当前 Run
                </Button>
              ) : null}
            </>
          )}
        </Card>
      ) : null}
      {state === 'tool-failed-retryable' ? (
        <Card className={styles.realStateCard}>
          <h2>工具失败状态</h2>
          <p className={styles.realStateMeta}>{error || '等待后端返回失败原因。'}</p>
          {retryable ? (
            <Button disabled={actionState === 'loading'} onClick={() => void retryRun()}>
              重试当前 Run
            </Button>
          ) : (
            <p className={styles.realStateMeta}>当前失败未被后端标记为可重试，页面不显示重试请求。</p>
          )}
        </Card>
      ) : null}
      {state === 'safety-degraded' ? (
        <Alert className={styles.realStateNotice} role="status" variant="warning">
          <AlertTitle>安全降级状态</AlertTitle>
          <AlertDescription>
            {safetyDegraded
              ? '后端已标记本次结果为安全降级，回答范围和引用可能不完整。'
              : '等待后端返回安全降级结果标记。'}
          </AlertDescription>
        </Alert>
      ) : null}
      {state === 'user-cancelled' ? (
        <Alert className={styles.realStateNotice} role="status" variant="warning">
          <AlertTitle>运行已取消</AlertTitle>
          <AlertDescription>
            {cancelReason ? `取消原因：${cancelReason}` : '等待后端返回取消原因；已接收文本会保留。'}
          </AlertDescription>
        </Alert>
      ) : null}
      {state === 'sse-reconnecting' ? (
        <div className={styles.realStateConnection} role={connection.state === 'exhausted' ? 'alert' : 'status'}>
          <strong>
            {connection.state === 'reconnecting'
              ? '连接已中断，正在重新连接...'
              : connection.state === 'exhausted'
                ? '连接重试已耗尽'
                : connection.state === 'connected'
                  ? '实时连接已恢复'
                  : '正在连接实时事件...'}
          </strong>
          <span>
            第 {connection.attempt} / {connection.maxAttempts} 次尝试 · 最近事件 {connection.lastEventId || '未返回'}
          </span>
        </div>
      ) : null}
      {checkpointAvailable ? (
        <Card className={styles.realStateCard}>
          <h2>{checkpointRecovery ? '使用 checkpoint 元数据恢复' : '可从 checkpoint 恢复'}</h2>
          <p className={styles.realStateMeta}>
            {checkpointRecovery
              ? '页面仅转发服务端事件中的 checkpoint 元数据，最终校验和恢复由 Java 服务端完成。'
              : '恢复内容由 Java 服务端按已持久化 checkpoint 校验，浏览器不会提交 checkpoint 内容。'}
          </p>
          <Button disabled={actionState === 'loading'} onClick={() => void recoverRun()}>
            {checkpointRecovery ? '提交恢复请求' : '从 checkpoint 恢复'}
          </Button>
        </Card>
      ) : null}
      {actionMessage ? (
        <p className={styles.realStateActionMessage} role="status">
          {actionMessage}
        </p>
      ) : null}
    </ChatSurface>
  );
}

function RealChatPage() {
  const { session_id: sessionId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const navigationState = readChatNavigationState(location.state);
  const pendingPrompt = navigationState.pendingPrompt;
  const navigationError = navigationState.sendError;
  const seedPrompt = searchParams.get('prompt') ?? '';
  const [messages, setMessages] = useState<RealMessage[]>([]);
  const [input, setInput] = useState(pendingPrompt ?? seedPrompt);
  const [loading, setLoading] = useState(Boolean(sessionId));
  const [sending, setSending] = useState(false);
  const [activeRunId, setActiveRunId] = useState<string>();
  const [runIntent, setRunIntent] = useState<AgentRunView['intent']>('knowledge_qna');
  const [toolCalls, setToolCalls] = useState<ToolCall[]>([]);
  const [citations, setCitations] = useState<AgentRunView['citations']>([]);
  const [runStatus, setRunStatus] = useState('idle');
  const [assistantText, setAssistantText] = useState('');
  const [assistantTime, setAssistantTime] = useState('');
  const [assistantMessageId, setAssistantMessageId] = useState<string>();
  const [error, setError] = useState<string>();
  const [retryAvailable, setRetryAvailable] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [safetyDegraded, setSafetyDegraded] = useState(false);
  const [budgetConfirmation, setBudgetConfirmation] = useState(false);
  const [budgetFacts, setBudgetFacts] = useState<RealBudgetFacts>({});
  const [budgetSubmitting, setBudgetSubmitting] = useState(false);
  const [checkpointAvailable, setCheckpointAvailable] = useState(false);
  const [checkpointRecovery, setCheckpointRecovery] = useState<AgentRecoveryRequest>();
  const [approval, setApproval] = useState<{
    id: string;
    operation?: string;
    resourceType?: string;
    toolName?: string;
    details: NonNullable<AgentRunEvent['details']>;
  }>();
  const [approvalSubmitting, setApprovalSubmitting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelAcknowledged, setCancelAcknowledged] = useState(false);
  const [editingMessageId, setEditingMessageId] = useState<string>();
  const [editingContent, setEditingContent] = useState('');
  const [messageToDelete, setMessageToDelete] = useState<RealMessage>();
  const [messageMutation, setMessageMutation] = useState<{
    messageId: string;
    action: 'edit' | 'delete';
  }>();
  const [messageError, setMessageError] = useState<string>();
  const [messageNotice, setMessageNotice] = useState<string>();
  const [connection, setConnection] = useState<AgentStreamConnection>({ state: 'closed', attempt: 0, maxAttempts: 5 });
  const [streamGeneration, setStreamGeneration] = useState(0);
  const messagesRef = useRef<HTMLDivElement>(null);
  const messagesStateRef = useRef<RealMessage[]>([]);
  const messageLoadGenerationRef = useRef(0);
  const streamRef = useRef<AgentStreamHandle>();
  const streamResumeRef = useRef<{ lastEventId?: string; preserveContent: boolean }>({ preserveContent: false });
  const mountedRef = useRef(true);

  useEffect(() => {
    // 在订阅新 Run 前同步历史消息，避免回放时重复追加已持久化的回答。
    messagesStateRef.current = messages;
  }, [messages]);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    // 路由切换先关闭旧 Run，避免旧会话的事件继续写入新会话状态。
    streamRef.current?.close();
    streamRef.current = undefined;
    streamResumeRef.current = { preserveContent: false };
    // 路由变化时重置状态，后续由新的 SSE 订阅接管这些值。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setActiveRunId(undefined);
    setInput(pendingPrompt ?? seedPrompt);
    setRunIntent('knowledge_qna');
    setToolCalls([]);
    setRunStatus('idle');
    setAssistantText('');
    setAssistantTime('');
    setAssistantMessageId(undefined);
    setCitations([]);
    setRetryAvailable(false);
    setRetrying(false);
    setSafetyDegraded(false);
    setBudgetConfirmation(false);
    setBudgetFacts({});
    setBudgetSubmitting(false);
    setCheckpointAvailable(false);
    setCheckpointRecovery(undefined);
    setApproval(undefined);
    setApprovalSubmitting(false);
    setCancelling(false);
    setCancelAcknowledged(false);
    setEditingMessageId(undefined);
    setEditingContent('');
    setMessageToDelete(undefined);
    setMessageMutation(undefined);
    setMessageError(undefined);
    setMessageNotice(undefined);
    setConnection({ state: 'closed', attempt: 0, maxAttempts: 5 });
    if (!sessionId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(navigationError);
    const loadGeneration = ++messageLoadGenerationRef.current;
    loadSessionMessages(sessionId)
      .then((rows) => {
        if (cancelled || loadGeneration !== messageLoadGenerationRef.current) return;
        const ordered = [...rows].sort((a, b) => a.sequence_no - b.sequence_no);
        setMessages(ordered);
        // 重新进入历史会话时恢复最近一次 Run，才能回放终态事件和引用。
        const latestRunId = [...ordered].reverse().find((message) => message.agent_run_id)?.agent_run_id;
        if (latestRunId) setActiveRunId(String(latestRunId));
      })
      .catch((reason) => {
        if (!cancelled && loadGeneration === messageLoadGenerationRef.current)
          setError(reason instanceof Error ? reason.message : '消息加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [navigationError, pendingPrompt, seedPrompt, sessionId]);

  useEffect(() => {
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, assistantText]);

  useEffect(() => {
    if (!activeRunId) return undefined;
    let streamActive = true;
    const streamResume = streamResumeRef.current;
    const hasPersistedAnswer = messagesStateRef.current.some(
      (message) => message.agent_run_id === activeRunId && message.role === 'assistant',
    );
    // SSE 订阅建立后先进入排队状态，再接收运行事件。
    if (!streamResume.preserveContent) {
      setRunStatus('queued');
      setAssistantText('');
    }
    const stream = openAgentRunStream(
      activeRunId,
      (eventType, payload) => {
        if (!streamActive || !mountedRef.current) return;
        const rawPayload = payload as unknown as Record<string, unknown>;
        const normalizedPayload = flattenAgentEventPayload(rawPayload) as AgentRunEvent;
        const normalizedEventType = resolveAgentEventType(eventType, rawPayload);
        setBudgetFacts((current) => mergeBudgetFacts(current, budgetFactsFromEvent(normalizedPayload)));
        if (budgetConfirmationRequested(normalizedPayload)) setBudgetConfirmation(true);
        if (normalizedEventType === 'run.created' || normalizedEventType === 'run.accepted') {
          setRunStatus('queued');
          return;
        }
        if (normalizedEventType === 'run.routed') {
          setRunIntent(normalizeRunIntent(normalizedPayload.intent));
          setRunStatus('routed');
          return;
        }
        if (normalizedEventType === 'run.planned') {
          setRunStatus('planning');
          return;
        }
        if (
          normalizedEventType === 'run.context_assembled' ||
          normalizedEventType === 'run.retrieval_started' ||
          normalizedEventType === 'run.retrieval_finished'
        ) {
          setRunStatus('retrieving');
          return;
        }
        if (normalizedEventType === 'run.tool_started') {
          setRunStatus('executing');
          setToolCalls((current) => mergeToolCall(current, normalizedPayload, 'started'));
          return;
        }
        if (normalizedEventType === 'run.tool_finished') {
          setToolCalls((current) => mergeToolCall(current, normalizedPayload, 'finished'));
          return;
        }
        if (normalizedEventType === 'run.eval_decided') {
          setRunStatus('validating');
          return;
        }
        if (normalizedEventType === 'run.model_usage') {
          setRunStatus('composing');
          return;
        }
        if (normalizedEventType === 'run.answer_stream') {
          setRunStatus('composing');
          setAssistantTime((current) => current || new Date().toISOString());
          if (!hasPersistedAnswer) setAssistantText((current) => current + (normalizedPayload.text ?? ''));
          return;
        }
        if (normalizedEventType === 'run.completed') {
          setCancelling(false);
          setCancelAcknowledged(false);
          setRetryAvailable(false);
          setRetrying(false);
          setRunStatus('completed');
          setCheckpointAvailable(false);
          setCheckpointRecovery(undefined);
          setApproval(undefined);
          setAssistantTime((current) => current || new Date().toISOString());
          if (!hasPersistedAnswer) setAssistantText((current) => normalizedPayload.answer ?? current);
          const degraded = normalizedPayload.result_type === 'safety_degraded';
          setSafetyDegraded(degraded);
          if (sessionId) {
            void loadSessionMessages(sessionId)
              .then((rows) => {
                if (!streamActive || !mountedRef.current) return;
                const assistant = rows.find(
                  (message) => message.agent_run_id === activeRunId && message.role === 'assistant',
                );
                setAssistantMessageId(assistant?.message_id);
              })
              .catch((reason) => {
                if (!streamActive || !mountedRef.current) return;
                setError(reason instanceof Error ? reason.message : '回答完成后刷新消息失败，请刷新会话。');
              });
          }
          setCitations(
            degraded
              ? []
              : (normalizedPayload.citations ?? []).map((citation) => ({
                  id: citation.citation_id,
                  title: citation.title,
                  snippet: citation.snippet,
                  source: [citation.version, citation.section_path].filter(Boolean).join(' · '),
                })),
          );
          setBudgetConfirmation((current) => current || budgetConfirmationRequested(normalizedPayload));
          return;
        }
        if (normalizedEventType === 'run.checkpoint_saved') {
          if (normalizedPayload.approval_request_id) {
            setRunStatus('waiting_user');
            setCheckpointAvailable(false);
            setCheckpointRecovery(undefined);
            setApproval({
              id: normalizedPayload.approval_request_id,
              operation: normalizedPayload.operation,
              resourceType: normalizedPayload.resource_type,
              toolName: normalizedPayload.tool_name,
              details: normalizedPayload.details ?? {},
            });
          } else {
            setRunStatus('waiting_user');
            setCheckpointRecovery(recoveryRequestFromEvent(normalizedPayload));
            setCheckpointAvailable(true);
          }
          return;
        }
        if (normalizedEventType === 'run.failed') {
          setCancelling(false);
          setCancelAcknowledged(false);
          setRunStatus('failed');
          setCheckpointAvailable(false);
          setCheckpointRecovery(undefined);
          setSafetyDegraded(false);
          setRetrying(false);
          setBudgetSubmitting(false);
          setRetryAvailable(normalizedPayload.retryable === true);
          if (budgetConfirmationRequested(normalizedPayload)) setBudgetConfirmation(true);
          setError(runtimeErrorMessage(normalizedPayload));
          return;
        }
        if (normalizedEventType === 'run.cancelled') {
          setRunStatus('cancelled');
          setCancelling(false);
          setCancelAcknowledged(false);
          setCheckpointAvailable(false);
          setCheckpointRecovery(undefined);
          setRetryAvailable(false);
          setRetrying(false);
          setBudgetConfirmation(false);
          setBudgetSubmitting(false);
          return;
        }
        if (normalizedEventType === 'run.superseded') {
          setCancelling(false);
          setCancelAcknowledged(false);
          setRunStatus('superseded');
          setCheckpointAvailable(false);
          setCheckpointRecovery(undefined);
          setBudgetConfirmation(false);
          setBudgetSubmitting(false);
          return;
        }
        if (normalizedEventType === 'run.clarification_requested') {
          setRunStatus('waiting_user');
          if (normalizedPayload.approval_request_id) {
            setCheckpointAvailable(false);
            setApproval({
              id: normalizedPayload.approval_request_id,
              operation: normalizedPayload.operation,
              resourceType: normalizedPayload.resource_type,
              toolName: normalizedPayload.tool_name,
              details: normalizedPayload.details ?? {},
            });
          }
          return;
        }
        if (normalizedEventType === 'run.cancel_acknowledged') {
          setCancelAcknowledged(true);
          return;
        }
        setRunStatus(normalizedPayload.status ?? normalizedPayload.state ?? normalizedEventType.replace('run.', ''));
      },
      {
        maxAttempts: 5,
        lastEventId: streamResume.lastEventId,
        onStateChange: (nextConnection) => {
          if (streamActive && mountedRef.current) setConnection(nextConnection);
        },
        onError: () => {
          if (!streamActive || !mountedRef.current) return;
          // exhausted 使用专用连接提示，避免与通用错误卡片重复展示两个 alert。
          setError(undefined);
        },
      },
    );
    streamRef.current = stream;
    return () => {
      streamActive = false;
      stream.close();
      if (streamRef.current === stream) streamRef.current = undefined;
    };
  }, [activeRunId, sessionId, streamGeneration]);

  const send = async () => {
    const content = input.trim();
    if (!content || sending) return;
    setError(undefined);
    setSending(true);
    setRetryAvailable(false);
    setSafetyDegraded(false);
    setBudgetConfirmation(false);
    setBudgetFacts({});
    setBudgetSubmitting(false);
    // 新消息开始后清理上一条 Run 的瞬态展示，避免旧工具和反馈状态挂到新回答上。
    setRunIntent('knowledge_qna');
    setToolCalls([]);
    setAssistantText('');
    setAssistantTime('');
    setAssistantMessageId(undefined);
    let createdSessionId: string | undefined;
    try {
      let target = sessionId;
      if (!target) {
        const created = await createSession(content.slice(0, 40));
        target = String(created.session_id);
        if (!target || target === 'undefined') throw new Error('会话创建响应缺少 session_id。');
        createdSessionId = target;
      }
      const saved = await sendUserMessage(target, content);
      if (createdSessionId) {
        // 新会话先确认首条消息已被服务端接收，再切换路由加载真实消息和 Run。
        setInput('');
        navigate(`/chat/${encodeURIComponent(createdSessionId)}`, { replace: true });
        return;
      }
      setMessages((current) => [...current, saved].sort((a, b) => a.sequence_no - b.sequence_no));
      if (saved.agent_run_id) {
        // 新 Run 不得继承旧 Run 的 SSE 游标，避免跳过新运行的首批事件。
        streamResumeRef.current = { preserveContent: false };
        setCancelAcknowledged(false);
        setActiveRunId(String(saved.agent_run_id));
      } else {
        setActiveRunId(undefined);
        setRunStatus('idle');
      }
      setCitations([]);
      setInput('');
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '消息发送失败';
      if (createdSessionId) {
        // 会话已创建但首条消息失败时保留真实会话，用户可以在当前路由重试发送。
        navigate(`/chat/${encodeURIComponent(createdSessionId)}`, {
          replace: true,
          state: { sendError: message, pendingPrompt: content } satisfies ChatNavigationState,
        });
        return;
      }
      setError(message);
    } finally {
      setSending(false);
    }
  };

  const retryFailed = () => {
    if (!activeRunId || !retryAvailable || retrying) return;
    const resumeCursor = connection.lastEventId;
    streamRef.current?.close();
    streamRef.current = undefined;
    streamResumeRef.current = { lastEventId: resumeCursor, preserveContent: false };
    setRetrying(true);
    setRetryAvailable(false);
    setError(undefined);
    void retryAgentRun(activeRunId)
      .then(() => {
        if (!mountedRef.current) return;
        setRunStatus('queued');
        setToolCalls([]);
        setAssistantText('');
        setAssistantTime('');
        setAssistantMessageId(undefined);
        setRunIntent('knowledge_qna');
        setCitations([]);
        setSafetyDegraded(false);
        setStreamGeneration((current) => current + 1);
      })
      .catch((reason) => {
        if (!mountedRef.current) return;
        setRetryAvailable(true);
        setError(reason instanceof Error ? reason.message : '重试请求失败');
      })
      .finally(() => {
        if (mountedRef.current) setRetrying(false);
      });
  };

  const cancelActiveRun = () => {
    if (cancelling || !activeRunId) return;
    const currentStream = streamRef.current;
    const resumeCursor = currentStream?.getConnection().lastEventId ?? connection.lastEventId;
    // 取消请求发出前关闭旧连接，收到 HTTP 接受响应后再从原游标续接终态。
    currentStream?.close();
    if (streamRef.current === currentStream) streamRef.current = undefined;
    streamResumeRef.current = { lastEventId: resumeCursor, preserveContent: true };
    setCancelAcknowledged(false);
    setCancelling(true);
    setBudgetConfirmation(false);
    setBudgetSubmitting(false);
    void cancelAgentRun(activeRunId)
      .then(() => {
        if (!mountedRef.current) return;
        setCheckpointAvailable(false);
        // HTTP 200 只表示取消请求已被接受，真正的 cancelled 必须来自 SSE 终态事件。
        setStreamGeneration((current) => current + 1);
      })
      .catch((reason) => {
        if (!mountedRef.current) return;
        setError(reason instanceof Error ? reason.message : '取消运行失败');
        setCancelling(false);
        // 取消失败时恢复原订阅，继续接收尚未结束的运行事件。
        setStreamGeneration((current) => current + 1);
      });
  };

  const refreshMessages = async (targetSessionId: string) => {
    const loadGeneration = ++messageLoadGenerationRef.current;
    const rows = await loadSessionMessages(targetSessionId);
    if (!mountedRef.current || loadGeneration !== messageLoadGenerationRef.current) return false;
    setMessages([...rows].sort((a, b) => a.sequence_no - b.sequence_no));
    return true;
  };

  const startMessageEdit = (message: RealMessage) => {
    setMessageError(undefined);
    setMessageNotice(undefined);
    setEditingMessageId(message.message_id);
    setEditingContent(message.content);
  };

  const cancelMessageEdit = () => {
    if (messageMutation?.action === 'edit') return;
    setEditingMessageId(undefined);
    setEditingContent('');
    setMessageError(undefined);
  };

  const saveMessageEdit = async (message: RealMessage) => {
    if (!sessionId || !editingContent.trim() || messageMutation) return;
    const originalContent = message.content;
    setMessageError(undefined);
    setMessageNotice(undefined);
    setMessageMutation({ messageId: message.message_id, action: 'edit' });
    try {
      await updateMessage(sessionId, message.message_id, editingContent.trim());
      await refreshMessages(sessionId);
      if (!mountedRef.current) return;
      setEditingMessageId(undefined);
      setEditingContent('');
      setMessageNotice('消息已更新。');
    } catch (reason) {
      if (!mountedRef.current) return;
      // 服务端失败时恢复原文，避免本地草稿被误认为已经写入。
      setEditingContent(originalContent);
      setMessageError(messageMutationError(reason, '消息更新失败，请稍后重试。'));
    } finally {
      if (mountedRef.current) setMessageMutation(undefined);
    }
  };

  const confirmMessageDelete = async () => {
    if (!sessionId || !messageToDelete || messageMutation) return;
    setMessageError(undefined);
    setMessageNotice(undefined);
    setMessageMutation({ messageId: messageToDelete.message_id, action: 'delete' });
    try {
      await deleteMessage(sessionId, messageToDelete.message_id);
      await refreshMessages(sessionId);
      if (!mountedRef.current) return;
      setMessageToDelete(undefined);
      setMessageNotice('消息已删除。');
    } catch (reason) {
      if (!mountedRef.current) return;
      // 删除请求未成功时不改变本地列表，原消息继续保留。
      setMessageError(messageMutationError(reason, '消息删除失败，请稍后重试。'));
    } finally {
      if (mountedRef.current) setMessageMutation(undefined);
    }
  };

  const realRun: AgentRunView = {
    id: activeRunId ?? '等待运行',
    status: displayRunStatus(runStatus === 'idle' ? 'completed' : runStatus),
    intent: runIntent,
    toolsUsed: toolCalls.filter((tool) => tool.status === 'success').length,
    toolsTotal: toolCalls.length,
    agentsUsed: activeRunId ? 1 : 0,
    agentsTotal: activeRunId ? 1 : 0,
    toolCalls,
    citations,
    connection,
  };

  const mappedMessages: ChatMessage[] = messages.map((message) => ({
    id: message.message_id,
    role: message.role,
    content: message.content,
    time: message.created_at,
    source: undefined,
    agentRunId: message.agent_run_id,
  }));
  const budgetPercent =
    budgetFacts.ratio == null ? undefined : Math.max(0, Math.min(100, Math.round(budgetFacts.ratio * 100)));

  return (
    <ChatSurface
      run={realRun}
      messagesRef={messagesRef}
      input={input}
      running={
        runStatus !== 'idle' &&
        !['completed', 'failed', 'cancelled', 'waiting_user', 'superseded'].includes(runStatus) &&
        !['closed', 'exhausted'].includes(connection.state) &&
        !cancelling
      }
      disabled={loading || sending || cancelling}
      onChange={setInput}
      onSend={() => void send()}
      onStop={cancelActiveRun}
      placeholder="追问或添加自定义指令..."
    >
      {loading ? <p className={styles.systemMessage}>正在加载消息...</p> : null}
      {!loading && mappedMessages.length === 0 ? (
        <p className={styles.systemMessage}>暂无消息，发送第一条内容开始会话。</p>
      ) : null}
      {error ? (
        <div className={styles.runtimeErrorBlock}>
          <ErrorState message={error} />
          {retryAvailable ? (
            <Button disabled={retrying} onClick={retryFailed} variant="outline">
              {retrying ? '正在重试...' : '重试'}
            </Button>
          ) : null}
        </div>
      ) : null}
      {messageError && !messageToDelete ? (
        <Alert className={styles.messageMutationNotice} role="alert" variant="destructive">
          <AlertTitle>消息操作失败</AlertTitle>
          <AlertDescription>{messageError}</AlertDescription>
        </Alert>
      ) : null}
      {messageNotice ? (
        <p className={styles.messageMutationSuccess} role="status">
          {messageNotice}
        </p>
      ) : null}
      {safetyDegraded ? (
        <Alert className={styles.safetyDegradedNotice} role="status" variant="warning">
          <AlertTitle>安全降级提示</AlertTitle>
          <AlertDescription>
            当前回答基于有限数据，个人条件或外部工具未完整应用；本次结果不代表完整分析，后续追问仍会发送到当前会话。
          </AlertDescription>
        </Alert>
      ) : null}
      {connection.state === 'reconnecting' ? (
        <div className={styles.connectionNotice} role="status" aria-live="polite">
          <LoaderCircle aria-hidden="true" />
          <div>
            <strong>连接已中断，正在重新连接...</strong>
            <span>
              第 {connection.attempt} 次重连尝试 (最多 {connection.maxAttempts} 次)
            </span>
          </div>
        </div>
      ) : null}
      {connection.state === 'exhausted' ? (
        <div className={`${styles.connectionNotice} ${styles.connectionNoticeError}`} role="alert">
          <XCircle aria-hidden="true" />
          <div>
            <strong>连接重试已耗尽</strong>
            <span>如果持续失败，请刷新页面</span>
          </div>
        </div>
      ) : null}
      {cancelling ? (
        <div className={styles.connectionNotice} role="status" aria-live="polite">
          <LoaderCircle aria-hidden="true" />
          <div>
            <strong>{cancelAcknowledged ? '取消请求已确认，等待运行终态...' : '正在取消当前运行...'}</strong>
            <span>已停止接收新的运行事件，等待服务返回 cancelled 终态。</span>
          </div>
        </div>
      ) : null}
      {mappedMessages.map((message) => (
        <MessageBubble
          key={message.id}
          message={message}
          editing={editingMessageId === message.id}
          editValue={editingMessageId === message.id ? editingContent : undefined}
          editPending={messageMutation?.messageId === message.id}
          onEditChange={setEditingContent}
          onEditSave={() => {
            const source = messages.find((item) => item.message_id === message.id);
            if (source) void saveMessageEdit(source);
          }}
          onEditCancel={cancelMessageEdit}
          userActions={
            message.role === 'user' ? (
              <>
                <Button
                  aria-label="编辑消息"
                  title="编辑消息"
                  type="button"
                  variant="ghost"
                  size="icon"
                  className={styles.messageActionButton}
                  disabled={Boolean(messageMutation)}
                  onClick={() => {
                    const source = messages.find((item) => item.message_id === message.id);
                    if (source) startMessageEdit(source);
                  }}
                >
                  <Pencil aria-hidden="true" />
                </Button>
                <Button
                  aria-label="删除消息"
                  title="删除消息"
                  type="button"
                  variant="ghost"
                  size="icon"
                  className={styles.messageActionButton}
                  disabled={Boolean(messageMutation)}
                  onClick={() => {
                    const source = messages.find((item) => item.message_id === message.id);
                    if (!source) return;
                    setMessageError(undefined);
                    setMessageNotice(undefined);
                    setMessageToDelete(source);
                  }}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              </>
            ) : null
          }
        >
          {message.role === 'assistant' &&
          message.agentRunId === activeRunId &&
          runStatus === 'completed' &&
          !safetyDegraded ? (
            <CitationList citations={citations} />
          ) : null}
          {message.role === 'assistant' && message.agentRunId ? (
            <AgentFeedback runId={message.agentRunId} messageId={message.id} />
          ) : null}
        </MessageBubble>
      ))}
      {assistantText ? (
        <MessageBubble
          message={{
            id: 'assistant-stream',
            role: 'assistant',
            content: assistantText,
            time: assistantTime || new Date().toISOString(),
            agentRunId: activeRunId,
          }}
        >
          {runStatus === 'completed' && !safetyDegraded ? <CitationList citations={citations} /> : null}
          {assistantMessageId && activeRunId ? (
            <AgentFeedback runId={activeRunId} messageId={assistantMessageId} />
          ) : null}
        </MessageBubble>
      ) : null}
      {approval && activeRunId ? (
        <div className={styles.cardWrap}>
          {(() => {
            const resourceType = approval.resourceType ?? approval.details.resource_type;
            const supported = resourceType === 'food_log' || resourceType === 'meal_plan';
            return (
              <ConfirmationCard
                title={resourceType === 'meal_plan' ? '请确认保存餐食计划' : '请确认将这条内容写入饮食日志'}
                helperText={
                  resourceType === 'meal_plan'
                    ? '确认后会创建餐食计划并生成购物清单。'
                    : '确认后会创建饮食记录；取消不会修改业务数据。'
                }
                state={approvalSubmitting ? 'disabled' : supported ? 'normal' : 'error'}
                errorText="当前写入类型无法识别，请重新发送需求。"
                data={approvalData(approval.details, resourceType)}
                onConfirm={() => {
                  if (!supported) return;
                  const parameters = approvalParameters(approval.details, resourceType);
                  setApprovalSubmitting(true);
                  void confirmAgentWrite(approval.id, parameters)
                    .then(() => executeAgentWrite(approval.id, parameters))
                    .catch((reason) => setError(reason instanceof Error ? reason.message : '饮食记录写入失败'))
                    .finally(() => setApprovalSubmitting(false));
                }}
                onEdit={() => setError('请发送一条新消息修改食物和份量。')}
                onCancel={() => {
                  if (!supported) return;
                  const parameters = approvalParameters(approval.details, resourceType);
                  setApprovalSubmitting(true);
                  void rejectAgentWrite(approval.id, parameters)
                    .catch((reason) => setError(reason instanceof Error ? reason.message : '取消写入失败'))
                    .finally(() => setApprovalSubmitting(false));
                }}
              />
            );
          })()}
        </div>
      ) : null}
      {checkpointAvailable && !approval && activeRunId ? (
        <div className={styles.cardWrap}>
          <ConfirmationCard
            title="运行已暂停，可从检查点继续"
            helperText={
              checkpointRecovery
                ? '页面将转发服务端事件中的 checkpoint 元数据，由 Java 校验后创建新的 dispatch attempt。'
                : '系统已保存运行进度。继续后会创建新的 dispatch attempt，不会重复已完成的工具调用。'
            }
            confirmLabel={checkpointRecovery ? '提交恢复请求' : '从 checkpoint 恢复'}
            state={approvalSubmitting ? 'disabled' : 'normal'}
            data={[
              { label: '恢复方式', value: '从已校验 checkpoint 恢复' },
              { label: '安全校验', value: 'Java 服务端完成' },
            ]}
            onConfirm={() => {
              const recovery = checkpointRecovery;
              setApprovalSubmitting(true);
              const recoveryRequest = recovery
                ? recoverAgentRun(activeRunId, recovery)
                : recoverAgentRunFromCheckpoint(activeRunId);
              void recoveryRequest
                .then(() => {
                  setCheckpointAvailable(false);
                  setCheckpointRecovery(undefined);
                  setRunStatus('queued');
                })
                .catch((reason) => setError(reason instanceof Error ? reason.message : '运行恢复失败'))
                .finally(() => setApprovalSubmitting(false));
            }}
            onEdit={() => setError('恢复参数来自服务端 checkpoint 事件，页面不提供手工修改。')}
            onCancel={() => setCheckpointAvailable(false)}
          />
        </div>
      ) : null}
      {budgetConfirmation && activeRunId ? (
        <div className={styles.cardWrap}>
          <ConfirmationCard
            title="本次运行已达到预算上限"
            helperText="继续执行会创建新的预算 revision，并接续当前 Run。"
            confirmLabel="追加预算"
            cancelLabel="结束当前 Run"
            state={
              budgetSubmitting
                ? 'disabled'
                : budgetFacts.additionalTokens != null && budgetFacts.additionalCostCny
                  ? 'normal'
                  : 'error'
            }
            errorText="后端尚未返回可确认的追加额度，页面不会猜测 Token 或费用。"
            data={[
              {
                label: '已用 Token',
                value: budgetFacts.usedTokens == null ? '后端未返回' : budgetFacts.usedTokens.toLocaleString('en-US'),
              },
              {
                label: 'Token 上限',
                value: budgetFacts.maxTokens == null ? '后端未返回' : budgetFacts.maxTokens.toLocaleString('en-US'),
              },
              { label: '使用比例', value: budgetPercent == null ? '后端未返回' : `${budgetPercent}%` },
              {
                label: '追加 Token',
                value:
                  budgetFacts.additionalTokens == null
                    ? '后端未返回'
                    : budgetFacts.additionalTokens.toLocaleString('en-US'),
              },
              {
                label: '追加成本上限',
                value: budgetFacts.additionalCostCny ? `¥${budgetFacts.additionalCostCny}` : '后端未返回',
              },
            ]}
            onConfirm={() => {
              if (budgetFacts.additionalTokens == null || !budgetFacts.additionalCostCny || budgetSubmitting) return;
              const currentStream = streamRef.current;
              const resumeCursor = currentStream?.getConnection().lastEventId ?? connection.lastEventId;
              // 预算追加会创建新的 dispatch attempt，先关闭旧连接，再从原游标接收新 Run 事件。
              currentStream?.close();
              if (streamRef.current === currentStream) streamRef.current = undefined;
              streamResumeRef.current = { lastEventId: resumeCursor, preserveContent: true };
              setBudgetSubmitting(true);
              void extendAgentRunBudget(activeRunId, budgetFacts.additionalTokens, budgetFacts.additionalCostCny)
                .then(() => {
                  if (!mountedRef.current) return;
                  setBudgetConfirmation(false);
                  setRunStatus('queued');
                  setStreamGeneration((current) => current + 1);
                })
                .catch((reason) => {
                  if (!mountedRef.current) return;
                  setError(reason instanceof Error ? reason.message : '预算追加失败');
                  setStreamGeneration((current) => current + 1);
                })
                .finally(() => {
                  if (mountedRef.current) setBudgetSubmitting(false);
                });
            }}
            onEdit={() => setError('追加额度由后端返回，页面不能修改。')}
            onCancel={cancelActiveRun}
          />
        </div>
      ) : null}
      <Dialog
        open={Boolean(messageToDelete)}
        onOpenChange={(open) => {
          if (!open && !messageMutation) {
            setMessageToDelete(undefined);
            setMessageError(undefined);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除这条消息？</DialogTitle>
            <DialogDescription>删除后消息会从当前会话中移除，后端不会复用原消息顺序号。</DialogDescription>
          </DialogHeader>
          <p className={styles.messageDeleteQuote}>{messageToDelete?.content}</p>
          {messageError ? (
            <Alert className={styles.messageMutationNotice} role="alert" variant="destructive">
              <AlertTitle>消息操作失败</AlertTitle>
              <AlertDescription>{messageError}</AlertDescription>
            </Alert>
          ) : null}
          <DialogFooter>
            <Button
              variant="outline"
              type="button"
              disabled={messageMutation?.action === 'delete'}
              onClick={() => setMessageToDelete(undefined)}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              type="button"
              disabled={messageMutation?.action === 'delete'}
              onClick={() => void confirmMessageDelete()}
            >
              {messageMutation?.action === 'delete' ? (
                <LoaderCircle className="animate-spin" aria-hidden="true" />
              ) : (
                <Trash2 aria-hidden="true" />
              )}
              删除消息
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </ChatSurface>
  );
}

function ChatRunPage() {
  const { session_id: sessionId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const messagesRef = useRef<HTMLDivElement>(null);
  const onSessionCreated = useCallback(
    (createdSessionId: string) => {
      navigate(`/chat/${encodeURIComponent(createdSessionId)}?transport=chat-run`, { replace: true });
    },
    [navigate],
  );
  const agent = useRealAgentReplay(true, sessionId, searchParams.get('prompt'), { onSessionCreated });

  useEffect(() => {
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [agent.messages, agent.assistantText]);

  return (
    <ChatSurface
      run={agent.run}
      messagesRef={messagesRef}
      input={agent.input}
      running={agent.running}
      disabled={agent.loading || agent.cancelling}
      onChange={agent.setInput}
      onSend={() => void agent.send()}
      onStop={agent.stop}
      showKnowledgeTopNav
      placeholder="追问或添加自定义指令..."
    >
      <span className={styles.srOnly} data-chat-transport="chat-run">
        ChatRun 兼容模式
      </span>
      {agent.loading ? <p className={styles.systemMessage}>正在加载 ChatRun 消息...</p> : null}
      {!agent.loading && agent.messages.length === 0 ? (
        <p className={styles.systemMessage}>暂无消息，发送第一条内容开始会话。</p>
      ) : null}
      {agent.error ? (
        <div className={styles.runtimeErrorBlock}>
          <ErrorState message={agent.error} />
          {agent.run.connection?.state === 'exhausted' ? (
            <Button variant="outline" type="button" onClick={agent.reconnect}>
              <RefreshCw aria-hidden="true" />
              重新连接
            </Button>
          ) : null}
        </div>
      ) : null}
      {agent.run.connection?.state === 'connecting' ? (
        <div className={styles.connectionNotice} role="status" aria-live="polite">
          <LoaderCircle aria-hidden="true" />
          <span>正在连接 ChatRun 实时事件...</span>
        </div>
      ) : null}
      {agent.run.connection?.state === 'reconnecting' ? (
        <div className={styles.connectionNotice} role="status" aria-live="polite">
          <LoaderCircle aria-hidden="true" />
          <div>
            <strong>连接已中断，正在重新连接...</strong>
            <span>
              第 {agent.run.connection?.attempt} 次重连尝试（最多 {agent.run.connection?.maxAttempts} 次）
            </span>
          </div>
        </div>
      ) : null}
      {agent.cancelling ? (
        <div className={styles.connectionNotice} role="status" aria-live="polite">
          <LoaderCircle aria-hidden="true" />
          <div>
            <strong>{agent.cancelAcknowledged ? '取消请求已确认，等待运行终态...' : '正在取消当前运行...'}</strong>
            <span>取消请求已接受，仍需等待 ChatRun 的 cancelled 事件。</span>
          </div>
        </div>
      ) : null}
      {agent.messages.map((message) => (
        <MessageBubble key={message.id} message={message} />
      ))}
      {agent.assistantText ? (
        <MessageBubble
          message={{
            id: `chat-run-answer-${agent.activeRunId ?? 'pending'}`,
            role: 'assistant',
            content: agent.assistantText,
            time: agent.assistantTime || new Date().toISOString(),
          }}
        />
      ) : null}
    </ChatSurface>
  );
}

function MockChatPage() {
  const params = useParams();
  const sessionId = params.session_id;
  const [searchParams] = useSearchParams();
  const isFigmaFixture = searchParams.get('state') === 'figma-v2';
  const agent = useAgentReplay(sessionId, searchParams.get('prompt'));
  const messagesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Figma 默认态需要保留画板的初始构图，让用户消息在紧凑视口中保持可见。
    // 普通 Mock 会话仍然跟随最新消息滚动，真实模式由独立的消息流逻辑负责滚动。
    if (isFigmaFixture) return;
    messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' });
  }, [agent.messages, agent.card, isFigmaFixture]);

  return (
    <ChatSurface
      run={agent.run}
      messagesRef={messagesRef}
      input={agent.input}
      running={agent.running}
      designChat={isFigmaFixture}
      fixtureVariant={isFigmaFixture ? 'chat' : undefined}
      displayNameOverride={isFigmaFixture ? 'Anddy' : undefined}
      profileIdOverride={isFigmaFixture ? '1234567' : undefined}
      showKnowledgeTopNav={!isFigmaFixture}
      pageVariant={isFigmaFixture ? 'figma-default' : undefined}
      sidebarAvatarSrc={isFigmaFixture ? FIXTURE_CHAT_ACCOUNT_AVATAR : undefined}
      topAvatarSrc={isFigmaFixture ? FIXTURE_CHAT_ACCOUNT_AVATAR : undefined}
      onChange={agent.setInput}
      onSend={() => agent.send()}
      onStop={agent.stop}
      placeholder="追问或添加自定义指令..."
    >
      {agent.messages.map((message, index) => (
        <MessageBubble
          key={message.id}
          message={{ ...message, wide: isFigmaFixture }}
          userAvatarSrc={isFigmaFixture ? FIXTURE_CHAT_ACCOUNT_AVATAR : undefined}
          userAvatarGender={isFigmaFixture ? FIXTURE_CHAT_AVATAR_GENDERS.defaultMessage : undefined}
        >
          {index === agent.messages.length - 1 && agent.card.type === 'confirmation' ? (
            <InlineConfirmationCard onConfirm={agent.confirmWrite} onCancel={agent.cancelWrite} />
          ) : null}
        </MessageBubble>
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
      {agent.card.type === 'confirmation' ? null : null}
      {agent.card.type === 'error' ? <ErrorState message={agent.card.message} /> : null}
      {isFigmaFixture ? <MessageActionsPanel /> : null}
    </ChatSurface>
  );
}
