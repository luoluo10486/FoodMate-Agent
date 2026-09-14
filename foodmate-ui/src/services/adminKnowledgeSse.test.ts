import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { streamKnowledgeBatch, type KnowledgeBatchEvent } from './adminService';

type EventListener = (event: Event) => void;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readonly listeners = new Map<string, Set<EventListener>>();
  closed = false;
  onerror: ((event: Event) => void) | null = null;
  onopen: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: EventListener) {
    const listeners = this.listeners.get(type) ?? new Set<EventListener>();
    listeners.add(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type: string, listener: EventListener) {
    this.listeners.get(type)?.delete(listener);
  }

  open() {
    this.onopen?.();
  }

  emit(type: string, payload: unknown, lastEventId: string) {
    const event = new MessageEvent(type, { data: JSON.stringify(payload), lastEventId });
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }

  fail() {
    this.onerror?.(new Event('error'));
  }

  close() {
    this.closed = true;
  }
}

describe('admin knowledge batch SSE', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('uses the shared cursor, removes duplicate events, and closes on terminal progress', async () => {
    const events: KnowledgeBatchEvent[] = [];
    const states: string[] = [];
    const stream = streamKnowledgeBatch('9001', (event) => events.push(event), {
      reconnectDelayMs: 10,
      onStateChange: (connection) => states.push(connection.state),
    });

    const first = FakeEventSource.instances[0];
    expect(first.url).toBe('/api/admin/knowledge-upload-batches/9001/events');
    first.open();
    first.emit('knowledge.batch.progress', { status: 'indexing' }, 'event-1');
    first.emit('knowledge.batch.progress', { status: 'indexing' }, 'event-1');
    expect(events).toHaveLength(1);

    first.fail();
    await vi.advanceTimersByTimeAsync(10);
    const second = FakeEventSource.instances[1];
    expect(second.url).toContain('lastEventId=event-1');
    second.open();
    second.emit('knowledge.batch.progress', { status: 'completed' }, 'event-2');

    expect(events).toHaveLength(2);
    expect(events[1]).toMatchObject({ event_id: 'event-2', event_type: 'knowledge.batch.progress' });
    expect(stream.getConnection()).toMatchObject({ state: 'closed', lastEventId: 'event-2' });
    expect(second.closed).toBe(true);
    expect(states).toContain('reconnecting');
    stream.close();
  });

  it('enters exhausted state after the configured retry limit', async () => {
    const stream = streamKnowledgeBatch('9001', () => undefined, {
      maxAttempts: 2,
      reconnectDelayMs: 0,
    });

    FakeEventSource.instances[0].fail();
    await vi.advanceTimersByTimeAsync(0);
    FakeEventSource.instances[1].fail();

    expect(stream.getConnection()).toMatchObject({ state: 'exhausted', attempt: 2, maxAttempts: 2 });
    stream.close();
  });

  it('deduplicates batch events by payload ids even when the SSE message id changes', () => {
    const events: KnowledgeBatchEvent[] = [];
    const stream = streamKnowledgeBatch('9001', (event) => events.push(event));
    const source = FakeEventSource.instances[0];

    source.emit(
      'knowledge.batch.progress',
      { event_id: 'business-id', sse_event_id: 'stream-id', status: 'indexing' },
      'message-1',
    );
    source.emit(
      'knowledge.batch.progress',
      { event_id: 'business-id', sse_event_id: 'stream-id', status: 'indexing' },
      'message-2',
    );

    expect(events).toHaveLength(1);
    expect(stream.getConnection().lastEventId).toBe('message-2');
    stream.close();
  });

  it('closes the stream and prevents reconnect after cancellation', async () => {
    const controller = new AbortController();
    const stream = streamKnowledgeBatch('9001', () => undefined, {
      signal: controller.signal,
      reconnectDelayMs: 10,
    });
    const source = FakeEventSource.instances[0];

    controller.abort();
    expect(source.closed).toBe(true);
    expect(stream.getConnection()).toMatchObject({ state: 'closed' });

    source.fail();
    await vi.advanceTimersByTimeAsync(20);
    expect(FakeEventSource.instances).toHaveLength(1);
    stream.close();
  });
});
