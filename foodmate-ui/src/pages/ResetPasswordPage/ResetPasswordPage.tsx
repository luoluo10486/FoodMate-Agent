import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { notify } from '../../lib/notice';
import { confirmPasswordReset } from '../../services/authService';
import { AuthBrand, AuthCard, AuthShell, AuthSubmit, PasswordField } from '../Auth/AuthVisual';
import { Button } from '../../components/ui/button';
import styles from '../LoginPage/LoginPage.module.css';

type ResetValues = { password: string; confirmPassword: string };

export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [values, setValues] = useState<ResetValues>({ password: '', confirmPassword: '' });
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token) {
      notify('重置链接无效或缺少令牌。', 'error');
      return;
    }
    if (values.password !== values.confirmPassword) {
      notify('两次输入的密码不一致。', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await confirmPasswordReset(token, values.password);
      notify('密码已重置，请重新登录。', 'success');
      navigate('/login', { replace: true });
    } catch (error) {
      notify(error instanceof Error ? error.message : '密码重置失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell variant="reset">
      <AuthCard className={styles.resetCard}>
        <AuthBrand title="重置密码" subtitle="请输入你的新密码" />
        <form className={styles.authForm} onSubmit={submit}>
          <div className={styles.resetFields} data-node-id="680:318">
            <PasswordField
              label="新密码"
              name="password"
              autoComplete="new-password"
              placeholder="StrongPass99"
              value={values.password}
              show={showPassword}
              onToggle={() => setShowPassword((current) => !current)}
              onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
            />
            <PasswordField
              label="确认新密码"
              name="confirmPassword"
              autoComplete="new-password"
              placeholder="StrongPass99"
              value={values.confirmPassword}
              show={showConfirmPassword}
              onToggle={() => setShowConfirmPassword((current) => !current)}
              onChange={(event) => setValues((current) => ({ ...current, confirmPassword: event.target.value }))}
            />
          </div>
          <div className={styles.passwordStrength} data-node-id="680:331">
            <div>
              <span>密码强度</span>
              <strong>高安全</strong>
            </div>
            <div className={styles.passwordStrengthBars} aria-label="密码强度：高安全">
              <i />
              <i />
              <i />
              <i />
            </div>
          </div>
          <div className={styles.authActionStack} data-node-id="680:340">
            <AuthSubmit disabled={submitting}>{submitting ? '重置中...' : '确认重置'}</AuthSubmit>
            <Button className={styles.authBackLink} variant="ghost" type="button" onClick={() => navigate('/login')}>
              返回登录
            </Button>
          </div>
        </form>
      </AuthCard>
    </AuthShell>
  );
}
