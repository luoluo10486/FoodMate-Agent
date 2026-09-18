import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import prettier from 'eslint-plugin-prettier';
import prettierConfig from 'eslint-config-prettier';

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      prettier,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-hooks/set-state-in-effect': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      'prettier/prettier': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: [
      'src/pages/**/*.{ts,tsx}',
      'src/layouts/**/*.{ts,tsx}',
      'src/components/agent/**/*.{ts,tsx}',
      'src/components/brand/**/*.{ts,tsx}',
      'src/components/common/**/*.{ts,tsx}',
      'src/components/planning/**/*.{ts,tsx}',
      'src/components/workspace/**/*.{ts,tsx}',
    ],
    ignores: ['src/components/ui/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: 'JSXOpeningElement[name.name="button"]',
          message: '页面交互按钮必须使用 src/components/ui/button.tsx 中的 Button。',
        },
        {
          selector: 'JSXOpeningElement[name.name="input"]',
          message: '页面输入控件必须使用 src/components/ui/input.tsx 中的 Input。',
        },
        {
          selector: 'JSXOpeningElement[name.name="select"]',
          message: '页面选择控件必须使用 src/components/ui/select.tsx 中的 Select。',
        },
        {
          selector: 'JSXOpeningElement[name.name="textarea"]',
          message: '页面多行输入控件必须使用 src/components/ui/textarea.tsx 中的 Textarea。',
        },
      ],
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@radix-ui/*'],
              message: '页面和业务组件必须通过 src/components/ui 使用 Radix，不得直接引入底层包。',
            },
          ],
        },
      ],
    },
  },
  prettierConfig,
);
