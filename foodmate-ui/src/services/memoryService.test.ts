import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { confirmMemory, deleteMemory, loadMemories, updateMemory } from './memoryService';

describe('memoryService', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('forwards the cancellation signal to memory reads and mutations', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: {} }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: {} }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();

    await loadMemories(controller.signal);
    await confirmMemory(1, controller.signal);
    await updateMemory(1, '新的记忆', 'user', controller.signal);
    await deleteMemory(1, controller.signal);

    expect(fetchMock).toHaveBeenCalledTimes(4);
    for (const [, init] of fetchMock.mock.calls) expect(init.signal).toBe(controller.signal);
  });

  it('wraps plain text edits in the JSON object required by the backend', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { memory_id: 1, memory_value: JSON.stringify({ value: '偏好燕麦' }) },
        }),
        { status: 200 },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await updateMemory(1, '  偏好燕麦  ', 'user');

    const requestBody = JSON.parse(String(fetchMock.mock.calls[0][1].body)) as {
      memoryValue: string;
      scope: string;
    };
    expect(JSON.parse(requestBody.memoryValue)).toEqual({ value: '偏好燕麦' });
    expect(requestBody.scope).toBe('user');
  });

  it('preserves an object-shaped JSON edit instead of nesting it again', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ success: true, data: {} }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await updateMemory(1, '{"value":"低盐","unit":"g"}');

    const requestBody = JSON.parse(String(fetchMock.mock.calls[0][1].body)) as { memoryValue: string };
    expect(JSON.parse(requestBody.memoryValue)).toEqual({ value: '低盐', unit: 'g' });
  });
});
