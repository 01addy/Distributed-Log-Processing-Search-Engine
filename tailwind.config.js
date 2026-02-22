export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Syne"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
        body: ['"DM Sans"', 'sans-serif'],
      },
      colors: {
        void: '#080B14',
        surface: '#0D1117',
        panel: '#111827',
        border: '#1E2D40',
        accent: '#00D4FF',
        success: '#00FF88',
        warning: '#FFB800',
        danger: '#FF3B5C',
        muted: '#4A5568',
        subtle: '#2D3748',
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-in': 'fadeIn 0.5s ease forwards',
        'slide-up': 'slideUp 0.4s ease forwards',
        'glow': 'glow 2s ease-in-out infinite alternate',
      },
      keyframes: {
        fadeIn: { from: { opacity: 0 }, to: { opacity: 1 } },
        slideUp: { from: { opacity: 0, transform: 'translateY(16px)' }, to: { opacity: 1, transform: 'translateY(0)' } },
        glow: { from: { boxShadow: '0 0 5px #00D4FF33' }, to: { boxShadow: '0 0 20px #00D4FF66' } },
      }
    },
  },
  plugins: [],
}
