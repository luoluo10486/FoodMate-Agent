import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { PlanningPage } from './PlanningPage';

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

function renderPage(initialEntry = '/planning') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/planning" element={<PlanningPage />} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PlanningPage', () => {
  it('supports the weekly schedule and shopping checklist interactions', async () => {
    const user = userEvent.setup();
    renderPage();

    const Wednesday = screen.getByRole('tab', { name: '周三 15' });
    expect(Wednesday).toHaveClass('inline-flex');
    await user.click(Wednesday);
    expect(Wednesday).toHaveAttribute('aria-selected', 'true');

    const emptyMeal = screen.getAllByRole('button', { name: '+ 计划' })[0];
    expect(emptyMeal).toHaveClass('inline-flex');
    await user.click(emptyMeal);
    expect(screen.getByRole('status')).toHaveTextContent('已打开早餐的计划入口');

    const salmon = screen.getByRole('checkbox', { name: '野生三文鱼 (450g)' });
    await user.click(salmon);
    expect(salmon).toBeChecked();

    await user.click(screen.getByRole('button', { name: '保存计划' }));
    expect(screen.getByRole('status')).toHaveTextContent('计划已保存');
  });

  it('renders the nine-session Figma sidebar fixture', () => {
    renderPage('/planning?state=v2');

    [
      '每周饮食微调',
      '运动前零食建议',
      '过敏原排除规则',
      '蛋白质补充方案',
      '睡前加餐建议',
      '早餐碳水搭配',
      '晚餐蛋白质补充',
      '低碳水饮食建议',
      '早餐奶昔配方',
    ].forEach((title) => expect(screen.getByText(title)).toBeInTheDocument());
  });

  it('renders loading, empty, and error states with their recovery paths', async () => {
    const user = userEvent.setup();
    const { unmount } = renderPage('/planning?state=loading');
    expect(screen.getByLabelText('餐食规划加载中')).toHaveAttribute('aria-busy', 'true');
    unmount();

    renderPage('/planning?state=empty');
    await user.click(screen.getByRole('button', { name: '创建首个规划方案' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/chat?prompt=请为我创建本周餐食规划');
    unmount();

    renderPage('/planning?state=error');
    await user.click(screen.getByRole('button', { name: '重新加载' }));
    expect(screen.getByRole('heading', { name: '增肌计划 v3' })).toBeInTheDocument();
  });

  it('keeps plan list filters and the wizard progression aligned', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=list');

    expect(screen.getByText('夏日减脂轻食计划')).toBeInTheDocument();
    expect(screen.queryByText('高蛋白增肌能量餐')).not.toBeInTheDocument();
    const draftTab = screen.getByRole('tab', { name: '草稿箱' });
    expect(draftTab).toHaveClass('inline-flex');
    await user.click(draftTab);
    expect(screen.getByText('高蛋白增肌能量餐')).toBeInTheDocument();
    expect(screen.queryByText('夏日减脂轻食计划')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: '进行中' }));
    await user.click(screen.getByRole('button', { name: '进入计划' }));
    expect(screen.getByRole('heading', { name: '增肌计划 v3' })).toBeInTheDocument();
  });

  it('moves through the wizard and supports cancelling generation', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=wizard-step1');

    expect(screen.getByRole('button', { name: /设置目标/ })).toHaveClass('inline-flex');
    await user.click(screen.getByRole('button', { name: '下一步: 膳食约束' }));
    expect(screen.getByRole('heading', { name: '步骤 2: 设置膳食约束 & 偏好' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '下一步: 确认并生成' }));
    expect(screen.getByRole('heading', { name: '步骤 3: 确认规则并运行规划' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '开始生成智能计划' }));
    expect(screen.getByRole('heading', { name: '正在生成您的智能餐食计划' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '取消生成' }));
    expect(screen.getByRole('heading', { name: '步骤 3: 确认规则并运行规划' })).toBeInTheDocument();
  });

  it('uses shadcn actions for allergy chips and plan card menus', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=wizard-step2');

    const peanut = screen.getByRole('button', { name: /花生/ });
    expect(peanut).toHaveClass('inline-flex');
    await user.click(peanut);
    expect(screen.queryByRole('button', { name: /花生/ })).not.toBeInTheDocument();

    const addAllergy = screen.getByRole('button', { name: '+ 添加过敏源' });
    expect(addAllergy).toHaveClass('inline-flex');
    await user.click(addAllergy);
    expect(screen.getByRole('button', { name: /坚果/ })).toBeInTheDocument();

    renderPage('/planning?state=list');
    const planMenu = screen.getByRole('button', { name: '夏日减脂轻食计划更多操作' });
    expect(planMenu).toHaveClass('inline-flex');
  });

  it('uses the shadcn select for cuisine preferences', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=wizard-step2');

    const cuisine = screen.getByRole('combobox', { name: '首选菜系口味' });
    expect(cuisine).toHaveTextContent('中式、日式轻食');
    await user.click(cuisine);
    await user.click(screen.getByRole('option', { name: '地中海轻食' }));
    expect(cuisine).toHaveTextContent('地中海轻食');
  });

  it('applies a selected conflict resolution and updates the shopping progress count', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=conflict');

    await user.click(screen.getByRole('radio', { name: '替换菜品（智能推荐低蛋白早餐）' }));
    expect(screen.getByRole('radio', { name: '替换菜品（智能推荐低蛋白早餐）' })).toBeChecked();
    await user.click(screen.getByRole('button', { name: '应用修改并重新计划' }));
    expect(screen.getByRole('heading', { name: '增肌计划 v3' })).toBeInTheDocument();

    renderPage('/planning?state=shopping-list');
    expect(screen.getByText('已买 3 / 8 项')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '导出清单文件' }));
    expect(screen.getByRole('status')).toHaveTextContent('清单导出已准备');
  });
});
