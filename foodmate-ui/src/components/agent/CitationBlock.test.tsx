import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CitationBlock } from './CitationBlock';

describe('CitationBlock', () => {
  it('keeps citation details collapsed until the user opens them', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <CitationBlock
        citation={{
          id: 'citation-1',
          title: '公共饮食指南',
          snippet: '每日膳食应覆盖足量蛋白质和膳食纤维。',
          source: '版本 2026.08 · 章节 基础原则',
        }}
      />,
    );

    const details = container.querySelector('details');
    expect(details).not.toBeNull();
    expect(details).not.toHaveAttribute('open');
    await user.click(screen.getByText('公共饮食指南'));
    expect(details).toHaveAttribute('open');
    expect(screen.getByText('每日膳食应覆盖足量蛋白质和膳食纤维。')).toBeInTheDocument();
  });
});
