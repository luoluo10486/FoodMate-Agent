import { describe, expect, it } from 'vitest';
import { getVisualQaNow, isVisualQaEnabled } from './visualQa';

describe('visual QA mode', () => {
  it('only enables with the explicit query value', () => {
    expect(isVisualQaEnabled('?visual-qa=1')).toBe(true);
    expect(isVisualQaEnabled('?visual-qa=0')).toBe(false);
    expect(isVisualQaEnabled('')).toBe(false);
  });

  it('uses the fixed mock time for visual captures', () => {
    expect(getVisualQaNow('?visual-qa=1').toISOString()).toBe('2024-03-14T04:46:00.000Z');
  });
});
