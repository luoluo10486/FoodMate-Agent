import { useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Message, Select, Tag } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { mockAuthUser } from '../../mock/auth';
import styles from './ProfilePage.module.css';

const Option = Select.Option;

export function ProfilePage() {
  const [avatarRemoved, setAvatarRemoved] = useState(false);
  const [avatarError, setAvatarError] = useState('');
  const [saved, setSaved] = useState(false);

  const handleSave = () => {
    setSaved(true);
    Message.success('个人资料已模拟保存');
  };

  return (
    <WorkspaceLayout activeModule="profile" moduleLabel={<Tag color="green">个人资料</Tag>}>
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <h1>个人资料</h1>
            <p>管理账号、安全设置和饮食画像。当前为 mock 流程，不会写入真实后端。</p>
          </div>
          {saved ? <Tag color="green">已模拟保存</Tag> : null}
        </section>

        <section className={styles.body}>
          <aside className={styles.accountColumn}>
            <Card className={styles.card} bordered={false}>
              <div className={styles.avatarPanel}>
                <div className={`${styles.avatar} ${avatarRemoved ? styles.avatarMuted : ''}`}>
                  {avatarRemoved ? '访' : mockAuthUser.displayName.slice(0, 1)}
                </div>
                <strong>{mockAuthUser.displayName}</strong>
                <span>{mockAuthUser.email}</span>
              </div>
              <div className={styles.avatarActions}>
                <Button
                  type="primary"
                  onClick={() => {
                    setAvatarRemoved(false);
                    setAvatarError('');
                    Message.success('头像已模拟上传');
                  }}
                >
                  上传头像
                </Button>
                <Button
                  onClick={() => {
                    setAvatarRemoved(true);
                    setAvatarError('');
                    Message.info('头像已模拟删除');
                  }}
                >
                  删除头像
                </Button>
                <Button
                  status="danger"
                  onClick={() => {
                    setAvatarError('图片超过 2MB 或类型不支持');
                    Message.error('头像上传失败：图片超过 2MB 或类型不支持');
                  }}
                >
                  模拟失败
                </Button>
              </div>
              {avatarError ? <Tag color="red">{avatarError}</Tag> : null}
              <p className={styles.uploadHint}>支持 JPG / PNG / WebP，2MB 内。真实接入后文件进入 MinIO。</p>
            </Card>

            <Card className={styles.card} bordered={false}>
              <strong>账号状态</strong>
              <div className={styles.metaList}>
                <article>
                  <span>用户名</span>
                  <b>{mockAuthUser.username}</b>
                </article>
                <article>
                  <span>角色</span>
                  <Tag color="arcoblue">{mockAuthUser.role}</Tag>
                </article>
                <article>
                  <span>状态</span>
                  <Tag color="green">{mockAuthUser.status}</Tag>
                </article>
                <article>
                  <span>最近登录</span>
                  <b>{mockAuthUser.lastLoginAt}</b>
                </article>
              </div>
            </Card>
          </aside>

          <main className={styles.formColumn}>
            <Card className={styles.card} bordered={false}>
              <div className={styles.cardHead}>
                <strong>资料与饮食画像</strong>
                <Tag color="orange">role/status 只能由管理员修改</Tag>
              </div>
              <Form
                className={styles.form}
                layout="vertical"
                initialValues={{
                  displayName: mockAuthUser.displayName,
                  email: mockAuthUser.email,
                  heightCm: mockAuthUser.profile.heightCm,
                  weightKg: mockAuthUser.profile.weightKg,
                  activityLevel: mockAuthUser.profile.activityLevel,
                  dietGoal: mockAuthUser.profile.dietGoal,
                  calorieTarget: mockAuthUser.profile.calorieTarget,
                  proteinTarget: mockAuthUser.profile.proteinTarget,
                  dislikes: mockAuthUser.profile.dislikes.join('、'),
                  allergens: mockAuthUser.profile.allergens.join('、'),
                  units: `${mockAuthUser.profile.preferredUnits.weight} / ${mockAuthUser.profile.preferredUnits.energy}`
                }}
                onSubmit={handleSave}
              >
                <div className={styles.formGrid}>
                  <Form.Item label="昵称 / 展示名" field="displayName">
                    <Input />
                  </Form.Item>
                  <Form.Item label="邮箱" field="email">
                    <Input />
                  </Form.Item>
                  <Form.Item label="身高 cm" field="heightCm">
                    <InputNumber min={80} max={240} />
                  </Form.Item>
                  <Form.Item label="体重 kg" field="weightKg">
                    <InputNumber min={20} max={250} />
                  </Form.Item>
                  <Form.Item label="活动水平" field="activityLevel">
                    <Select>
                      <Option value="低活动">低活动</Option>
                      <Option value="中等活动">中等活动</Option>
                      <Option value="高活动">高活动</Option>
                    </Select>
                  </Form.Item>
                  <Form.Item label="饮食目标" field="dietGoal">
                    <Input />
                  </Form.Item>
                  <Form.Item label="每日热量目标 kcal" field="calorieTarget">
                    <InputNumber min={800} max={6000} />
                  </Form.Item>
                  <Form.Item label="蛋白质目标 g" field="proteinTarget">
                    <InputNumber min={20} max={300} />
                  </Form.Item>
                  <Form.Item label="忌口" field="dislikes">
                    <Input />
                  </Form.Item>
                  <Form.Item label="过敏原" field="allergens">
                    <Input />
                  </Form.Item>
                  <Form.Item label="单位偏好" field="units">
                    <Input />
                  </Form.Item>
                </div>
                <div className={styles.actions}>
                  <Button>修改密码</Button>
                  <Button type="primary" htmlType="submit">
                    保存资料
                  </Button>
                </div>
              </Form>
            </Card>
          </main>
        </section>
      </div>
    </WorkspaceLayout>
  );
}
