import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AgentFeedback } from './AgentFeedback';
import { apiRequest } from '../../services/apiClient';

vi.mock('../../services/apiClient', () => ({ apiRequest: vi.fn() }));

describe('AgentFeedback', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(apiRequest).mockResolvedValue({});
  });

  it('submits positive feedback without sending answer text', async () => {
    const user = userEvent.setup();
    render(<AgentFeedback runId="42" messageId="99" />);

    await user.click(screen.getByRole('button', { name: '有帮助' }));

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/agent-runs/42/messages/99/feedback',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ helpful: true, reason_codes: [] }),
      }),
    );
    expect(await screen.findByText('感谢反馈')).toBeInTheDocument();
  });

  it('requires a reason for negative feedback', async () => {
    const user = userEvent.setup();
    render(<AgentFeedback runId="42" messageId="99" />);

    await user.click(screen.getByRole('button', { name: '没帮助' }));
    await user.click(screen.getByRole('button', { name: '提交反馈' }));

    expect(screen.getByRole('alert')).toHaveTextContent('请选择至少一个原因');
    expect(apiRequest).not.toHaveBeenCalled();
  });
});
