import '@testing-library/jest-dom';

// jsdom 未实现 scrollTo / scroll，多个组件依赖
window.scrollTo = () => {};
Element.prototype.scrollTo = () => {};

// jsdom 未实现 matchMedia，多个组件依赖它判断动画偏好
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
