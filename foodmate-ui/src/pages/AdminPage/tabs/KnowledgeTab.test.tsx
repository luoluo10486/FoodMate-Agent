import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { KnowledgeSection } from './KnowledgeTab';

describe('KnowledgeSection', () => {
  it('renders the Figma upload workspace, compact document table, and vector insights', () => {
    render(<KnowledgeSection onAction={vi.fn()} />);

    expect(screen.getByText('拖入多个文件，后台异步建索引')).toBeInTheDocument();
    expect(screen.getByText('上传 / 索引状态')).toBeInTheDocument();
    expect(screen.getByText('USDA_Keto_Ingredient_Guidelines.pdf')).toBeInTheDocument();
    expect(screen.getByText('文档向量洞察')).toBeInTheDocument();
    expect(screen.getByText('Dimensions: 1536 (text-embedding-ada-002)')).toBeInTheDocument();
  });

  it('opens the upload dialog when a document is selected', async () => {
    const user = userEvent.setup();
    render(<KnowledgeSection onAction={vi.fn()} />);

    const file = new File(['nutrition'], 'nutrient_reference.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText('选择知识库文件'), file);

    expect(screen.getByRole('dialog', { name: '上传知识库文档' })).toBeInTheDocument();
    expect(screen.getByText('已选择 1 个文件')).toBeInTheDocument();
  });

  it('sends selected document state changes through the shared action flow', async () => {
    const onAction = vi.fn();
    const user = userEvent.setup();
    render(<KnowledgeSection onAction={onAction} />);

    await user.click(screen.getByRole('button', { name: '下线文档' }));

    expect(onAction).toHaveBeenCalledWith(expect.objectContaining({ action: '下线文档', targetId: 'doc_118a9' }));
  });
});
