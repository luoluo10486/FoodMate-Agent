import { afterEach, describe, expect, it, vi } from 'vitest';
import { cancelChatRun, createChatRun } from './chatApi';

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), { status: 200 });
}

describe('chatApi HTTP contract', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('normalizes the two backend naming styles used by Chat cancellation', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok({ run_id: '42', dispatch_id: 'dsp-1', status: 'DISPATCHED', duplicate: false }))
      .mockResolvedValueOnce(ok({ runId: '42', status: 'requested', terminal: false }))
      .mockResolvedValueOnce(ok({ commandId: 'cmd-1', runId: '42', status: 'CANCELED', duplicate: true }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(createChatRun('记录午餐')).resolves.toMatchObject({ run_id: '42' });
    await expect(cancelChatRun('42')).resolves.toEqual({ run_id: '42', status: 'requested', terminal: false });
    await expect(cancelChatRun('42')).resolves.toEqual({
      run_id: '42',
      status: 'CANCELED',
      command_id: 'cmd-1',
      duplicate: true,
    });

    expect(JSON.parse(String(fetchMock.mock.calls[1][1].body))).toEqual({ reason: 'user_cancelled' });
  });
});
