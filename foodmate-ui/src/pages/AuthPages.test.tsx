import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ForgotPasswordPage } from './ForgotPasswordPage/ForgotPasswordPage';
import { LoginPage } from './LoginPage/LoginPage';
import { RegisterPage } from './RegisterPage/RegisterPage';
import { ResetPasswordPage } from './ResetPasswordPage/ResetPasswordPage';

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
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

  it('uses the Figma exception-state registration copy', () => {
    renderAuth('/login?state=service-unavailable');

    expect(screen.getByRole('button', { name: '立即注册' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '注册' })).not.toBeInTheDocument();
  });

  it('uses the Figma submitting assets and example values', () => {
    renderAuth('/login?state=submitting');

    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-submitting-leaf.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-submitting-user.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-submitting-lock.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-submitting-eye.svg"]'),
    ).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-submitting-line.svg"]')).toHaveLength(
      2,
    );
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-login-loader.svg"]')).toBeInTheDocument();
    expect(screen.getByLabelText('邮箱地址')).toHaveValue('alex@foodmate.com');
    expect(screen.getByLabelText('密码')).toHaveValue('password');
    expect(screen.getByRole('button', { name: '登录中...' })).toHaveClass('primaryActionDisabled');
  });

  it('uses the Figma field-error assets and error contract', () => {
    renderAuth('/login?state=field-error');

    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-field-error-leaf.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-field-error-user.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-field-error-lock.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-field-error-eye.svg"]'),
    ).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-field-error-line.svg"]')).toHaveLength(
      2,
    );
    expect(screen.getByLabelText('邮箱地址')).toHaveAttribute('placeholder', 'invalid-email');
    expect(screen.getByLabelText('密码')).toHaveAttribute('placeholder', '密码');
    expect(screen.getByText('请输入有效的邮箱地址')).toBeInTheDocument();
    expect(screen.getByText('密码不能为空')).toBeInTheDocument();
  });

  it('uses the Figma credential-error banner, assets and example values', () => {
    renderAuth('/login?state=credential-error');

    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-credential-error-leaf.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-credential-error-alert.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-credential-error-user.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-credential-error-lock.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-credential-error-eye.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-credential-error-line.svg"]'),
    ).toHaveLength(2);
    expect(screen.getByLabelText('邮箱地址')).toHaveValue('wrong@foodmate.com');
    expect(screen.getByLabelText('密码')).toHaveValue('password');
    expect(screen.getByRole('alert')).toHaveTextContent('邮箱或密码错误，请重试');
  });

  it('uses the Figma account-locked warning, disabled controls and example values', () => {
    renderAuth('/login?state=account-locked');

    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-locked-leaf.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-locked-alert.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-locked-user.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-locked-lock.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-locked-eye.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-account-locked-line.svg"]'),
    ).toHaveLength(2);
    expect(screen.getByLabelText('邮箱地址')).toHaveValue('locked@foodmate.com');
    expect(screen.getByLabelText('密码')).toHaveValue('password');
    expect(screen.getByRole('alert')).toHaveTextContent('账号已锁定');
    expect(screen.getByRole('alert')).toHaveTextContent('由于多次登录失败');
    expect(screen.getByRole('main')).toHaveClass(/authPageLoginAccountLocked/);
    expect(screen.getByRole('button', { name: '忘记密码？' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登录已禁用' })).toHaveProperty('disabled', true);
  });

  it('uses the Figma account-disabled error banner, support action and example values', () => {
    renderAuth('/login?state=account-disabled');

    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-disabled-leaf.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-disabled-alert.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-disabled-user.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-disabled-lock.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-account-disabled-eye.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-account-disabled-line.svg"]'),
    ).toHaveLength(2);
    expect(screen.getByLabelText('邮箱地址')).toHaveValue('disabled@foodmate.com');
    expect(screen.getByLabelText('密码')).toHaveValue('password');
    expect(screen.getByRole('alert')).toHaveTextContent('账号已禁用');
    expect(screen.getByRole('alert')).toHaveTextContent('你的账号已被管理员禁用');
    expect(screen.getByRole('button', { name: '联系客服' })).toHaveClass('loginAlertAction');
    expect(screen.queryByRole('button', { name: '登录' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '账号不可用' })).toHaveProperty('disabled', true);
  });

  it('uses the Figma service-unavailable info banner and recovery action', () => {
    renderAuth('/login?state=service-unavailable');

    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-service-unavailable-leaf.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-service-unavailable-info.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-service-unavailable-user.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-service-unavailable-lock.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-login-service-unavailable-eye.svg"]'),
    ).toBeInTheDocument();
    expect(
      document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-service-unavailable-line.svg"]'),
    ).toHaveLength(2);
    expect(screen.getByLabelText('邮箱地址')).toHaveAttribute('placeholder', '邮箱地址');
    expect(screen.getByLabelText('密码')).toHaveAttribute('placeholder', '密码');
    expect(screen.getByRole('alert')).toHaveTextContent('服务暂时不可用');
    expect(screen.getByRole('alert')).toHaveTextContent('系统维护中，请稍后再试。');
    expect(screen.getByRole('button', { name: '刷新页面' })).toHaveClass('loginAlertAction');
    expect(screen.getByRole('button', { name: '系统维护中' })).toHaveProperty('disabled', true);
  });

  it('does not render Figma login states in real mode', () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    try {
      renderAuth('/login?state=account-disabled');

      expect(screen.getByRole('button', { name: '登录' })).toBeEnabled();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(screen.getByLabelText('邮箱地址')).toHaveValue('');
      expect(screen.getByLabelText('密码')).toHaveValue('');
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('renders the backend credential error without redirecting in real mode', async () => {
    const user = userEvent.setup();
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            error: { code: 'AUTH_INVALID_CREDENTIALS', message: '用户名或密码错误' },
          }),
          { status: 401 },
        ),
      ),
    );

    try {
      renderAuth('/login?visual-qa=1');
      await user.type(screen.getByLabelText('邮箱地址'), 'wrong@example.com');
      await user.type(screen.getByLabelText('密码'), 'wrong-password');
      await user.click(screen.getByRole('button', { name: '登录' }));

      await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('邮箱或密码错误，请重试'));
      expect(screen.queryByTestId('location')).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: '登录' })).toBeEnabled();
    } finally {
      vi.unstubAllEnvs();
      vi.unstubAllGlobals();
    }
  });

  it('returns to a safe internal redirect after real login', async () => {
    const user = userEvent.setup();
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              success: true,
              data: { user_id: 7, username: 'real-user', role: 'user', session_expires_at: '2026-09-14T00:00:00Z' },
            }),
            { status: 200 },
          ),
        )
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              success: true,
              data: {
                user_id: 7,
                username: 'real-user',
                email: 'real@example.com',
                role: 'user',
                status: 'active',
                gender: '男',
              },
            }),
            { status: 200 },
          ),
        ),
    );

    try {
      renderAuth('/login?redirect=%2Fchat%2F7%3Fmode%3Dcompact&visual-qa=1');
      await user.type(screen.getByLabelText('邮箱地址'), 'real@example.com');
      await user.type(screen.getByLabelText('密码'), 'StrongPass99!');
      await user.click(screen.getByRole('button', { name: '登录' }));

      await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/chat/7?mode=compact'));
    } finally {
      vi.unstubAllEnvs();
      vi.unstubAllGlobals();
      localStorage.clear();
    }
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

  it('starts the mock registration fixture with the Figma example values', () => {
    renderAuth('/register');

    expect(screen.getByRole('main')).toHaveStyle({
      '--auth-diagonal': '#dfeedb',
      '--auth-brand': '#a6d997',
      '--auth-primary': '#a6d997',
      '--auth-card-width': '460px',
      '--auth-card-radius': '24px',
      '--auth-control-height': '50px',
      '--auth-control-radius': '12px',
      '--auth-primary-height': '50px',
      '--auth-primary-radius': '14px',
    });
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-register-leaf.svg"]')).toBeInTheDocument();
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-register-user.svg"]')).toBeInTheDocument();
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-register-mail.svg"]')).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-register-lock.svg"]')).toHaveLength(2);
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-register-eye.svg"]')).toHaveLength(2);
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-register-check-circle.svg"]')).toHaveLength(
      4,
    );
    expect(screen.getByLabelText('用户名')).toHaveValue('麦克斯');
    expect(screen.getByLabelText('邮箱地址')).toHaveValue('max@foodmate.com');
    expect(screen.getByLabelText('密码')).toHaveValue('Foodmate123');
    expect(screen.getByLabelText('确认密码')).toHaveValue('Foodmate123');
    expect(screen.getByText('至少 8 个字符')).toHaveClass(/passwordRuleValid/);
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-register-line.svg"]')).toHaveLength(2);
  });

  it('does not prefill the registration form in real mode', () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    try {
      renderAuth('/register');

      expect(screen.getByLabelText('用户名')).toHaveValue('');
      expect(screen.getByLabelText('邮箱地址')).toHaveValue('');
      expect(screen.getByLabelText('密码')).toHaveValue('');
      expect(screen.getByLabelText('确认密码')).toHaveValue('');
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('renders backend registration field errors on the matching controls', async () => {
    const user = userEvent.setup();
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            error: { code: 'INVALID_ARGUMENT', message: '请求参数无效', details: { email: '邮箱已注册' } },
          }),
          { status: 400 },
        ),
      ),
    );

    try {
      renderAuth('/register');
      await user.type(screen.getByLabelText('用户名'), 'real-user');
      await user.type(screen.getByLabelText('邮箱地址'), 'real@example.com');
      await user.type(screen.getByLabelText('密码'), 'StrongPass99!');
      await user.type(screen.getByLabelText('确认密码'), 'StrongPass99!');
      await user.click(screen.getByRole('button', { name: '注册' }));

      await waitFor(() => expect(screen.getByText('邮箱已注册')).toBeInTheDocument());
      expect(document.querySelector('input[name="email"]')).toHaveAttribute('aria-invalid', 'true');
    } finally {
      vi.unstubAllEnvs();
      vi.unstubAllGlobals();
    }
  });

  it('uses the shared shadcn icon button for password visibility', async () => {
    const user = userEvent.setup();
    renderAuth('/register');

    const password = screen.getByLabelText('密码');
    const toggle = screen.getByRole('button', { name: /^隐藏密码$/ });
    expect(toggle).toBeInTheDocument();
    expect(password).toHaveAttribute('type', 'text');

    await user.click(toggle);
    expect(password).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: /^显示密码$/ })).toBeInTheDocument();
  });

  it('uses the shared shadcn icon button for login password visibility', async () => {
    const user = userEvent.setup();
    renderAuth('/login');

    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-login-user.svg"]')).toBeInTheDocument();
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-login-lock.svg"]')).toBeInTheDocument();
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-login-eye.svg"]')).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-login-line.svg"]')).toHaveLength(2);
    const password = screen.getByLabelText('密码');
    const toggle = screen.getByRole('button', { name: /^显示密码$/ });
    expect(toggle).toHaveClass('inline-flex');
    expect(password).toHaveAttribute('type', 'password');

    await user.click(toggle);
    expect(password).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('隐藏密码')).toHaveClass('inline-flex');
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-login-eye.svg"]')).toBeInTheDocument();
  });

  it('keeps account support and service recovery actions available as shadcn buttons', () => {
    renderAuth('/login?state=account-disabled');
    expect(screen.getByRole('button', { name: '联系客服' })).toHaveClass('inline-flex');

    renderAuth('/login?state=service-unavailable');
    expect(screen.getByRole('button', { name: '刷新页面' })).toHaveClass('inline-flex');
  });

  it('keeps the forgot-password success card visible beside the form', async () => {
    const user = userEvent.setup();
    renderAuth('/forgot-password');

    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-forgot-leaf.svg"]')).toBeInTheDocument();
    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-forgot-mail.svg"]')).toBeInTheDocument();
    expect(
      document.querySelector('img[src="/assets/figma/auth/foodmate-forgot-check-circle.svg"]'),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '邮件已发送' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('邮箱地址'), 'demo@example.com');
    await user.click(screen.getByRole('button', { name: '发送重置邮件' }));
    expect(screen.getByRole('status')).toHaveTextContent('重置邮件请求已完成');
  });

  it('renders backend forgot-password field errors on the email control', async () => {
    const user = userEvent.setup();
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            error: { code: 'INVALID_ARGUMENT', message: '请求参数无效', details: { email: '邮箱格式不正确' } },
          }),
          { status: 400 },
        ),
      ),
    );

    try {
      renderAuth('/forgot-password');
      await user.type(screen.getByLabelText('邮箱地址'), 'invalid@example.com');
      await user.click(screen.getByRole('button', { name: '发送重置邮件' }));

      await waitFor(() => expect(screen.getByText('邮箱格式不正确')).toBeInTheDocument());
      const emailInput = document.querySelector('input[name="email"]');
      expect(emailInput).toHaveAttribute('aria-invalid', 'true');
      expect(emailInput).toHaveAttribute('aria-describedby', 'email-error');
    } finally {
      vi.unstubAllEnvs();
      vi.unstubAllGlobals();
    }
  });

  it('separates the Figma brand and primary tokens on forgot-password', () => {
    renderAuth('/forgot-password');

    expect(screen.getByRole('main')).toHaveStyle({
      '--auth-diagonal': '#c5f0d6',
      '--auth-brand': '#a6d997',
      '--auth-primary': '#48c78e',
      '--auth-card-width': '440px',
      '--auth-control-height': '50px',
    });
  });

  it('renders the reset-password strength contract and protects missing tokens', async () => {
    const user = userEvent.setup();
    renderAuth('/reset-password');

    expect(document.querySelector('img[src="/assets/figma/auth/foodmate-reset-leaf.svg"]')).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-reset-lock.svg"]')).toHaveLength(2);
    expect(document.querySelectorAll('img[src="/assets/figma/auth/foodmate-reset-eye.svg"]')).toHaveLength(2);
    expect(screen.getByLabelText('新密码')).toHaveValue('StrongPass99');
    expect(screen.getByLabelText('新密码')).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('确认新密码')).toHaveValue('StrongPass99');
    expect(screen.getByLabelText('确认新密码')).toHaveAttribute('type', 'text');
    expect(screen.getByText('高安全')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '确认重置' }));
    expect(screen.getByRole('heading', { name: '重置密码' })).toBeInTheDocument();
  });

  it('renders backend reset-password field errors on the password control', async () => {
    const user = userEvent.setup();
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            error: { code: 'INVALID_ARGUMENT', message: '请求参数无效', details: { new_password: '密码强度不足' } },
          }),
          { status: 400 },
        ),
      ),
    );

    try {
      renderAuth('/reset-password?token=reset-token');
      await user.type(screen.getByLabelText('新密码'), 'weak-password');
      await user.type(screen.getByLabelText('确认新密码'), 'weak-password');
      await user.click(screen.getByRole('button', { name: '确认重置' }));

      await waitFor(() => expect(screen.getByText('密码强度不足')).toBeInTheDocument());
      expect(document.querySelector('input[name="password"]')).toHaveAttribute('aria-invalid', 'true');
    } finally {
      vi.unstubAllEnvs();
      vi.unstubAllGlobals();
    }
  });

  it('separates the Figma brand and primary tokens on reset-password', () => {
    renderAuth('/reset-password');

    expect(screen.getByRole('main')).toHaveStyle({
      '--auth-diagonal': '#c5f0d6',
      '--auth-brand': '#a6d997',
      '--auth-primary': '#48c78e',
      '--auth-card-width': '460px',
      '--auth-control-height': '50px',
    });
  });
});
