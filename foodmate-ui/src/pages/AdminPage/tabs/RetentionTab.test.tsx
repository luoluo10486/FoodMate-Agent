import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { RetentionSection } from './RetentionTab';

describe('Admin 数据保留页面 fixture', () => {
  it('展示清理请求、preflight 和法律保留安全摘要', () => {
    render(
      <MemoryRouter>
        <RetentionSection onAction={vi.fn()} refreshNonce={0} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('region', { name: '数据保留治理' })).toBeInTheDocument();
    expect(screen.getByText('创建清理请求')).toBeInTheDocument();
    expect(screen.getByText('读取清理请求')).toBeInTheDocument();
    expect(screen.getByText('执行前置检查')).toBeInTheDocument();
    expect(screen.getByText('RETENTION_HARD_DELETE_DISABLED')).toBeInTheDocument();
    expect(screen.getByText('法律保留 #88')).toBeInTheDocument();
    expect(screen.getByText('Fixture 只展示示例保留状态，真实模式以服务端响应为准。')).toBeInTheDocument();
  });
});
