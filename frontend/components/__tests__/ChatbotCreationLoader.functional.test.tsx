import React from 'react';
import { render, waitFor, act } from '@testing-library/react';
import ChatbotCreationLoader from '../ChatbotCreationLoader';

// Mock react-spinners
jest.mock('react-spinners', () => ({
  ClipLoader: ({ color, size }: { color: string; size: number }) => (
    <div data-testid="clip-loader" data-color={color} data-size={size}>Loading...</div>
  ),
  PulseLoader: ({ color, size }: { color: string; size: number }) => (
    <div data-testid="pulse-loader" data-color={color} data-size={size}>Loading...</div>
  ),
  BarLoader: ({ color, height }: { color: string; height: number }) => (
    <div data-testid="bar-loader" data-color={color} data-height={height}>Loading...</div>
  ),
}));

// Mock framer-motion
jest.mock('framer-motion', () => ({
  motion: {
    div: ({ children, className, ...props }: any) => <div className={className} {...props}>{children}</div>,
    h2: ({ children, className, ...props }: any) => <h2 className={className} {...props}>{children}</h2>,
    p: ({ children, className, ...props }: any) => <p className={className} {...props}>{children}</p>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock lucide-react icons
jest.mock('lucide-react', () => ({
  Sparkles: () => <div data-testid="sparkles-icon">Sparkles</div>,
  Brain: () => <div data-testid="brain-icon">Brain</div>,
  Zap: () => <div data-testid="zap-icon">Zap</div>,
  Book: () => <div data-testid="book-icon">Book</div>,
}));

describe('ChatbotCreationLoader Functional Tests', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('Visibility', () => {
    it('should not render when isVisible is false', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={false} />);
      expect(container.firstChild).toBeNull();
    });

    it('should render when isVisible is true', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      const loaderContainer = container.querySelector('.fixed.inset-0');
      expect(loaderContainer).toBeInTheDocument();
    });

    it('should hide when isVisible changes from true to false', () => {
      const { container, rerender } = render(
        <ChatbotCreationLoader isVisible={true} />
      );
      expect(container.querySelector('.fixed.inset-0')).toBeInTheDocument();

      rerender(<ChatbotCreationLoader isVisible={false} />);
      expect(container.firstChild).toBeNull();
    });
  });

  describe('Chatbot Name Display', () => {
    it('should display chatbot name when provided', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName="Test Bot" />
      );
      const nameElement = container.querySelector('.text-gold-300');
      expect(nameElement).toBeInTheDocument();
      expect(nameElement?.textContent).toContain('Test Bot');
    });

    it('should not display name element when chatbotName is not provided', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      const nameElement = container.querySelector('.text-gold-300');
      expect(nameElement).not.toBeInTheDocument();
    });
  });

  describe('Loading Steps', () => {
    it('should cycle through loading steps', async () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      
      // Initial step should be visible
      expect(container.textContent).toMatch(/initializing/i);
      
      // Advance timer to next step with act()
      act(() => {
        jest.advanceTimersByTime(2000);
      });
      
      // Wait for state update
      await waitFor(() => {
        const text = container.textContent || '';
        expect(text).toMatch(/training|optimizing|finalizing/i);
      });
    });

    it('should reset step when isVisible becomes false', () => {
      const { container, rerender } = render(
        <ChatbotCreationLoader isVisible={true} />
      );
      
      // Advance timer with act()
      act(() => {
        jest.advanceTimersByTime(2000);
      });
      
      // Hide loader
      act(() => {
        rerender(<ChatbotCreationLoader isVisible={false} />);
      });
      
      // Show again - should reset to first step
      act(() => {
        rerender(<ChatbotCreationLoader isVisible={true} />);
      });
      expect(container.textContent).toMatch(/initializing/i);
    });
  });

  describe('Loading Spinners', () => {
    it('should render ClipLoader when not scanning website', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} isScanningWebsite={false} />
      );
      expect(container.querySelector('[data-testid="clip-loader"]')).toBeInTheDocument();
    });

    it('should render PulseLoader for step indicators', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      expect(container.querySelector('[data-testid="pulse-loader"]')).toBeInTheDocument();
    });

    it('should render BarLoader for progress bar', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      expect(container.querySelector('[data-testid="bar-loader"]')).toBeInTheDocument();
    });

    it('should use correct colors for spinners', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      
      const clipLoader = container.querySelector('[data-testid="clip-loader"]');
      expect(clipLoader?.getAttribute('data-color')).toBe('#d4af37');
      
      const pulseLoader = container.querySelector('[data-testid="pulse-loader"]');
      expect(pulseLoader?.getAttribute('data-color')).toBe('#d4af37');
      
      const barLoader = container.querySelector('[data-testid="bar-loader"]');
      expect(barLoader?.getAttribute('data-color')).toBe('#d4af37');
    });
  });

  describe('Website Scanning Mode', () => {
    it('should render different animation when isScanningWebsite is true', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} isScanningWebsite={true} />
      );
      
      // Should not render ClipLoader when scanning
      expect(container.querySelector('[data-testid="clip-loader"]')).not.toBeInTheDocument();
      
      // Should render scanning animation (motion.div with border)
      const scanningAnimation = container.querySelector('.border-4.border-gold-400');
      expect(scanningAnimation).toBeInTheDocument();
    });

    it('should render ClipLoader when isScanningWebsite is false', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} isScanningWebsite={false} />
      );
      
      expect(container.querySelector('[data-testid="clip-loader"]')).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper semantic structure', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      
      // Should have heading
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toContain('Creating Your Chatbot');
    });

    it('should have proper z-index for overlay', () => {
      const { container } = render(<ChatbotCreationLoader isVisible={true} />);
      
      const overlay = container.querySelector('.z-50');
      expect(overlay).toBeInTheDocument();
    });
  });

  describe('Cleanup', () => {
    it('should cleanup intervals when component unmounts', () => {
      const clearIntervalSpy = jest.spyOn(global, 'clearInterval');
      
      const { unmount } = render(<ChatbotCreationLoader isVisible={true} />);
      
      unmount();
      
      // Should have called clearInterval
      expect(clearIntervalSpy).toHaveBeenCalled();
      
      clearIntervalSpy.mockRestore();
    });

    it('should cleanup intervals when isVisible becomes false', () => {
      const clearIntervalSpy = jest.spyOn(global, 'clearInterval');
      
      const { rerender } = render(<ChatbotCreationLoader isVisible={true} />);
      
      rerender(<ChatbotCreationLoader isVisible={false} />);
      
      // Should have called clearInterval
      expect(clearIntervalSpy).toHaveBeenCalled();
      
      clearIntervalSpy.mockRestore();
    });
  });
});

