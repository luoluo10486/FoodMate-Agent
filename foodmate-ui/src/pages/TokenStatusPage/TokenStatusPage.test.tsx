import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { TokenStatusPage } from './TokenStatusPage';

describe('TokenStatusPage', () => {
  it.each([
    ['invalid', '链接无效'],
    ['expired', '链接已过期'],
    ['used', '链接已使用'],
  ])('renders the Figma token state %s', (state, title) => {
    render(
      <MemoryRouter initialEntries={[`/token-status?state=${state}`]}>
        <TokenStatusPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新发送重置邮件' })).toBeInTheDocument();
  });
});
