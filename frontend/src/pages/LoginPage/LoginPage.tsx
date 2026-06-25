import { Button, Checkbox, Form, Input, Tag } from '@arco-design/web-react';
import { IconLock, IconUser } from '@arco-design/web-react/icon';
import { Link, useNavigate } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { mockAuthUser, mockLoginDefaults } from '../../mock/auth';
import styles from './LoginPage.module.css';

export function LoginPage() {
  const navigate = useNavigate();

  return (
    <main className={styles.page}>
      <section className={styles.panel}>
        <div className={styles.brand}>
          <BrandLogo size="hero" showTagline />
          <p>登录后会把会话、饮食记录、计划和知识库访问范围绑定到当前用户。</p>
        </div>

        <Form
          className={styles.form}
          initialValues={mockLoginDefaults}
          layout="vertical"
          onSubmit={() => navigate('/')}
        >
          <div className={styles.formHead}>
            <Tag color="green">Mock 登录入口</Tag>
            <h1>进入 FoodMate 工作台</h1>
            <span>当前前端只模拟登录态，真实接口待鉴权方案审核后接入。</span>
          </div>
          <Form.Item label="用户名" field="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<IconUser />} placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<IconLock />} placeholder="请输入密码" />
          </Form.Item>
          <div className={styles.options}>
            <Checkbox defaultChecked>保持登录 7 天</Checkbox>
            <Link to="/">稍后再说</Link>
          </div>
          <Button htmlType="submit" long type="primary">
            登录并进入工作台
          </Button>
        </Form>
      </section>

      <aside className={styles.contract}>
        <span>当前 mock 用户</span>
        <strong>{mockAuthUser.displayName}</strong>
        <dl>
          <dt>角色</dt>
          <dd>{mockAuthUser.role}</dd>
          <dt>账号</dt>
          <dd>{mockAuthUser.username}</dd>
          <dt>营养目标</dt>
          <dd>{mockAuthUser.proteinTarget}</dd>
          <dt>推荐鉴权</dt>
          <dd>Access Token + Refresh Token</dd>
        </dl>
      </aside>
    </main>
  );
}
