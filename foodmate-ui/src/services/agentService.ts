/**
 * Agent 交互服务 — 根据运行模式选择本地预览回放或真实 SSE Hook。
 */
import { useMockAgentReplay } from '../mock/agentReplay';
import { useRealAgentReplay } from './realAgentService';

/**
 * 预览模式保留确定性回放；真实模式由 RealAgentReplay 负责 API/SSE 生命周期。
 */
export function useAgentReplay(seedKey?: string, seedPrompt?: string | null) {
  const mock = useMockAgentReplay(seedKey, seedPrompt);
  const real = useRealAgentReplay(import.meta.env.VITE_AGENT_MODE === 'real', seedKey, seedPrompt);
  return import.meta.env.VITE_AGENT_MODE === 'real' ? real : mock;
}

export type AgentReplayState = ReturnType<typeof useAgentReplay>;
