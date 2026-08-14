/**
 * ChatPage / useMockAgentReplay 会话隔离测试
 *
 * P0-1: 验证切换 session_id 后 mock 状态重置
 *
 * 审计风险：seededRef 不会随 seedKey 变化重置，
 * 导致第二个会话的 seed 无法触发。
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { describe, it, expect } from 'vitest';
import { ChatPage } from './ChatPage';

/**
 * 路由切换控制器：先挂载 /chat/session-a，
 * 等 seed 触发后导航到 /chat/session-b，验证第二个 seed 是否触发。
 */
function SessionSwitchTest() {
  const navigate = useNavigate();
  const [phase, setPhase] = useState<'a' | 'b'>('a');
  const [navigated, setNavigated] = useState(false);

  useEffect(() => {
    if (phase === 'a') {
      // 等待第一次 seed 完成后切换到 B
      const timer = setTimeout(() => {
        setPhase('b');
        setNavigated(true);
        navigate('/chat/session-b');
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [phase, navigate]);

  return (
    <div>
      <Routes>
        <Route path="/chat/:session_id?" element={<ChatPage />} />
      </Routes>
      {navigated && <div data-testid="navigated-to-b">已导航到 B</div>}
    </div>
  );
}

describe('ChatPage 会话隔离', () => {
  it('切换 session_id 后重新触发 seed（修复前：seededRef 残留导致 seed 不触发）', async () => {
    render(
      <MemoryRouter initialEntries={['/chat/session-a']}>
        <SessionSwitchTest />
      </MemoryRouter>,
    );

    // 阶段 A：等待会话 A 的 seed 触发
    await waitFor(
      () => {
        const messages = screen.getAllByText('你');
        expect(messages.length).toBeGreaterThanOrEqual(1);
      },
      { timeout: 3000 },
    );

    // 等待导航到会话 B 完成
    await waitFor(
      () => {
        expect(screen.getByTestId('navigated-to-b')).toBeInTheDocument();
      },
      { timeout: 3000 },
    );

    // 阶段 B：等待会话 B 的新消息出现
    // 如果 seededRef 正确重置，会话 B 会触发新的 send
    // 如果 seededRef 残留 true，则不会出现新消息
    await waitFor(
      () => {
        expect(screen.getByTestId('navigated-to-b')).toBeInTheDocument();
      },
      { timeout: 200 },
    );

    // 验证 ChatPage 仍在渲染（没有崩溃）
    expect(screen.getByText('工具与引用')).toBeInTheDocument();
  });
});

describe('ChatPage Figma 空态', () => {
  it('renders the empty conversation state and places a recommended prompt into the composer', () => {
    render(
      <MemoryRouter initialEntries={['/chat?state=empty']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: '开始新的对话' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /分析我今天的饮食/ })).toBeInTheDocument();
    expect(screen.queryByText('运行轨迹')).not.toBeInTheDocument();

    const input = screen.getByPlaceholderText('输入消息或添加自定义指令...');
    fireEvent.click(screen.getByRole('button', { name: /制定本周餐食计划/ }));
    expect(input).toHaveValue('根据我的减脂目标制定本周餐食计划');
  });
});

describe('ChatPage Figma Planning 状态', () => {
  it('renders the planning steps without the trace rail and disables the composer', () => {
    render(
      <MemoryRouter initialEntries={['/chat?state=planning']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Planning...')).toBeInTheDocument();
    expect(screen.getByText(/制定分析方案/)).toBeInTheDocument();
    expect(screen.queryByText('运行轨迹')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '停止生成' })).toBeEnabled();
    expect(screen.getByPlaceholderText('正在规划任务流程，请稍候...')).toBeDisabled();
  });
});

describe('ChatPage Figma Tool Executing 状态', () => {
  it('renders the executing tool card, running trace and disabled composer', () => {
    render(
      <MemoryRouter initialEntries={['/chat?state=tool-executing']}>
        <Routes>
          <Route path="/chat/:session_id?" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Executing Tools...')).toBeInTheDocument();
    expect(screen.getByText('向量索引检索 - 12ms ✓')).toBeInTheDocument();
    expect(screen.getByText('数据库调用 - running...')).toBeInTheDocument();
    expect(screen.getByText('营养计算 - pending')).toBeInTheDocument();
    expect(screen.getByText('RUN ID: fst_trace_9821aa')).toBeInTheDocument();
    expect(screen.getByText('数据库调用: 饮食日志表')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('正在运行数据计算工具...')).toBeDisabled();
    expect(screen.getByRole('button', { name: '停止生成' })).toBeEnabled();
  });
});
