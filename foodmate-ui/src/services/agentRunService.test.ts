import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AgentStreamConnection } from '../types/agent';
import { openAgentRunStream } from './agentRunService';

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readonly listeners = new Map<string, Array<(event: Event) => void>>();
  readonly url: string;
  closed = false;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close() {
    this.closed = true;
  }

  open() {
    this.onopen?.();
  }

  fail() {
    this.onerror?.();
  }

  emit(type: string, payload: object, lastEventId = '') {
    const event = new MessageEvent(type, { data: JSON.stringify(payload), lastEventId });
    for (const listener of this.listeners.get(type) ?? []) listener(event);
  }

  emitRaw(type: string, data: string, lastEventId = '') {
    const event = new MessageEvent(type, { data, lastEventId });
    for (const listener of this.listeners.get(type) ?? []) listener(event);
  }
}

describe('openAgentRunStream', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    FakeEventSource.instances = [];
  });

  it('starts a resumed stream with the persisted last event id', () => {
    vi.stubGlobal('EventSource', FakeEventSource);
    const stream = openAgentRunStream('42', () => undefined, { lastEventId: 'evt-before' });

    expect(FakeEventSource.instances[0].url).toContain('lastEventId=evt-before');
    expect(stream.getConnection()).toMatchObject({ state: 'connecting', lastEventId: 'evt-before' });

    stream.close();
  });

  it('reconnects with the last sse_event_id and deduplicates replayed events', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const states: string[] = [];
    const stream = openAgentRunStream(
      '42',
      (eventType, payload, eventId) => received.push(`${eventType}:${payload.text ?? ''}:${eventId}`),
      {
        reconnectDelayMs: 10,
        maxAttempts: 3,
        onStateChange: (connection) => states.push(`${connection.state}:${connection.attempt}`),
      },
    );

    const first = FakeEventSource.instances[0];
    first.open();
    first.emit('run.answer_stream', { sse_event_id: 'evt-1', text: '部分文本' });
    first.emit('run.answer_stream', { sse_event_id: 'evt-1', text: '部分文本' });
    first.fail();
    expect(states).toContain('reconnecting:2');

    vi.advanceTimersByTime(10);
    const second = FakeEventSource.instances[1];
    expect(second.url).toContain('lastEventId=evt-1');
    second.open();
    second.emit('run.answer_stream', { sse_event_id: 'evt-1', text: '重放文本' });
    second.emit('run.completed', { sse_event_id: 'evt-2', answer: '完成' });

    expect(received).toEqual(['run.answer_stream:部分文本:evt-1', 'run.completed::evt-2']);
    expect(second.closed).toBe(true);
    expect(stream.getConnection().state).toBe('closed');
  });

  it('uses the SSE message id as the resume cursor and ignores duplicate payload ids', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const stream = openAgentRunStream('42', (_eventType, _payload, eventId) => received.push(eventId), {
      reconnectDelayMs: 10,
      maxAttempts: 2,
    });

    const first = FakeEventSource.instances[0];
    first.emit('run.answer_stream', { sse_event_id: 'payload-id', text: '第一段' }, 'message-id');
    first.emit('run.answer_stream', { sse_event_id: 'another-payload-id', text: '重复事件' }, 'message-id');
    first.fail();
    vi.advanceTimersByTime(10);

    expect(received).toEqual(['message-id']);
    expect(FakeEventSource.instances[1].url).toContain('lastEventId=message-id');
    expect(stream.getConnection().lastEventId).toBe('message-id');
    stream.close();
  });

  it('deduplicates an event when any payload id matches and advances the cursor', () => {
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const stream = openAgentRunStream('42', (_eventType, _payload, eventId) => received.push(eventId));
    const source = FakeEventSource.instances[0];

    source.emit(
      'run.answer_stream',
      { sse_event_id: 'stream-id', event_id: 'business-id', text: '第一段' },
      'message-id-1',
    );
    source.emit(
      'run.answer_stream',
      { sse_event_id: 'stream-id', event_id: 'business-id', text: '重复事件' },
      'message-id-2',
    );

    expect(received).toEqual(['message-id-1']);
    expect(stream.getConnection().lastEventId).toBe('message-id-2');
    stream.close();
  });

  it('publishes the latest event cursor through the connection callback', () => {
    vi.stubGlobal('EventSource', FakeEventSource);
    const states: AgentStreamConnection[] = [];
    openAgentRunStream('42', () => undefined, {
      onStateChange: (connection) => states.push(connection),
    });

    const source = FakeEventSource.instances[0];
    source.open();
    source.emit('run.answer_stream', { sse_event_id: 'evt-7', text: '部分文本' });

    expect(states.at(-1)).toMatchObject({ state: 'connected', lastEventId: 'evt-7' });
  });

  it('forwards cancel acknowledgement without treating it as a terminal event', () => {
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const stream = openAgentRunStream('42', (eventType, payload, eventId) =>
      received.push(`${eventType}:${payload.reason ?? ''}:${eventId}`),
    );
    const source = FakeEventSource.instances[0];

    source.emit(
      'run.cancel_acknowledged',
      { event_type: 'run.cancel_acknowledged', reason: 'user_requested' },
      'ack-1',
    );

    expect(received).toEqual(['run.cancel_acknowledged:user_requested:ack-1']);
    expect(source.closed).toBe(false);

    stream.close();
  });

  it('ignores a delayed error from an obsolete EventSource', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const stream = openAgentRunStream('42', () => undefined, { reconnectDelayMs: 10, maxAttempts: 3 });
    const first = FakeEventSource.instances[0];

    first.fail();
    vi.advanceTimersByTime(10);
    const second = FakeEventSource.instances[1];
    first.fail();
    vi.advanceTimersByTime(10);

    expect(FakeEventSource.instances).toHaveLength(2);
    second.fail();
    expect(stream.getConnection().attempt).toBe(3);

    stream.close();
  });

  it('ignores delayed messages from an obsolete EventSource', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const stream = openAgentRunStream('42', (eventType) => received.push(eventType), {
      reconnectDelayMs: 10,
      maxAttempts: 3,
    });
    const first = FakeEventSource.instances[0];

    first.fail();
    vi.advanceTimersByTime(10);
    const second = FakeEventSource.instances[1];
    first.emit('run.answer_stream', { text: '旧连接文本' }, 'old-event');
    second.emit('run.answer_stream', { text: '新连接文本' }, 'new-event');

    expect(received).toEqual(['run.answer_stream']);

    stream.close();
  });

  it('reconnects after a malformed SSE payload', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const states: string[] = [];
    const stream = openAgentRunStream('42', () => undefined, {
      reconnectDelayMs: 10,
      maxAttempts: 2,
      onStateChange: (connection) => states.push(connection.state),
    });
    const first = FakeEventSource.instances[0];

    first.emitRaw('run.answer_stream', 'not-json');
    expect(states).toContain('reconnecting');

    vi.advanceTimersByTime(10);
    expect(FakeEventSource.instances).toHaveLength(2);

    stream.close();
  });

  it.each(['run.failed', 'run.cancelled', 'run.superseded'])(
    'closes the stream for %s terminal events',
    (eventType) => {
      vi.useFakeTimers();
      vi.stubGlobal('EventSource', FakeEventSource);
      const received: string[] = [];
      const stream = openAgentRunStream('42', (receivedType) => received.push(receivedType), {
        reconnectDelayMs: 10,
        maxAttempts: 2,
      });

      const source = FakeEventSource.instances[0];
      source.emit(eventType, { event_type: eventType, sse_event_id: `${eventType}-id` });
      source.fail();
      vi.advanceTimersByTime(20);

      expect(received).toEqual([eventType]);
      expect(source.closed).toBe(true);
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(stream.getConnection()).toMatchObject({
        state: 'closed',
        lastEventId: `${eventType}-id`,
      });
    },
  );

  it('closes a generic run.event when its payload reports a terminal status', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const stream = openAgentRunStream('42', (eventType) => received.push(eventType), {
      reconnectDelayMs: 10,
      maxAttempts: 2,
    });
    const source = FakeEventSource.instances[0];

    source.emit('run.event', { event_type: 'run.event', status: 'failed', event_id: 'failed-event' });
    source.fail();
    vi.advanceTimersByTime(20);

    expect(received).toEqual(['run.event']);
    expect(source.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(stream.getConnection()).toMatchObject({ state: 'closed', lastEventId: 'failed-event' });
  });

  it('closes the stream when a generic run.event reports a terminal state', () => {
    vi.stubGlobal('EventSource', FakeEventSource);
    const received: string[] = [];
    const stream = openAgentRunStream('42', (eventType) => received.push(eventType));
    const source = FakeEventSource.instances[0];

    source.emit('run.event', { state: 'FAILED', event_id: 'failed-state', payload: { error_code: 'TIMEOUT' } });
    source.fail();

    expect(received).toEqual(['run.event']);
    expect(source.closed).toBe(true);
    expect(stream.getConnection()).toMatchObject({ state: 'closed', lastEventId: 'failed-state' });
  });

  it('enters exhausted after the bounded number of attempts', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const states: string[] = [];
    openAgentRunStream('42', () => undefined, {
      reconnectDelayMs: 5,
      maxAttempts: 2,
      onStateChange: (connection) => states.push(connection.state),
    });

    FakeEventSource.instances[0].fail();
    vi.advanceTimersByTime(5);
    FakeEventSource.instances[1].fail();

    expect(states).toContain('reconnecting');
    expect(states.at(-1)).toBe('exhausted');
  });

  it('publishes a single initial connecting state and closes without scheduling a reconnect', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const states: string[] = [];
    const stream = openAgentRunStream('42', () => undefined, {
      reconnectDelayMs: 5,
      onStateChange: (connection) => states.push(`${connection.state}:${connection.attempt}`),
    });

    expect(states).toEqual(['connecting:1']);
    const source = FakeEventSource.instances[0];
    stream.close();
    source.fail();
    vi.advanceTimersByTime(10);

    expect(source.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(states.at(-1)).toBe('closed:1');
  });

  it('closes the stream and cancels pending reconnect when the lifecycle signal aborts', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const controller = new AbortController();
    const states: string[] = [];
    const stream = openAgentRunStream('42', () => undefined, {
      signal: controller.signal,
      reconnectDelayMs: 10,
      onStateChange: (connection) => states.push(connection.state),
    });

    const source = FakeEventSource.instances[0];
    source.fail();
    controller.abort();
    vi.advanceTimersByTime(20);

    expect(source.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(stream.getConnection().state).toBe('closed');
    expect(states.at(-1)).toBe('closed');
  });
});
