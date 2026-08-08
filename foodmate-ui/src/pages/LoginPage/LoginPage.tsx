import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { getLoginDefaults, login, register, requestPasswordReset } from '../../services/authService';
import styles from './LoginPage.module.css';
import { Button, Form, Input, Message } from '../../components/ui/legacy-primitives';

type AuthMode = 'login' | 'register' | 'forgot';

export function LoginPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<AuthMode>('login');
  const [registerPassword, setRegisterPassword] = useState('');

  const handleLogin = async (values: Record<string, string>) => {
    const credentials = {
      username: values.username ?? '',
      password: values.password ?? '',
      rememberMe: values.rememberMe === 'true',
    };
    try { await login(credentials); navigate('/'); } catch (error) { Message.error(error instanceof Error ? error.message : '登录失败'); }
  };
  const handleRegister = async (values: { username: string; email: string; password: string; confirmPassword: string }) => {
    try { await register(values); navigate('/'); } catch (error) { Message.error(error instanceof Error ? error.message : '注册失败'); }
  };
  const handleForgot = async (values: { email: string }) => {
    try { await requestPasswordReset(values.email); Message.success('重置邮件已发送，请检查邮箱。'); setMode('login'); } catch (error) { Message.error(error instanceof Error ? error.message : '密码重置请求失败'); }
  };

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-label={mode === 'login' ? '登录 FoodMate' : mode === 'register' ? '注册账号' : '找回密码'}>
        {mode !== 'login' ? <Button className={styles.backButton} type="text" onClick={() => setMode('login')}>返回登录</Button> : null}
        <div className={styles.brand}><BrandLogo size="small" showTagline={false} /><p>{mode === 'login' ? '登录 FoodMate' : mode === 'register' ? '注册账号' : '找回密码'}</p></div>
        {mode === 'login' ? <Form className={styles.form} initialValues={getLoginDefaults()} layout="vertical" onSubmit={handleLogin}>
          <Form.Item label="用户名或邮箱" field="username" rules={[{ required: true, message: '请输入用户名或邮箱' }]}><Input placeholder="请输入用户名或邮箱" /></Form.Item>
          <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}><Input.Password placeholder="请输入密码" /></Form.Item>
          <Button className={styles.primaryAction} htmlType="submit" long type="primary">登录</Button>
        </Form> : null}
        {mode === 'register' ? <Form className={styles.form} layout="vertical" onSubmit={handleRegister}>
          <Form.Item label="用户名" field="username" rules={[{ required: true, message: '请输入用户名' }]}><Input placeholder="请输入用户名" /></Form.Item>
          <Form.Item label="邮箱" field="email" rules={[{ required: true, message: '请输入邮箱' }]}><Input placeholder="请输入邮箱" /></Form.Item>
          <Form.Item label="密码" field="password" rules={[{ required: true, minLength: 8, message: '请输入至少 8 位密码' }]}><Input.Password placeholder="请输入密码" onChange={setRegisterPassword} /></Form.Item>
          <Form.Item label="确认密码" field="confirmPassword" rules={[{ required: true, message: '请再次输入密码' }, { validator: (value, callback) => value && value !== registerPassword ? callback('两次密码不一致') : callback() }]}><Input.Password placeholder="请再次输入密码" /></Form.Item>
          <Button className={styles.primaryAction} htmlType="submit" long type="primary">注册</Button>
        </Form> : null}
        {mode === 'forgot' ? <Form className={styles.form} layout="vertical" onSubmit={handleForgot}>
          <Form.Item label="邮箱" field="email" rules={[{ required: true, message: '请输入邮箱' }]}><Input placeholder="请输入注册邮箱" /></Form.Item>
          <Button className={styles.primaryAction} htmlType="submit" long type="primary">发送重置邮件</Button>
        </Form> : null}
        <div className={styles.actions}>
          {mode === 'login' ? <><Button long onClick={() => setMode('register')}>注册账号</Button><Button long onClick={() => setMode('forgot')}>忘记密码</Button></> : null}
        </div>
        <span className={styles.note}>{import.meta.env.VITE_AGENT_MODE === 'real' ? '当前连接真实服务' : '当前为前端 mock 流程'}</span>
      </section>
    </main>
  );
}
