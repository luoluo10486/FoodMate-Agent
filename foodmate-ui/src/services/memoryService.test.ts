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
});
