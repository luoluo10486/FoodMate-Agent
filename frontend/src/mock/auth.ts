export type AuthStatus = 'anonymous' | 'authenticated' | 'expired' | 'disabled' | 'forbidden';

export type AuthPermission = {
  key: string;
  label: string;
  scope: string;
};

export type UserNutritionProfile = {
  heightCm: number;
  weightKg: number;
  activityLevel: string;
  dietGoal: string;
  proteinMultiplierRange: [number, number];
  proteinTargetRange: [number, number];
  calorieTarget: number;
  proteinTarget: number;
  preference: string;
  allergens: string[];
  dislikes: string[];
  preferredUnits: {
    weight: string;
    energy: string;
  };
};

export type AuthUser = {
  id: string;
  username: string;
  displayName: string;
  role: 'user' | 'admin' | 'operator';
  status: 'active' | 'disabled' | 'locked';
  email: string;
  avatarUrl?: string;
  lastLoginAt: string;
  profile: UserNutritionProfile;
  permissions: AuthPermission[];
  security: {
    tokenStrategy: string;
    accessTokenTtl: string;
    refreshTokenMode: string;
  };
};

export type LoginFormValues = {
  username: string;
  password: string;
  rememberMe: boolean;
};

export const mockAuthStatus: AuthStatus = 'authenticated';

export const mockAuthUser: AuthUser = {
  id: '10001',
  username: 'liang',
  displayName: '梁同学',
  role: 'admin',
  status: 'active',
  email: 'liang@example.com',
  avatarUrl: '',
  lastLoginAt: '2026-06-25 15:40',
  profile: {
    heightCm: 175,
    weightKg: 70,
    activityLevel: '中等活动',
    dietGoal: '高蛋白 · 控预算',
    proteinMultiplierRange: [1.5, 2],
    proteinTargetRange: [105, 140],
    calorieTarget: 2100,
    proteinTarget: 120,
    preference: '高蛋白 · 控预算',
    allergens: ['暂无过敏原'],
    dislikes: ['不吃猪肉', '少油'],
    preferredUnits: {
      weight: 'g',
      energy: 'kcal',
    },
  },
  permissions: [
    { key: 'agent.session', label: 'Agent 会话', scope: '仅自己的会话和消息' },
    { key: 'food.log', label: '饮食记录', scope: '仅自己的饮食日志' },
    { key: 'meal.plan', label: '备餐计划', scope: '仅自己的计划与购物清单' },
    { key: 'knowledge.search', label: '知识库检索', scope: '公开内容 + 用户可见内容' },
    { key: 'admin.dashboard', label: '管理后台', scope: '管理员治理视图' },
  ],
  security: {
    tokenStrategy: 'Access Token + HttpOnly Refresh Cookie',
    accessTokenTtl: '15-30 分钟',
    refreshTokenMode: '7-30 天，可撤销',
  },
};

export const mockLoginDefaults: LoginFormValues = {
  username: '',
  password: '',
  rememberMe: true,
};

export const mockAuthScenarios: Array<{ status: AuthStatus; title: string; description: string; code: string }> = [
  {
    status: 'anonymous',
    title: '未登录',
    description: '访问受保护资源时跳转登录，并保留 redirect。',
    code: 'AUTH_REQUIRED',
  },
  {
    status: 'authenticated',
    title: '已登录',
    description: '可访问自己的会话、饮食记录、计划和知识库内容。',
    code: 'OK',
  },
  {
    status: 'expired',
    title: '登录过期',
    description: 'Access Token 过期后尝试刷新，失败则回到登录页。',
    code: 'AUTH_TOKEN_EXPIRED',
  },
  {
    status: 'disabled',
    title: '账号禁用',
    description: '账号状态不是 active 时拒绝登录。',
    code: 'AUTH_ACCOUNT_DISABLED',
  },
  {
    status: 'forbidden',
    title: '权限不足',
    description: '已登录但访问管理员资源时展示无权限。',
    code: 'AUTH_FORBIDDEN',
  },
];
