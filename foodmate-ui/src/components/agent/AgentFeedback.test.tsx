import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AgentFeedback } from './AgentFeedback';
import { submitAgentFeedback } from '../../services/agentRunService';

vi.mock('../../services/agentRunService', () => ({ submitAgentFeedback: vi.fn() }));

describe('AgentFeedback', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(submitAgentFeedback).mockResolvedValue({
      feedback_id: '1',
      run_id: '42',
      message_id: '99',
      helpful: true,
      reason_codes: [],
      high_risk: false,
      idempotency_key: 'agent-feedback-42-99',
    });
  });

  it('submits positive feedback without sending answer text', async () => {
    const user = userEvent.setup();
    render(<AgentFeedback runId="42" messageId="99" />);

    await user.click(screen.getByRole('button', { name: '有帮助' }));

    expect(submitAgentFeedback).toHaveBeenCalledWith('42', '99', {
      helpful: true,
      reasonCodes: [],
      comment: undefined,
    });
    expect(await screen.findByText('感谢反馈')).toBeInTheDocument();
  });

  it('requires a reason for negative feedback', async () => {
    const user = userEvent.setup();
    render(<AgentFeedback runId="42" messageId="99" />);

    await user.click(screen.getByRole('button', { name: '没帮助' }));
    await user.click(screen.getByRole('button', { name: '提交反馈' }));

    expect(screen.getByRole('alert')).toHaveTextContent('请选择至少一个原因');
    expect(submitAgentFeedback).not.toHaveBeenCalled();
  });
});
