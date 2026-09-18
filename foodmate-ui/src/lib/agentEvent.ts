type AgentEventRecord = Record<string, unknown>;

const terminalEventTypes = new Set(['run.completed', 'run.failed', 'run.cancelled', 'run.superseded']);

const terminalStatusEventTypes: Record<string, string> = {
  completed: 'run.completed',
  succeeded: 'run.completed',
  success: 'run.completed',
  failed: 'run.failed',
  error: 'run.failed',
  cancelled: 'run.cancelled',
  canceled: 'run.cancelled',
  superseded: 'run.superseded',
};

function recordValue(value: unknown): AgentEventRecord | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as AgentEventRecord) : undefined;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : value == null ? '' : String(value);
}

function terminalEventTypeFromStatus(value: unknown): string | undefined {
  return terminalStatusEventTypes[stringValue(value).trim().toLowerCase()];
}

/** 将兼容流的 run.event 外层状态映射为页面可处理的标准终态事件。 */
export function resolveAgentEventType(eventType: string | undefined, payload: AgentEventRecord): string {
  const registeredType = stringValue(eventType).trim().toLowerCase();
  const payloadType = stringValue(payload.event_type).trim().toLowerCase();
  const nestedPayload = recordValue(payload.payload);
  const nestedType = stringValue(nestedPayload?.event_type).trim().toLowerCase();
  const explicitType = [payloadType, nestedType, registeredType].find((value) => value && value !== 'run.event');
  if (explicitType) return explicitType;

  return (
    terminalEventTypeFromStatus(payload.status) ??
    terminalEventTypeFromStatus(payload.state) ??
    terminalEventTypeFromStatus(nestedPayload?.status) ??
    terminalEventTypeFromStatus(nestedPayload?.state) ??
    (registeredType || 'run.event')
  );
}

/** 合并兼容流的外层信封和内层业务载荷，避免终态字段被嵌套后丢失。 */
export function flattenAgentEventPayload(payload: AgentEventRecord): AgentEventRecord {
  const nestedPayload = recordValue(payload.payload);
  return nestedPayload ? { ...payload, ...nestedPayload } : payload;
}

export function isTerminalAgentEvent(eventType: string | undefined, payload: AgentEventRecord): boolean {
  const normalizedType = resolveAgentEventType(eventType, payload);
  if (terminalEventTypes.has(normalizedType)) return true;

  const nestedPayload = recordValue(payload.payload);
  return Boolean(
    terminalEventTypeFromStatus(payload.status) ||
    terminalEventTypeFromStatus(payload.state) ||
    terminalEventTypeFromStatus(nestedPayload?.status) ||
    terminalEventTypeFromStatus(nestedPayload?.state),
  );
}
