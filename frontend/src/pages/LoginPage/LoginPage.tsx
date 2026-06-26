import { Button, Checkbox, Form, Input, Message } from '@arco-design/web-react';
import { IconEmail, IconLock, IconUser } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { mockLoginDefaults } from '../../mock/auth';
import type { LoginFormValues } from '../../mock/auth';
import styles from './LoginPage.module.css';

type AuthMode = 'login' | 'register' | 'forgot';

type RegisterFormValues = {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
};

type ForgotFormValues = {
  email: string;
};

const titleByMode: Record<AuthMode, string> = {
  login: '登录 FoodMate',
  register: '注册账号',
  forgot: '找回密码'
};

export function LoginPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<AuthMode>('login');
  const [registerPassword, setRegisterPassword] = useState('');

  const handleLogin = (_values: LoginFormValues) => {
    navigate('/');
  };

  const handleRegister = (_values: RegisterFormValues) => {
    Message.success('注册流程已提交（mock）');
    setMode('login');
  };

  const handleForgot = (_values: ForgotFormValues) => {
    Message.success('重置邮件已发送（mock）');
    setMode('login');
  };

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-label={titleByMode[mode]}>
        <div className={styles.brand}>
          <BrandLogo size="small" showTagline={false} />
          <p>{titleByMode[mode]}</p>
        </div>

        {mode === 'login' ? (
          <Form className={styles.form} initialValues={mockLoginDefaults} layout="vertical" onSubmit={handleLogin}>
            <Form.Item label="用户名或邮箱" field="username" rules={[{ required: true, message: '请输入用户名或邮箱' }]}>
              <Input prefix={<IconUser />} placeholder="请输入用户名或邮箱" />
            </Form.Item>
            <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<IconLock />} placeholder="请输入密码" />
            </Form.Item>

            <div className={styles.options}>
              <Form.Item field="rememberMe" triggerPropName="checked" noStyle>
                <Checkbox>保持登录</Checkbox>
              </Form.Item>
              <Button className={styles.linkButton} type="text" onClick={() => setMode('forgot')}>
                忘记密码
              </Button>
            </div>

            <Button className={styles.primaryAction} htmlType="submit" long type="primary">
              登录
            </Button>
          </Form>
        ) : null}

        {mode === 'register' ? (
          <Form className={styles.form} layout="vertical" onSubmit={handleRegister}>
            <Form.Item label="用户名" field="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<IconUser />} placeholder="请输入用户名" />
            </Form.Item>
            <Form.Item label="邮箱" field="email" rules={[{ required: true, message: '请输入邮箱' }]}>
              <Input prefix={<IconEmail />} placeholder="请输入邮箱" />
            </Form.Item>
            <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<IconLock />} placeholder="请输入密码" onChange={setRegisterPassword} />
            </Form.Item>
            <Form.Item
              label="确认密码"
              field="confirmPassword"
              rules={[
                { required: true, message: '请再次输入密码' },
                {
                  validator: (value, callback) => {
                    if (value && registerPassword && value !== registerPassword) {
                      callback('两次密码不一致');
                    } else {
                      callback();
                    }
                  }
                }
              ]}
            >
              <Input.Password prefix={<IconLock />} placeholder="请再次输入密码" />
            </Form.Item>

            <Button className={styles.primaryAction} htmlType="submit" long type="primary">
              注册
            </Button>
          </Form>
        ) : null}

        {mode === 'forgot' ? (
          <Form className={styles.form} layout="vertical" onSubmit={handleForgot}>
            <Form.Item label="邮箱" field="email" rules={[{ required: true, message: '请输入邮箱' }]}>
              <Input prefix={<IconEmail />} placeholder="请输入邮箱" />
            </Form.Item>

            <Button className={styles.primaryAction} htmlType="submit" long type="primary">
              发送重置邮件
            </Button>
          </Form>
        ) : null}

        <div className={styles.actions}>
          {mode === 'login' ? (
            <>
              <Button long onClick={() => setMode('register')}>
                注册账号
              </Button>
              <Button long onClick={() => navigate('/')}>
                稍后体验
              </Button>
            </>
          ) : (
            <Button long onClick={() => setMode('login')}>
              返回登录
            </Button>
          )}
        </div>

        <span className={styles.note}>当前为前端 mock 流程</span>
      </section>
    </main>
  );
}
