import animate from 'tailwindcss-animate'

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#E1251B',
          dark: '#B81B12',
          soft: '#FFF1EF',
        },
        page: {
          DEFAULT: '#F2F4F6',
          card: '#FFFFFF',
          subtle: '#F8F9FB',
        },
        ink: {
          900: '#15181D',
          600: '#5B6470',
          // 辅助文字对比度 ≈4.8:1（原 #8A919C 仅 3.4:1，小字号不达 AA）
          400: '#717885',
          0: '#FFFFFF',
        },
        state: {
          success: '#0F9D6E',
          warning: '#F08C00',
          danger: '#D93A44',
          info: '#2F6BFF',
          line: '#E7E9ED',
        },
      },
      fontFamily: {
        sans: [
          'PingFang SC',
          'Hiragino Sans GB',
          'Microsoft YaHei',
          'system-ui',
          '-apple-system',
          'sans-serif',
        ],
      },
      fontSize: {
        heading: ['28px', { lineHeight: '36px', fontWeight: '800' }],
        subheading: ['16px', { lineHeight: '24px', fontWeight: '700' }],
        body: ['14px', { lineHeight: '22px' }],
      },
      boxShadow: {
        card: '0 1px 2px rgba(21, 24, 29, 0.04), 0 4px 16px -8px rgba(21, 24, 29, 0.10)',
        'card-hover': '0 2px 4px rgba(21, 24, 29, 0.05), 0 16px 32px -16px rgba(21, 24, 29, 0.20)',
        float: '0 12px 40px -16px rgba(21, 24, 29, 0.28)',
      },
      zIndex: {
        nav: '40',
        overlay: '50',
        toast: '60',
      },
    },
  },
  plugins: [animate],
}
