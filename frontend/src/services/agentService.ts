/**
 * Agent 交互服务 — 当前转发 mock Hook，后续替换为真实 SSE 调用。
 */
import { useMockAgentReplay } from '../mock/agentReplay';

/**
 * 当前使用 mock 实现，后续替换为真实 API 版本的 Hook。
 */
export function useAgentReplay(seedKey?: string, seedPrompt?: string | null) {
  return useMockAgentReplay(seedKey, seedPrompt);
}

export type AgentReplayState = ReturnType<typeof useAgentReplay>;
