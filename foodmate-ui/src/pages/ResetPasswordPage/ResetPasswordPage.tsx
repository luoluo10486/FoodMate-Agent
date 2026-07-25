import { Button, Form, Input, Message } from '@arco-design/web-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { confirmPasswordReset } from '../../services/authService';
import styles from '../LoginPage/LoginPage.module.css';

type ResetValues = { password: string; confirmPassword: string };

export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';

  const submit = async (values: ResetValues) => {
    if (!token) { Message.error('重置链接无效或缺少令牌。'); return; }
    if (values.password !== values.confirmPassword) { Message.error('两次输入的密码不一致。'); return; }
    try {
      await confirmPasswordReset(token, values.password);
      Message.success('密码已重置，请重新登录。');
      navigate('/login', { replace: true });
    } catch (error) {
      Message.error(error instanceof Error ? error.message : '密码重置失败');
    }
  };

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-label="重置密码">
        <div className={styles.brand}><BrandLogo size="small" showTagline={false} /><p>重置密码</p></div>
        <Form className={styles.form} layout="vertical" onSubmit={submit}>
          <Form.Item label="新密码" field="password" rules={[{ required: true, minLength: 8, message: '请输入至少 8 位密码' }]}><Input.Password /></Form.Item>
          <Form.Item label="确认密码" field="confirmPassword" rules={[{ required: true, message: '请再次输入密码' }]}><Input.Password /></Form.Item>
          <Button className={styles.primaryAction} htmlType="submit" long type="primary">确认重置</Button>
        </Form>
        <Button type="text" onClick={() => navigate('/login')}>返回登录</Button>
      </section>
    </main>
  );
}
