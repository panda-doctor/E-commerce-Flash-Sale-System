import animate from 'tailwindcss-animate'

/**
 * 设计令牌（全站唯一色源）。
 *
 * 配色原则：
 * - 中性色承载结构：页面 #F6F7F9、表面 #FFF、次级表面 #F0F2F5、边框 #E5E7EB；
 * - 文字三级：#202124（主）/ #667085（次）/ #98A2B3（弱，仅非关键信息）；
 * - 秒杀红 #E5484D 只用于「价格、主操作、促销标记」，不用于导航、分页、普通链接与装饰；
 * - 语义色只表达真实业务状态：成功 #16865C、警告 #B86E08、危险 #D92D20；
 *   info 收敛为中性深灰 #475467，避免蓝色成为第二个装饰强调色。
 *
 * @type {import('tailwindcss').Config}
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          // 与建议色 #E5484D 同色相，下压 1 档使「白字/红底」与「红字/白底」都过 AA（4.5:1）
          DEFAULT: '#D93A40',
          dark: '#C22F36',
          // 浅红底上的小字号文字（徽标/角标），对比度 5.9:1
          ink: '#B3252B',
          soft: '#FFF1F0',
        },
        page: {
          DEFAULT: '#F6F7F9',
          card: '#FFFFFF',
          subtle: '#F0F2F5',
        },
        ink: {
          900: '#202124',
          // 次级文字：在白卡/页面底/次级面上均 ≥5.1:1
          600: '#5D6675',
          // 弱化文字：仅占位符、图标与禁用态，不承载关键信息
          400: '#98A2B3',
          0: '#FFFFFF',
        },
        state: {
          success: '#12724E',
          warning: '#9A5B08',
          danger: '#C42A1F',
          info: '#475467',
          line: '#E5E7EB',
          'line-strong': '#D0D5DD',
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
        card: '0 2px 10px rgba(16, 24, 40, 0.04)',
        'card-hover': '0 8px 24px rgba(16, 24, 40, 0.08)',
        float: '0 12px 32px rgba(16, 24, 40, 0.12)',
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
