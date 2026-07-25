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
        expect(screen.getByText('推荐任务')).toBeInTheDocument();
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
        expect(screen.getByText('推荐任务')).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
  });
});

describe('HomePage 独立渲染', () => {
  it('渲染任务卡片和推荐提示', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );

    expect(screen.getByText('推荐任务')).toBeInTheDocument();
    expect(screen.getByText('热量计算')).toBeInTheDocument();
    expect(screen.getByText('摄入分析')).toBeInTheDocument();
    expect(screen.getByText('复杂规划')).toBeInTheDocument();
  });
});
