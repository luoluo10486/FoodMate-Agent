import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { KnowledgeSection } from './KnowledgeTab';

describe('KnowledgeSection controls', () => {
  it('renders document rows as accessible shadcn buttons', () => {
    render(<KnowledgeSection onAction={vi.fn()} />);

    const row = screen.getByRole('button', { name: /doc_118a9/ });
    expect(row).toBeInTheDocument();
    expect(row).toHaveClass(/knowledgeTableRow/);
  });
});
