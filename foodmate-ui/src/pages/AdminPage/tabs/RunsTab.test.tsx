import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminPage } from '../AdminPage';

function renderRuns(initialEntry = '/admin/runs') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Admin run governance', () => {
  it('renders complete governance tabs and filters runs by traceable identifiers', async () => {
    const user = userEvent.setup();
    renderRuns();

    expect(screen.getByRole('tab', { name: 'AgentRun' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('Session ID')).toBeInTheDocument();
    expect(screen.getByText('SQL_POLICY')).toBeInTheDocument();

    const query = screen.getByPlaceholderText('Run ID / user / session / trace');
    await user.type(query, 'run_1025');
    expect(screen.getByText('run_1025')).toBeInTheDocument();
    expect(screen.queryByText('run_1024')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '重置筛选' }));
    expect(screen.getByText('run_1024')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'ToolCall' }));
    expect(screen.getByRole('tab', { name: 'ToolCall' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('tool_call_8921')).toBeInTheDocument();
  });

  it('opens Run detail and follows the related Tool Call and Trace records', async () => {
    const user = userEvent.setup();
    renderRuns();

    await user.click(screen.getAllByRole('button', { name: '查看详情' })[0]);
    const runDialog = screen.getByRole('dialog');
    expect(within(runDialog).getByRole('heading', { name: 'Run 详情' })).toBeInTheDocument();
    expect(within(runDialog).getByText('session_plan_901')).toBeInTheDocument();

    await user.click(within(runDialog).getByRole('button', { name: /food_log_writer/ }));
    expect(within(runDialog).getByRole('heading', { name: 'Tool Call 详情' })).toBeInTheDocument();
    expect(within(runDialog).getByText('mealType=dinner, items=4')).toBeInTheDocument();

    await user.click(within(runDialog).getByRole('button', { name: /查看关联 Trace/ }));
    expect(within(runDialog).getByRole('heading', { name: 'Trace 详情' })).toBeInTheDocument();
    expect(within(runDialog).getByText('planner')).toBeInTheDocument();
  });

  it('opens SQL Audit from its own tab and exposes policy and query hash', async () => {
    const user = userEvent.setup();
    renderRuns('/admin/runs?tab=sql');

    expect(screen.getByRole('tab', { name: 'SQLAudit' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getAllByText('template_allowlist')).toHaveLength(2);
    await user.click(screen.getAllByRole('button', { name: '查看详情' })[0]);

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'SQL Audit 详情' })).toBeInTheDocument();
    expect(within(dialog).getByText('sha256:meal-summary-301')).toBeInTheDocument();
  });
});
