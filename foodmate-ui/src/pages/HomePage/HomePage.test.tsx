import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { HomePage } from './HomePage';

function LocationProbe() {
  return <output data-testid="location">{useLocation().pathname}</output>;
}

describe('HomePage session cards', () => {
  it('renders active sessions through shadcn buttons and keeps navigation intact', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/chat/:sessionId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    const sessionCard = screen.getByRole('button', { name: /每周宏量调整/ });
    expect(sessionCard).toHaveClass('inline-flex');

    await user.click(sessionCard);
    expect(screen.getByTestId('location')).toHaveTextContent('/chat/week-plan');
  });
});
