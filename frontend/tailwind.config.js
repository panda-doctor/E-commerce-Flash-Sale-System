/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#E1251B',
          dark: '#C81E14',
          soft: '#FDECEA',
        },
        page: {
          DEFAULT: '#F5F6F7',
          card: '#FFFFFF',
          subtle: '#FAFAFB',
        },
        ink: {
          900: '#1F2329',
          600: '#646A73',
          400: '#8F959E',
          0: '#FFFFFF',
        },
        state: {
          success: '#00A870',
          warning: '#FF9800',
          danger: '#E34D59',
          info: '#2F6BFF',
          line: '#EBEDF0',
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
        heading: ['28px', { lineHeight: '36px', fontWeight: '700' }],
        subheading: ['16px', { lineHeight: '24px', fontWeight: '600' }],
        body: ['14px', { lineHeight: '22px' }],
      },
      boxShadow: {
        card: '0 1px 2px rgba(31, 35, 41, 0.04), 0 8px 24px -12px rgba(31, 35, 41, 0.12)',
      },
      zIndex: {
        nav: '40',
        overlay: '50',
        toast: '60',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],
}
