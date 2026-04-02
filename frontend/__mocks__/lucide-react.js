const React = require('react');

// Create mock icon components that React can render
const createMockIcon = (name) => {
  const Icon = (props) => React.createElement('svg', {
    'data-testid': `${name.toLowerCase()}-icon`,
    xmlns: 'http://www.w3.org/2000/svg',
    width: '24',
    height: '24',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '2',
    ...props
  });
  Icon.displayName = name;
  return Icon;
};

module.exports = {
  Book: createMockIcon('Book'),
  Sparkles: createMockIcon('Sparkles'),
  Zap: createMockIcon('Zap'),
  Brain: createMockIcon('Brain'),
  CheckCircle: createMockIcon('CheckCircle'),
  TrendingUp: createMockIcon('TrendingUp'),
  Loader2: createMockIcon('Loader2'),
  AlertCircle: createMockIcon('AlertCircle'),
  CheckCircle2: createMockIcon('CheckCircle2'),
  Send: createMockIcon('Send'),
  User: createMockIcon('User'),
};

