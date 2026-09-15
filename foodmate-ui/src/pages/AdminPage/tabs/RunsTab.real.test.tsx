import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let RunsSection: typeof import('./RunsTab').RunsSection;

describe('RunsSection real DLQ view', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    ({ RunsSection } = await import('./RunsTab'));
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((input: RequestInfo | URL) => {
        const path = new URL(String(input), 'http://foodmate.local').pathname;
        const items = path.endsWith('/dlq')
          ? [
              {
                dlq_id: 21,
                consumer_group: 'foodmate-java-agent-event-v1',
                source_topic: 'foodmate-agent-event-v1',
                message_id: 'mq-21',
                run_id: '42',
                dispatch_id: 'dispatch-42',
                event_id: 'event-42',
                attempt: 2,
                reconsume_times: 8,
                error_code: 'RUNTIME_MESSAGE_DEAD_LETTERED',
                reconciliation_state: 'needs_attention',
                first_seen_at: '2026-08-23T00:00:00Z',
                reconciled_at: null,
              },
            ]
          : [];
        return Promise.resolve(
          new Response(JSON.stringify({ success: true, data: { items, total: items.length, page: 1, size: 100 } }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        );
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('loads safe DLQ summaries without rendering payload fields', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/runs']}>
        <RunsSection />
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('tab', { name: 'DLQ' }));
    expect(await screen.findByText('mq-21')).toBeInTheDocument();
    expect(screen.getByText('RUNTIME_MESSAGE_DEAD_LETTERED')).toBeInTheDocument();
    expect(screen.getByText('needs_attention')).toBeInTheDocument();
    expect(screen.queryByText('raw_payload_json')).not.toBeInTheDocument();
  });

  it('exposes DLQ replay only to superadmin and passes the selected message to the admin action flow', async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    render(
      <MemoryRouter initialEntries={['/admin/runs']}>
        <RunsSection onAction={onAction} canReplayDlq />
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('tab', { name: 'DLQ' }));
    const replayButton = await screen.findByRole('button', { name: '重放' });
    await user.click(replayButton);

    expect(onAction).toHaveBeenCalledWith(
      expect.objectContaining({
        action: '重放 DLQ 消息',
        targetLabel: 'mq-21',
        targetType: 'dlq',
        targetId: '21',
        execute: expect.any(Function),
      }),
    );
  });

  it('does not expose a replay button to non-superadmin roles', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/runs']}>
        <RunsSection />
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('tab', { name: 'DLQ' }));
    expect(await screen.findByText('仅 superadmin')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '重放' })).not.toBeInTheDocument();
  });

  it('运行治理页面卸载时取消当前查询', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
        capturedSignal = init?.signal ?? undefined;
        return new Promise<Response>(() => undefined);
      }),
    );

    const view = render(
      <MemoryRouter initialEntries={['/admin/runs']}>
        <RunsSection />
      </MemoryRouter>,
    );

    await waitFor(() => expect(capturedSignal).toBeDefined());
    view.unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('does not expose unsupported result type and error code filters in real mode', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            data: {
              resource: 'runs',
              items: [
                {
                  agent_run_id: 42,
                  session_id: 7,
                  intent: 'planning',
                  status: 'failed',
                  trace_id: 'trace-42',
                  duration_ms: 120,
                  actor_ref: 'user-42',
                },
              ],
              total: 1,
              page: 1,
              size: 20,
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );

    render(
      <MemoryRouter initialEntries={['/admin/runs']}>
        <RunsSection />
      </MemoryRouter>,
    );

    expect(await screen.findByText('trace-42')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '结果类型筛选' })).toBeDisabled();
    expect(screen.getByLabelText('错误码')).toBeDisabled();
  });
});
