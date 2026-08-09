import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { DietRecordsPage } from './DietRecordsPage';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/analysis?view=records']}>
      <DietRecordsPage />
    </MemoryRouter>,
  );
}

describe('DietRecordsPage', () => {
  it('switches between day and week views', async () => {
    const user = userEvent.setup();
    renderPage();

    const weekTab = screen.getByRole('tab', { name: '周视图' });
    await user.click(weekTab);

    expect(weekTab).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('button', { name: '本周，3月11日 - 3月17日' })).toBeInTheDocument();
  });

  it('adds a pending food item through the dialog', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getAllByRole('button', { name: '+ 添加食物' })[0]);
    const input = screen.getByPlaceholderText('例如：煮鸡蛋 2 个');
    await user.type(input, '香蕉');
    await user.click(screen.getByRole('button', { name: /^添加$/ }));

    expect(screen.getByText('香蕉')).toBeInTheDocument();
    expect(screen.getByText(/等待营养估算。/)).toBeInTheDocument();
  });
});
