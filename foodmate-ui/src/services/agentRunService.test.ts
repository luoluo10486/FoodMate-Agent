import { afterEach, describe, expect, it, vi } from 'vitest';
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
}

describe('openAgentRunStream', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    FakeEventSource.instances = [];
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
    second.emit('run.completed', { sse_event_id: 'evt-2', answer: '完成' });

    expect(received).toEqual(['run.answer_stream:部分文本:evt-1', 'run.completed::evt-2']);
    expect(second.closed).toBe(true);
    expect(stream.getConnection().state).toBe('closed');
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
});
