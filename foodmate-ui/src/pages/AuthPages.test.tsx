import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ForgotPasswordPage } from './ForgotPasswordPage/ForgotPasswordPage';
import { LoginPage } from './LoginPage/LoginPage';
import { RegisterPage } from './RegisterPage/RegisterPage';
import { ResetPasswordPage } from './ResetPasswordPage/ResetPasswordPage';

function LocationProbe() {
  return <output data-testid="location">当前路由</output>;
}

function renderAuth(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('authentication pages', () => {
  it.each([
    ['submitting', '登录中...', '登录中...', true],
    ['field-error', '请输入有效的邮箱地址', '登录', false],
    ['credential-error', '邮箱或密码错误，请重试', '登录', false],
    ['account-locked', '账号已锁定', '登录已禁用', true],
    ['account-disabled', '账号已禁用', '账号不可用', true],
    ['service-unavailable', '服务暂时不可用', '系统维护中', true],
  ])('renders login state %s from the Figma state query', (state, text, buttonText, disabled) => {
    renderAuth(`/login?state=${state}`);

    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: buttonText })).toHaveProperty('disabled', disabled);
  });

  it('routes login recovery and registration actions to independent Figma pages', async () => {
    const user = userEvent.setup();
    renderAuth('/login');

    await user.click(screen.getByRole('button', { name: '忘记密码？' }));
    expect(screen.getByRole('heading', { name: '找回密码' })).toBeInTheDocument();
  });

  it('renders the registration password rules as values change', async () => {
    const user = userEvent.setup();
    renderAuth('/register');

    expect(screen.getByText('至少 8 个字符')).toBeInTheDocument();
    await user.type(screen.getByLabelText('密码'), 'StrongPass9');
    expect(screen.getByText('至少 8 个字符')).toHaveClass(/passwordRuleValid/);
    expect(screen.getByText('包含大写字母')).toHaveClass(/passwordRuleValid/);
    expect(screen.getByText('包含数字')).toHaveClass(/passwordRuleValid/);
  });

  it('keeps the forgot-password success card visible beside the form', async () => {
    const user = userEvent.setup();
    renderAuth('/forgot-password');

    expect(screen.getByRole('heading', { name: '邮件已发送' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('邮箱地址'), 'demo@example.com');
    await user.click(screen.getByRole('button', { name: '发送重置邮件' }));
    expect(screen.getByRole('status')).toHaveTextContent('重置邮件请求已完成');
  });

  it('renders the reset-password strength contract and protects missing tokens', async () => {
    const user = userEvent.setup();
    renderAuth('/reset-password');

    expect(screen.getByText('高安全')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '确认重置' }));
    expect(screen.getByRole('heading', { name: '重置密码' })).toBeInTheDocument();
  });
});
