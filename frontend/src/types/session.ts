import type { AgentDisplayStatus } from './agent';

export type SessionSummary = {
  id: string;
  title: string;
  subtitle: string;
  pinned?: boolean;
  active?: boolean;
  status?: AgentDisplayStatus;
};

export type Message = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  time: string;
};
