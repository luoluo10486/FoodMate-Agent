export const planConstraints = [
  ['人数', '2 人'],
  ['周期', '7 天'],
  ['预算', '300 元'],
  ['目标', '高蛋白'],
  ['忌口', '不吃猪肉']
];

export const mealRows = [
  { day: '周一', lunch: '鸡胸藜麦碗', dinner: '番茄豆腐汤' },
  { day: '周二', lunch: '牛肉蔬菜饭', dinner: '鸡蛋菠菜面' },
  { day: '周三', lunch: '豆腐鸡蛋便当', dinner: '虾仁西兰花' }
];

export const shoppingGroups = [
  {
    name: '蛋白质',
    items: ['鸡胸肉 1.2kg', '鸡蛋 12 个', '豆腐 4 盒']
  },
  {
    name: '蔬菜',
    items: ['西兰花', '菠菜', '番茄', '胡萝卜']
  }
];

export const validationItems = [
  { label: '预算通过', value: '286 / 300 元', status: 'success' },
  { label: '蛋白质目标通过', value: '日均 78g', status: 'success' },
  { label: '提醒', value: '周四晚餐接近 35 分钟', status: 'warning' }
];
