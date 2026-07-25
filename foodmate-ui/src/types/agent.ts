export type AgentDisplayStatus =
  | 'routing'
  | 'planning'
  | 'retrieving'
  | 'executing_tools'
  | 'validating'
  | 'composing'
  | 'waiting_user'
  | 'completed'
  | 'failed'
  | 'cancelled';

export type ToolCallStatus = 'pending' | 'running' | 'success' | 'failed' | 'timeout' | 'cancelled';

export type ToolCall = {
  id: string;
  name: string;
  displayName: string;
  status: ToolCallStatus;
  latencyMs?: number;
  summary: string;
  error?: string;
  input?: string;
  output?: string;
};

export type Citation = {
  id: string;
  title: string;
  snippet: string;
  source: string;
  score?: number;
};

export type AgentRunView = {
  id: string;
  status: AgentDisplayStatus;
  intent: 'calculation' | 'record' | 'analysis' | 'planning' | 'knowledge_qna';
  toolsUsed: number;
  toolsTotal: number;
  agentsUsed: number;
  agentsTotal: number;
  toolCalls: ToolCall[];
  citations: Citation[];
};
