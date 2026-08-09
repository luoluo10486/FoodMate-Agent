import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AnalysisPage } from './AnalysisPage';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/analysis']}>
      <AnalysisPage />
    </MemoryRouter>,
  );
}

describe('AnalysisPage', () => {
  it('updates summary metrics when the range changes', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('tab', { name: '30 天' }));

    expect(screen.getByRole('tab', { name: '30 天' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('1,896 kcal')).toBeInTheDocument();
    expect(screen.getByText('26 / 30 Days')).toBeInTheDocument();
  });

  it('reports export state without changing the visible analysis', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: '导出 CSV' }));

    expect(screen.getByRole('status')).toHaveTextContent('分析报告已排队');
    expect(screen.getByText('能量摄入与目标对比')).toBeInTheDocument();
  });
});
