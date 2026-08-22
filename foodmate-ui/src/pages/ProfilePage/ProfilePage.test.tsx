import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ProfilePage } from './ProfilePage';

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/profile/memories" element={<ProfilePage />} />
        <Route path="/profile/security" element={<ProfilePage />} />
        <Route path="/profile/data" element={<ProfilePage />} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProfilePage', () => {
  it.each([
    ['basic', '饮食与身体目标'],
    ['memories', '记忆系统'],
    ['security', '修改账号密码'],
    ['privacy', '导出个人工作区数据'],
  ])('maps the Figma %s fixture to its page', (state, heading) => {
    renderPage(`/profile?state=${state}`);

    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
  });

  it('keeps the Figma fixture navigation semantics aligned with the rendered profile tab', () => {
    renderPage('/profile?state=security');

    expect(screen.getByRole('link', { name: '安全与设备' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '基本资料' })).not.toHaveAttribute('aria-current');
  });

  it('renders the Figma profile shell fixture', () => {
    renderPage('/profile?state=basic');

    expect(screen.getByText('Anddy')).toBeInTheDocument();
    expect(screen.getByText('早餐奶昔配方')).toBeInTheDocument();
    expect(screen.getByText('饮食与身体目标')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '个人头像' })).toHaveAttribute(
      'src',
      '/assets/figma/agent-chat/awaiting-clarification/sidebar-avatar.png',
    );
  });

  it('edits profile fields and manages allergens before saving', async () => {
    const user = userEvent.setup();
    renderPage('/profile');

    const displayName = screen.getByRole('textbox', { name: '展示名称' });
    await user.clear(displayName);
    await user.type(displayName, '我的营养工作区');

    const allergenInput = screen.getByRole('textbox', { name: '添加过敏原' });
    await user.type(allergenInput, '花生');
    await user.click(screen.getByRole('button', { name: '添加过敏原' }));

    expect(screen.getByRole('button', { name: '花生' })).toHaveClass('inline-flex');
    expect(screen.getByRole('button', { name: '放弃更改' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: '保存资料' }));

    expect(screen.getByRole('button', { name: '放弃更改' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: '花生' }));
    expect(screen.queryByRole('button', { name: '花生' })).not.toBeInTheDocument();
  });

  it('filters memories and routes the empty state back to chat', async () => {
    const user = userEvent.setup();
    renderPage('/profile/memories');

    await user.click(screen.getByRole('tab', { name: '待确认 (3)' }));
    expect(screen.getByRole('tab', { name: '待确认 (3)' })).toHaveClass('inline-flex');
    expect(screen.getByText(/Attempts to avoid soy protein isolates/)).toBeInTheDocument();
    expect(screen.queryByText(/Prefers wild caught salmon/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: '记忆分类' }));
    await user.click(screen.getByRole('option', { name: '目标' }));
    expect(screen.getByRole('heading', { name: '暂无长期记忆' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '去会话确认' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/chat');
  });

  it('shows password validation failure without submitting an invalid password', async () => {
    const user = userEvent.setup();
    renderPage('/profile/security');

    await user.type(screen.getByLabelText('当前密码'), 'current-password');
    await user.type(screen.getByLabelText('新密码'), 'short');
    await user.type(screen.getByLabelText('确认密码'), 'short');
    await user.click(screen.getByRole('button', { name: '更新密码' }));

    expect(screen.getByText('密码更新失败，请重新填写')).toBeInTheDocument();
  });

  it('confirms logging out other devices and preserves the current session', async () => {
    const user = userEvent.setup();
    renderPage('/profile/security');

    expect(screen.getByRole('button', { name: /查看登录历史/ })).toHaveClass('inline-flex');
    await user.click(screen.getByRole('button', { name: '退出其他设备' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '退出其他设备？' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '确认退出' }));

    expect(screen.queryByText('iPhone 15 Pro · iOS App')).not.toBeInTheDocument();
    expect(screen.queryByText('Google Chrome · Windows 11')).not.toBeInTheDocument();
    expect(screen.getByText('0 ACTIVE DEVICES')).toBeInTheDocument();
  });

  it('queues an export and requires the deletion confirmation phrase', async () => {
    const user = userEvent.setup();
    renderPage('/profile/data');

    await user.click(screen.getByRole('button', { name: '创建数据导出' }));
    expect(screen.getByText('今天')).toBeInTheDocument();
    expect(screen.getAllByText('排队中').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: '创建数据导出' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /下载归档/ })).toHaveClass('inline-flex');

    await user.click(screen.getByRole('button', { name: '申请注销账号' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.type(screen.getByLabelText('当前密码'), 'current-password');
    await user.type(screen.getByPlaceholderText('DELETE_MY_ACCOUNT'), 'DELETE_MY_ACCOUNT');
    await user.click(screen.getByRole('button', { name: '确认注销' }));

    expect(screen.getByText(/账号已注销 · request_id:/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
