import { render, screen } from '@testing-library/react';
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
});
