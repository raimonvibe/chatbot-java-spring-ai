import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './pages/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
    './app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#fdf8f3',
          100: '#f5ebdc',
          200: '#dec9b5',
          300: '#c9a882',
          400: '#b8905a',
          500: '#8b5a3c',
          600: '#6d4530',
          700: '#583628',
          800: '#3e2723',
          900: '#2d1b16',
        },
        brown: {
          50: '#fdf8f3',
          100: '#f5ebdc',
          200: '#dec9b5',
          300: '#d2b48c',
          400: '#c9a882',
          500: '#a67c52',
          600: '#8b5a3c',
          700: '#6d4530',
          800: '#4a2f1f',
          900: '#3e2723',
        },
        gold: {
          50: '#fefce8',
          100: '#fef9c3',
          200: '#fef08a',
          300: '#fde047',
          400: '#facc15',
          500: '#eab308',
          600: '#ca8a04',
          700: '#a16207',
          800: '#854d0e',
          900: '#713f12',
        },
      },
      animation: {
        'wave': 'wave 3s ease-in-out infinite',
        'fade-in': 'fadeIn 0.5s ease-in',
        'slide-up': 'slideUp 0.3s ease-out',
      },
      keyframes: {
        wave: {
          '0%, 100%': { transform: 'translateX(0)' },
          '50%': { transform: 'translateX(-25%)' },
        },
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { transform: 'translateY(10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};

export default config;
