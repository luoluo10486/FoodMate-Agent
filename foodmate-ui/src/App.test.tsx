/**
 * FoodMate 应用基础测试
 *
 * P0-1: 最小测试覆盖
 * - 应用可渲染和路由跳转
 * - 关键页面组件可独立挂载
 */
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { App } from './App';
import { HomePage } from './pages/HomePage/HomePage';

describe('App 路由', () => {
  it('根路径渲染 suspense fallback 后显示首页', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    // Suspense fallback 先出现
    expect(screen.getByText('FoodMate 正在准备工作台...')).toBeInTheDocument();

    // 等待首页内容加载
    await waitFor(
      () => {
        expect(screen.getByText('活跃会话')).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
  });

  it('非法路径重定向到首页', async () => {
    render(
      <MemoryRouter initialEntries={['/does-not-exist']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(
      () => {
        expect(screen.getByText('待确认队列')).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
  });
});

describe('HomePage 独立渲染', () => {
  it('渲染 Figma 工作台首页内容', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );

    expect(screen.getByText('活跃会话')).toBeInTheDocument();
    expect(screen.getByText('待确认队列')).toBeInTheDocument();
    expect(screen.getByText('记录饮食')).toBeInTheDocument();
    expect(screen.getByText('分析摄入')).toBeInTheDocument();
    expect(screen.getByText('创建计划')).toBeInTheDocument();
  });

  it('figma-v2 fixture 固定使用设计稿身份，不覆盖默认用户', () => {
    render(
      <MemoryRouter initialEntries={['/?state=figma-v2']}>
        <HomePage />
      </MemoryRouter>,
    );

    expect(screen.getByText('👋 早上好，Anddy！')).toBeInTheDocument();
    expect(screen.getByText('ID: 1234567')).toBeInTheDocument();
  });
});
