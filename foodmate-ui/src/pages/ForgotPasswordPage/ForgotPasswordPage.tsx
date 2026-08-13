import { CheckCircle2 } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { notify } from '../../lib/notice';
import { requestPasswordReset } from '../../services/authService';
import { AuthBrand, AuthCard, AuthField, AuthShell, AuthSubmit } from '../Auth/AuthVisual';
import styles from '../LoginPage/LoginPage.module.css';

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      await requestPasswordReset(email);
      setSent(true);
    } catch (error) {
      notify(error instanceof Error ? error.message : '密码重置请求失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell variant="forgot">
      <div className={styles.forgotContainer}>
        <AuthCard className={styles.forgotCard}>
          <AuthBrand title="找回密码" subtitle="输入你的注册邮箱，我们将发送重置链接" />
          <form className={styles.authForm} onSubmit={submit}>
            <AuthField
              label="邮箱地址"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="example@foodmate.com"
              leadingIcon="mail"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <div className={styles.authActionStack}>
              <AuthSubmit disabled={submitting}>{submitting ? '发送中...' : '发送重置邮件'}</AuthSubmit>
              <Button className={styles.authBackLink} variant="ghost" type="button" onClick={() => navigate('/login')}>
                返回登录
              </Button>
            </div>
          </form>
        </AuthCard>
        <AuthCard className={`${styles.forgotCard} ${styles.successCard}`}>
          <div className={styles.successIcon} aria-hidden="true">
            <CheckCircle2 />
          </div>
          <h1>邮件已发送</h1>
          <p>如果该邮箱已注册，你将收到重置密码的邮件。请检查你的收件箱。</p>
          <Button className={styles.authPrimary} type="button" onClick={() => navigate('/login')}>
            返回登录
          </Button>
          {sent ? (
            <span className={styles.successLive} role="status">
              重置邮件请求已完成
            </span>
          ) : null}
        </AuthCard>
      </div>
    </AuthShell>
  );
}
