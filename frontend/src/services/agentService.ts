/**
 * Agent 交互服务 — 当前转发 mock Hook，后续替换为真实 SSE 调用。
 */
import { useMockAgentReplay } from '../mock/agentReplay';
import { useRealAgentReplay } from './realAgentService';

/**
 * 当前使用 mock 实现，后续替换为真实 API 版本的 Hook。
 */
export function useAgentReplay(seedKey?: string, seedPrompt?: string | null) {
  const mock = useMockAgentReplay(seedKey, seedPrompt);
  const real = useRealAgentReplay(import.meta.env.VITE_AGENT_MODE === 'real', seedKey, seedPrompt);
  return import.meta.env.VITE_AGENT_MODE === 'real' ? real : mock;
}

export type AgentReplayState = ReturnType<typeof useAgentReplay>;
