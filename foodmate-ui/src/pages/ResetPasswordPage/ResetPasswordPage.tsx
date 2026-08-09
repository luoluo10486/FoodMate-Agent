import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { notify } from '../../lib/notice';
import { confirmPasswordReset } from '../../services/authService';
import styles from '../LoginPage/LoginPage.module.css';

type ResetValues = { password: string; confirmPassword: string };

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className={styles.field}>
      <span className={styles.fieldLabel}>{label}</span>
      {children}
    </label>
  );
}

export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [values, setValues] = useState<ResetValues>({ password: '', confirmPassword: '' });
  const [submitting, setSubmitting] = useState(false);

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
    <main className={styles.page}>
      <section className={styles.card} aria-label="重置密码">
        <div className={styles.brand}>
          <BrandLogo size="small" showTagline={false} />
          <p>重置密码</p>
        </div>
        <form className={styles.form} onSubmit={submit}>
          <Field label="新密码">
            <Input
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              required
              value={values.password}
              onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
            />
          </Field>
          <Field label="确认密码">
            <Input
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
              required
              value={values.confirmPassword}
              onChange={(event) => setValues((current) => ({ ...current, confirmPassword: event.target.value }))}
            />
          </Field>
          <Button className={styles.primaryAction} type="submit" disabled={submitting}>
            {submitting ? '重置中...' : '确认重置'}
          </Button>
        </form>
        <Button variant="ghost" type="button" onClick={() => navigate('/login')}>
          返回登录
        </Button>
      </section>
    </main>
  );
}
