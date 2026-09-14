import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  deleteMessage,
  loadSessionMessages,
  loadSessionMessagesPage,
  loadSessions,
  loadSessionsPage,
  searchSessions,
  updateMessage,
} from './sessionService';

describe('sessionService', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('preserves session pagination and filtering parameters', async () => {
    const data = { items: [], total: 0, page: 2, size: 10 };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadSessionsPage({ page: 2, size: 10, query: '早餐', status: 'active' })).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions?page=2&size=10&q=%E6%97%A9%E9%A4%90&status=active',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });

  it('maps real sessions to the workspace summary shape', async () => {
    const data = {
      items: [
        { session_id: '7', title: '本周计划', mode: 'agent', status: 'active' },
        { session_id: '8', title: '历史记录', mode: 'chat', status: 'archived' },
      ],
      total: 2,
      page: 1,
      size: 50,
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadSessions()).resolves.toEqual([
      { id: '7', title: '本周计划', subtitle: 'agent', active: true, status: 'active' },
      { id: '8', title: '历史记录', subtitle: 'chat', active: false, status: 'archived' },
    ]);
  });

  it('supports paginated message reads and message mutations', async () => {
    const message = {
      message_id: '99',
      session_id: '7',
      role: 'user',
      content: '更新后的内容',
      sequence_no: 1,
      created_at: '2026-09-13T09:00:00Z',
    };
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({ success: true, data: { ...message, items: [message], total: 1, page: 3, size: 20 } }),
            { status: 200 },
          ),
        ),
      );
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadSessionMessagesPage('7', { page: 3, size: 20 })).resolves.toMatchObject({ page: 3, size: 20 });
    await updateMessage('7', '99', '更新后的内容');
    await deleteMessage('7', '99');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/sessions/7/messages?page=3&size=20');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/sessions/7/messages/99');
    expect(JSON.parse(String(fetchMock.mock.calls[1][1].body))).toEqual({ content: '更新后的内容' });
    expect(fetchMock.mock.calls[2][0]).toBe('/api/sessions/7/messages/99');
    expect(fetchMock.mock.calls[2][1].method).toBe('DELETE');
  });

  it('loads every message page and restores sequence order', async () => {
    const firstPage = {
      items: [
        {
          message_id: 'message-2',
          session_id: '7',
          role: 'assistant' as const,
          content: '第二条',
          sequence_no: 2,
          created_at: '2026-09-13T09:00:02Z',
        },
      ],
      total: 2,
      page: 1,
      size: 1,
    };
    const secondPage = {
      items: [
        {
          message_id: 'message-1',
          session_id: '7',
          role: 'user' as const,
          content: '第一条',
          sequence_no: 1,
          created_at: '2026-09-13T09:00:01Z',
        },
      ],
      total: 2,
      page: 2,
      size: 1,
    };
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      const data = url.includes('page=2') ? secondPage : firstPage;
      return Promise.resolve(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadSessionMessagesPage('7', { page: 1, size: 1 })).resolves.toEqual(firstPage);
    const messages = await loadSessionMessages('7', { page: 1, size: 1 });

    expect(messages.map((message) => message.sequence_no)).toEqual([1, 2]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/7/messages?page=2&size=1',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });

  it('passes search pagination to the backend search endpoint', async () => {
    const data = {
      items: [{ session_id: '7', title: '早餐', snippet: '燕麦' }],
      total: 3,
      page: 2,
      size: 15,
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(searchSessions(' 早餐 ', { page: 2, size: 15 })).resolves.toEqual({
      items: [{ id: '7', title: '早餐', subtitle: '燕麦' }],
      total: 3,
      page: 2,
      size: 15,
    });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/sessions/search?q=%E6%97%A9%E9%A4%90&page=2&size=15');
  });

  it('passes a cancellation signal to session list requests', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { items: [], total: 0, page: 1, size: 50 } }), {
        status: 200,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await loadSessionsPage({ page: 1, size: 50 }, controller.signal);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions?page=1&size=50',
      expect.objectContaining({ signal: controller.signal }),
    );
  });
});
