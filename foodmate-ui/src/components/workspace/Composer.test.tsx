import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Composer } from './Composer';

function renderComposer({
  running = false,
  disabled = false,
  onSend = vi.fn(),
  onStop = vi.fn(),
}: {
  running?: boolean;
  disabled?: boolean;
  onSend?: () => void;
  onStop?: () => void;
} = {}) {
  function Harness() {
    const [value, setValue] = useState('');
    return (
      <Composer
        value={value}
        running={running}
        disabled={disabled}
        toolsUsed={0}
        toolsTotal={6}
        agentsUsed={0}
        agentsTotal={1}
        onChange={setValue}
        onSend={onSend}
        onStop={onStop}
      />
    );
  }

  render(<Harness />);
  return { input: screen.getByRole('textbox'), submit: screen.getAllByRole('button').at(-1)!, onSend, onStop };
}

describe('Composer interaction contract', () => {
  it('sends on Enter after text changes', () => {
    const onSend = vi.fn();
    const { input } = renderComposer({ onSend });

    fireEvent.change(input, { target: { value: '记录午餐' } });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });

    expect(onSend).toHaveBeenCalledOnce();
  });

  it('does not send on Shift+Enter', () => {
    const onSend = vi.fn();
    const { input } = renderComposer({ onSend });

    fireEvent.change(input, { target: { value: '换行内容' } });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', shiftKey: true });

    expect(onSend).not.toHaveBeenCalled();
  });

  it('does not send while an IME composition is active', () => {
    const onSend = vi.fn();
    const { input } = renderComposer({ onSend });

    fireEvent.change(input, { target: { value: '午餐' } });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', isComposing: true });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 229 });

    expect(onSend).not.toHaveBeenCalled();
  });

  it('stops a running agent from the submit control', () => {
    const onStop = vi.fn();
    const { submit } = renderComposer({ running: true, onStop });

    fireEvent.click(submit);

    expect(onStop).toHaveBeenCalledOnce();
    expect(submit).toHaveAttribute('data-state', 'running');
  });

  it('disables input and submit while disabled', () => {
    const onSend = vi.fn();
    const { input, submit } = renderComposer({ disabled: true, onSend });

    expect(input).toBeDisabled();
    expect(submit).toBeDisabled();
    fireEvent.click(submit);
    expect(onSend).not.toHaveBeenCalled();
  });
});
