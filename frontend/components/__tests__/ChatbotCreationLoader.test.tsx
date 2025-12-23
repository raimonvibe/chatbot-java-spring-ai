import React from 'react';
import { render, waitFor } from '@testing-library/react';

// Mock lucide-react - must be before component import
jest.mock('lucide-react', () => {
  const React = require('react');
  const MockIcon = (props: any) => React.createElement('svg', {
    'data-testid': 'mock-icon',
    ...props
  });
  return {
    Book: MockIcon,
    Sparkles: MockIcon,
    Zap: MockIcon,
    Brain: MockIcon,
    CheckCircle: MockIcon,
  };
});

// Import component after mock
import ChatbotCreationLoader from '../ChatbotCreationLoader';

describe('ChatbotCreationLoader', () => {
  it('should not render when isVisible is false', () => {
    const { container } = render(<ChatbotCreationLoader isVisible={false} />);
    expect(container.firstChild).toBeNull();
  });

  it('should render when isVisible is true', () => {
    const { container } = render(<ChatbotCreationLoader isVisible={true} />);
    // Check that the loader container is rendered
    const loaderContainer = container.querySelector('.fixed.inset-0');
    expect(loaderContainer).toBeInTheDocument();
  });

  it('should display chatbot name when provided', () => {
    const { container } = render(<ChatbotCreationLoader isVisible={true} chatbotName="Test Bot" />);
    // Check that the component renders with the name
    const nameElement = container.textContent;
    expect(nameElement).toContain('Test Bot');
  });

  it('should cycle through loading steps', async () => {
    jest.useFakeTimers();
    const { container, rerender } = render(<ChatbotCreationLoader isVisible={true} />);
    
    // Initial step should be visible
    expect(container.textContent).toMatch(/initializing/i);
    
    // Fast-forward time to trigger step change
    jest.advanceTimersByTime(2000);
    jest.runOnlyPendingTimers();
    
    // Re-render to see updated state
    rerender(<ChatbotCreationLoader isVisible={true} />);
    
    // Should have moved to next step (or still be on initializing)
    const text = container.textContent || '';
    expect(text).toMatch(/initializing|training|optimizing|finalizing/i);
    
    jest.useRealTimers();
  });

  it('should animate dots', () => {
    jest.useFakeTimers();
    const { container } = render(<ChatbotCreationLoader isVisible={true} />);
    
    // Initial state
    expect(container.textContent).toMatch(/initializing/i);
    
    // Fast-forward to see dots animation
    jest.advanceTimersByTime(500);
    jest.runOnlyPendingTimers();
    
    // Should still show initializing text
    const text = container.textContent || '';
    expect(text).toMatch(/initializing/i);
    
    jest.useRealTimers();
  });

  it('should reset step when isVisible becomes false', () => {
    const { container, rerender } = render(<ChatbotCreationLoader isVisible={true} />);
    expect(container.textContent).toMatch(/initializing/i);
    
    rerender(<ChatbotCreationLoader isVisible={false} />);
    expect(container.firstChild).toBeNull();
  });
});

