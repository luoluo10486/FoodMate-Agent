import { describe, expect, it } from 'vitest';
import { isFigmaFixtureState } from './figmaFixture';

describe('Figma Fixture 查询参数', () => {
  it('同时兼容旧版和当前验收参数', () => {
    expect(isFigmaFixtureState('v2')).toBe(true);
    expect(isFigmaFixtureState('figma-v2')).toBe(true);
    expect(isFigmaFixtureState('default')).toBe(false);
    expect(isFigmaFixtureState(null)).toBe(false);
  });
});
