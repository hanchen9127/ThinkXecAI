import { defineConfig } from 'eslint/config'
import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import prettierConfig from 'eslint-config-prettier'

export default defineConfig(
  { ignores: ['dist', 'src/api/schema.d.ts'] },
  {
    extends: [js.configs.recommended, tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...reactRefresh.configs.vite.rules,
      // Sizes, radii, and spacing come from the @theme names in index.css, not bracketed values
      // such as `pb-[18px]`, so the V1 look is decided in one place.
      'no-restricted-syntax': [
        'error',
        {
          selector:
            'Literal[value=/[\\w:]-\\[[^\\]]*\\]/], TemplateElement[value.raw=/[\\w:]-\\[[^\\]]*\\]/]',
          message: 'Use a theme name from index.css instead of a bracketed Tailwind value.',
        },
      ],
      // katex and mermaid carry advisories without a fix in Ant Design X's ranges; neither is
      // bundled unless one of these is imported (ADR 051).
      'no-restricted-imports': [
        'error',
        {
          paths: [
            { name: 'katex', message: 'Not used: open advisories (ADR 051).' },
            { name: 'mermaid', message: 'Not used: open advisories (ADR 051).' },
            {
              name: '@ant-design/x',
              importNames: ['Mermaid'],
              message: 'Not used: mermaid has open advisories (ADR 051).',
            },
          ],
          patterns: [
            {
              group: ['@ant-design/x-markdown/plugins/*', '@ant-design/x/*/mermaid*'],
              message: 'Not used: katex and mermaid have open advisories (ADR 051).',
            },
          ],
        },
      ],
    },
  },
  prettierConfig,
)
