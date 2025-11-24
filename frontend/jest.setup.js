require('@testing-library/jest-dom')

// Mock framer-motion to avoid issues with animations in tests
jest.mock('framer-motion', () => {
  const React = require('react')
  const filterProps = (props) => {
    const { animate, initial, transition, whileHover, whileTap, whileFocus, exit, ...domProps } = props
    return domProps
  }
  return {
    ...jest.requireActual('framer-motion'),
    motion: {
      div: React.forwardRef((props, ref) => React.createElement('div', { ...filterProps(props), ref })),
      button: React.forwardRef((props, ref) => React.createElement('button', { ...filterProps(props), ref })),
      input: React.forwardRef((props, ref) => React.createElement('input', { ...filterProps(props), ref })),
      p: React.forwardRef((props, ref) => React.createElement('p', { ...filterProps(props), ref })),
      time: React.forwardRef((props, ref) => React.createElement('time', { ...filterProps(props), ref })),
    },
    AnimatePresence: ({ children }) => children,
  }
})

// Mock Next.js router
jest.mock('next/navigation', () => ({
  useRouter: () => ({
    push: jest.fn(),
    replace: jest.fn(),
    prefetch: jest.fn(),
    back: jest.fn(),
    pathname: '/',
    query: {},
  }),
  usePathname: () => '/',
  useSearchParams: () => new URLSearchParams(),
}))

// Setup global fetch mock
global.fetch = jest.fn()

// Mock scrollIntoView
Element.prototype.scrollIntoView = jest.fn()
