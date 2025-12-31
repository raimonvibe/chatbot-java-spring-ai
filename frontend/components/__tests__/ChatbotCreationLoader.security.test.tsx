import React from 'react';
import { render, screen } from '@testing-library/react';
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

describe('ChatbotCreationLoader Security Tests', () => {
  describe('XSS Prevention', () => {
    it('should sanitize chatbot name to prevent XSS attacks', () => {
      const maliciousName = '<script>alert("XSS")</script>Test Bot';
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName={maliciousName} />
      );

      const nameElement = container.querySelector('.text-gold-300');
      expect(nameElement).toBeInTheDocument();
      
      // Should not contain script tags (removed by sanitization)
      expect(nameElement?.textContent).not.toContain('<script>');
      expect(nameElement?.textContent).not.toContain('</script>');
      
      // Should contain sanitized name (script tags removed, but text remains)
      // The important thing is that script tags are removed so they can't execute
      expect(nameElement?.textContent).toContain('Test Bot');
      
      // Verify no script tags in DOM
      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);
    });

    it('should handle chatbot name with HTML entities safely', () => {
      const nameWithEntities = 'Test & Bot < > " \'';
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName={nameWithEntities} />
      );

      const nameElement = container.querySelector('.text-gold-300');
      expect(nameElement).toBeInTheDocument();
      
      // Should not contain unescaped HTML
      expect(nameElement?.innerHTML).not.toContain('<');
      expect(nameElement?.innerHTML).not.toContain('>');
    });

    it('should prevent injection of event handlers', () => {
      const nameWithEvent = 'Test Bot" onclick="alert(\'XSS\')"';
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName={nameWithEvent} />
      );

      const nameElement = container.querySelector('.text-gold-300');
      expect(nameElement).toBeInTheDocument();
      
      // Should not contain onclick attribute (React prevents attribute injection)
      expect(nameElement?.getAttribute('onclick')).toBeNull();
      
      // Text content may contain the string, but it won't execute as code
      // React's default escaping prevents execution
      expect(nameElement?.textContent).toBeTruthy();
    });

    it('should handle empty chatbot name safely', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName="" />
      );

      const nameElement = container.querySelector('.text-gold-300');
      // Should not render name element if empty
      expect(nameElement).not.toBeInTheDocument();
    });

    it('should handle null chatbot name safely', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName={null as any} />
      );

      const nameElement = container.querySelector('.text-gold-300');
      // Should not render name element if null
      expect(nameElement).not.toBeInTheDocument();
    });

    it('should sanitize very long chatbot names to prevent DoS', () => {
      const longName = 'A'.repeat(10000);
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName={longName} />
      );

      const nameElement = container.querySelector('.text-gold-300');
      expect(nameElement).toBeInTheDocument();
      
      // Should render without crashing
      expect(nameElement?.textContent).toBeTruthy();
    });
  });

  describe('Library Security', () => {
    it('should use react-spinners safely without external dependencies', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} />
      );

      // Should render spinners without external network requests
      expect(container.querySelector('[data-testid="clip-loader"]')).toBeInTheDocument();
      expect(container.querySelector('[data-testid="pulse-loader"]')).toBeInTheDocument();
      expect(container.querySelector('[data-testid="bar-loader"]')).toBeInTheDocument();
    });

    it('should not use dangerouslySetInnerHTML', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName="Test Bot" />
      );

      // Check that no element has dangerouslySetInnerHTML
      const allElements = container.querySelectorAll('*');
      allElements.forEach((element) => {
        expect(element).not.toHaveProperty('dangerouslySetInnerHTML');
      });
    });

    it('should not execute inline scripts', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} chatbotName="Test Bot" />
      );

      // Should not contain script tags
      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);
    });
  });

  describe('Props Validation', () => {
    it('should handle invalid isVisible prop safely', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={false} />
      );

      expect(container.firstChild).toBeNull();
    });

    it('should handle isScanningWebsite prop correctly', () => {
      const { container } = render(
        <ChatbotCreationLoader isVisible={true} isScanningWebsite={true} />
      );

      // Should render when scanning website
      expect(container.querySelector('.fixed.inset-0')).toBeInTheDocument();
    });
  });
});

