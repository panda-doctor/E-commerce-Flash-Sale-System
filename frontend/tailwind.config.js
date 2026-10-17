import animate from 'tailwindcss-animate'

/**
 * 暖色电商品牌令牌（全站唯一色源）。
 *
 * 色彩结构：
 * - 奶油白画布 + 白色内容面 + 浅橙/浅红局部区域，形成"暖色层次"而不是整页灰白；
 * - 品牌红 #E53935 用于价格等大字号促销信息，实心按钮用 #D32F2F（白字 4.98:1 达 AA）；
 * - 橙 #FF8A34 / 珊瑚 #FF6548 只做装饰、渐变与局部强调，不承载小字号白字；
 * - 语义色只表达真实状态（成功/警告/失败），不参与品牌装饰。
 *
 * @type {import('tailwindcss').Config}
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#E53935',
          // 实心按钮/选中态：白字 4.98:1
          solid: '#D32F2F',
          // hover 与小字号强调：白底 5.62:1
          dark: '#C62828',
          deep: '#B71C1C',
          coral: '#FF6548',
          orange: '#FF8A34',
          // 浅红底（徽标/价格辅助/状态提示）
          soft: '#FFE7E5',
          // 浅橙底（商品区/活动区局部背景）
          cream: '#FFF0E3',
          tint: '#FFF1EC',
        },
        page: {
          DEFAULT: '#FFF7F0',
          card: '#FFFFFF',
          subtle: '#FFF0E5',
        },
        ink: {
          900: '#24202A',
          600: '#6B6470',
          // 仅占位符/图标/禁用态
          400: '#8A8490',
          0: '#FFFFFF',
        },
        state: {
          success: '#12724E',
          warning: '#9A5B08',
          danger: '#C42A1F',
          info: '#5B5560',
          line: '#F0E1DA',
          'line-strong': '#E6D0C5',
          hover: '#FFC7B6',
        },
        footer: {
          DEFAULT: '#2A1B19',
          text: '#F7E9E4',
          muted: '#C9B4AC',
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
      backgroundImage: {
        hero: 'linear-gradient(115deg, #B71C1C 0%, #C62828 48%, #E53935 76%, #FF6548 100%)',
        'hero-card': 'linear-gradient(150deg, #FFF7F1 0%, #FFFFFF 45%, #FFF1EC 100%)',
        'image-warm': 'linear-gradient(135deg, #FFF4EA 0%, #FFF1EC 55%, #FFE7E5 100%)',
      },
      boxShadow: {
        card: '0 2px 10px rgba(150, 70, 40, 0.06)',
        'card-hover': '0 12px 28px rgba(180, 70, 40, 0.12)',
        float: '0 16px 40px rgba(120, 40, 20, 0.18)',
        'hero-card': '0 18px 44px rgba(80, 20, 10, 0.22)',
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
