import { render } from '@testing-library/react';
import ChatbotCreationLoader from '../ChatbotCreationLoader';

// Mock lucide-react icons for this test file only
jest.mock('lucide-react', () => {
  const React = require('react')
  return {
    Book: (props: any) => React.createElement('svg', { ...props, 'data-testid': 'book-icon' }),
    Sparkles: (props: any) => React.createElement('svg', { ...props, 'data-testid': 'sparkles-icon' }),
    Zap: (props: any) => React.createElement('svg', { ...props, 'data-testid': 'zap-icon' }),
    Brain: (props: any) => React.createElement('svg', { ...props, 'data-testid': 'brain-icon' }),
    CheckCircle: (props: any) => React.createElement('svg', { ...props, 'data-testid': 'checkcircle-icon' }),
  }
})

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
    const { container } = render(<ChatbotCreationLoader isVisible={true} />);
    
    // Initial step should be visible
    expect(container.textContent).toMatch(/initializing/i);
    
    // Fast-forward time to trigger step change
    jest.advanceTimersByTime(2000);
    
    await waitFor(() => {
      // Should have moved to next step
      expect(container.textContent).toMatch(/training|optimizing|finalizing/i);
    });
    
    jest.useRealTimers();
  });

  it('should animate dots', async () => {
    jest.useFakeTimers();
    const { container } = render(<ChatbotCreationLoader isVisible={true} />);
    
    // Fast-forward to see dots animation
    jest.advanceTimersByTime(500);
    
    await waitFor(() => {
      const text = container.textContent || '';
      expect(text).toMatch(/initializing.*\./i);
    });
    
    jest.useRealTimers();
  });

  it('should reset step when isVisible becomes false', () => {
    const { container, rerender } = render(<ChatbotCreationLoader isVisible={true} />);
    expect(container.textContent).toMatch(/initializing/i);
    
    rerender(<ChatbotCreationLoader isVisible={false} />);
    expect(container.firstChild).toBeNull();
  });
});

