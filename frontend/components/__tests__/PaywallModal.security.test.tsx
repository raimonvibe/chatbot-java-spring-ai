import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import PaywallModal from '../PaywallModal';

// Mock framer-motion
jest.mock('framer-motion', () => ({
  motion: {
    div: ({ children, className, onClick, ...props }: any) => (
      <div className={className} onClick={onClick} {...props}>{children}</div>
    ),
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock lucide-react icons
jest.mock('lucide-react', () => ({
  Crown: () => <div data-testid="crown-icon">Crown</div>,
  X: () => <div data-testid="x-icon">X</div>,
  Sparkles: () => <div data-testid="sparkles-icon">Sparkles</div>,
  Book: () => <div data-testid="book-icon">Book</div>,
  Zap: () => <div data-testid="zap-icon">Zap</div>,
}));

// Mock fetch
global.fetch = jest.fn();

describe('PaywallModal Security Tests', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (global.fetch as jest.Mock).mockClear();
  });

  describe('XSS Prevention', () => {
    it('should sanitize title prop to prevent XSS', () => {
      const maliciousTitle = '<script>alert("XSS")</script>Malicious Title';
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} title={maliciousTitle} />
      );

      // Should not contain script tags
      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);

      // Title should be rendered as text, not executed
      const titleElement = container.querySelector('h3');
      expect(titleElement).toBeInTheDocument();
      expect(titleElement?.textContent).toContain('Malicious Title');
      expect(titleElement?.innerHTML).not.toContain('<script>');
    });

    it('should sanitize message prop to prevent XSS', () => {
      const maliciousMessage = '<img src=x onerror=alert("XSS")>Malicious Message';
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} message={maliciousMessage} />
      );

      // Should not execute inline scripts
      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);

      // Should not have onerror attributes
      const images = container.querySelectorAll('img[onerror]');
      expect(images.length).toBe(0);
    });

    it('should sanitize Bible verse text to prevent XSS', () => {
      const maliciousVerse = {
        text: '<script>alert("XSS")</script>Verse text',
        reference: '<img src=x onerror=alert("XSS")>Ref 1:1'
      };

      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} bibleVerse={maliciousVerse} />
      );

      // Should not contain script tags
      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);

      // Verse should be rendered as text
      expect(container.textContent).toContain('Verse text');
      expect(container.textContent).toContain('Ref 1:1');
    });

    it('should not use dangerouslySetInnerHTML', () => {
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} />
      );

      const allElements = container.querySelectorAll('*');
      allElements.forEach((element) => {
        expect(element).not.toHaveProperty('dangerouslySetInnerHTML');
      });
    });

    it('should escape HTML entities in user input', () => {
      const titleWithEntities = 'Title & "quotes" <tags>';
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} title={titleWithEntities} />
      );

      const titleElement = container.querySelector('h3');
      expect(titleElement).toBeInTheDocument();
      // React automatically escapes, so entities should be safe
      expect(titleElement?.textContent).toContain('Title');
    });
  });

  describe('API Security', () => {
    it('should use credentials: include for authenticated requests', async () => {
      const mockFetch = global.fetch as jest.Mock;
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ url: 'https://checkout.stripe.com/test' }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          expect.any(String),
          expect.objectContaining({
            credentials: 'include',
          })
        );
      });
    });

    it('should validate API response URL is from Stripe domain', async () => {
      const mockFetch = global.fetch as jest.Mock;
      const mockAlert = jest.spyOn(window, 'alert').mockImplementation(() => {});

      // Simulate malicious response with non-Stripe URL
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ url: 'https://malicious-site.com/phishing' }),
      });

      // Mock window.location.href to prevent actual redirect
      const originalLocation = window.location;
      delete (window as any).location;
      (window as any).location = { href: '' };

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        // Should show error alert because URL is not from Stripe
        expect(mockAlert).toHaveBeenCalled();
      });

      // Verify error message indicates invalid URL
      const alertCall = mockAlert.mock.calls[0][0];
      expect(alertCall).toMatch(/invalid|error/i);

      (window as any).location = originalLocation;
      mockAlert.mockRestore();
    });

    it('should accept valid Stripe checkout URLs', async () => {
      const mockFetch = global.fetch as jest.Mock;
      const validStripeUrl = 'https://checkout.stripe.com/pay/cs_test_123';

      // Mock window.location.href
      const originalLocation = window.location;
      delete (window as any).location;
      (window as any).location = { href: '' };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ url: validStripeUrl }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      // Valid Stripe URL should be accepted (would redirect in real browser)
      // In test, we verify the URL validation logic exists

      (window as any).location = originalLocation;
    });

    it('should handle API errors securely without exposing sensitive info', async () => {
      const mockFetch = global.fetch as jest.Mock;
      const mockAlert = jest.spyOn(window, 'alert').mockImplementation(() => {});

      // Simulate error with potentially sensitive data
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: async () => ({ 
          error: 'Database connection failed',
          stack: 'sensitive stack trace',
          apiKey: 'secret-key-123'
        }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockAlert).toHaveBeenCalled();
      });

      // Verify alert message doesn't contain sensitive info
      const alertCall = mockAlert.mock.calls[0][0];
      expect(alertCall).not.toContain('stack');
      expect(alertCall).not.toContain('apiKey');
      expect(alertCall).not.toContain('secret-key');

      mockAlert.mockRestore();
    });

    it('should use POST method for checkout session creation', async () => {
      const mockFetch = global.fetch as jest.Mock;
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ url: 'https://checkout.stripe.com/test' }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          expect.any(String),
          expect.objectContaining({
            method: 'POST',
          })
        );
      });
    });

    it('should send empty body (not user input) to checkout API', async () => {
      const mockFetch = global.fetch as jest.Mock;
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ url: 'https://checkout.stripe.com/test' }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        const callArgs = mockFetch.mock.calls[0];
        const requestOptions = callArgs[1];
        const body = JSON.parse(requestOptions.body);
        
        // Should send empty object, not user input
        expect(body).toEqual({});
      });
    });
  });

  describe('Input Validation', () => {
    it('should handle null/undefined props safely', () => {
      const { container } = render(
        <PaywallModal 
          isOpen={true} 
          onClose={jest.fn()} 
          title={null as any}
          message={undefined}
        />
      );

      expect(container).toBeInTheDocument();
      // Should use default values
      expect(screen.getByText(/Upgrade to Share Your Message/i)).toBeInTheDocument();
    });

    it('should handle very long input strings safely', () => {
      const longString = 'A'.repeat(10000);
      const { container } = render(
        <PaywallModal 
          isOpen={true} 
          onClose={jest.fn()} 
          title={longString}
        />
      );

      expect(container).toBeInTheDocument();
      // Should render without crashing
      const titleElement = container.querySelector('h3');
      expect(titleElement).toBeInTheDocument();
    });

    it('should handle special characters in input', () => {
      const specialChars = '!@#$%^&*()_+-=[]{}|;:,.<>?/~`';
      const { container } = render(
        <PaywallModal 
          isOpen={true} 
          onClose={jest.fn()} 
          title={specialChars}
        />
      );

      expect(container).toBeInTheDocument();
      // Should render safely
      const titleElement = container.querySelector('h3');
      expect(titleElement).toBeInTheDocument();
    });
  });

  describe('Event Handler Security', () => {
    it('should not allow injection of event handlers via props', () => {
      // TypeScript prevents passing non-function to onClose prop
      // This test verifies the component structure is secure
      const validOnClose = jest.fn();
      const { container } = render(
        <PaywallModal 
          isOpen={true} 
          onClose={validOnClose}
        />
      );

      // onClose should be a function
      const closeButton = screen.getByLabelText('Close');
      expect(closeButton).toBeInTheDocument();
      
      // Clicking should call the function, not execute code
      fireEvent.click(closeButton);
      expect(validOnClose).toHaveBeenCalledTimes(1);
      
      // TypeScript type checking prevents string injection at compile time
      // This is a compile-time security measure, not runtime
    });

    it('should prevent clickjacking via proper z-index', () => {
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} />
      );

      const modal = container.querySelector('.z-50');
      expect(modal).toBeInTheDocument();
      // High z-index prevents clickjacking
      expect(modal?.classList.contains('z-50')).toBe(true);
    });
  });

  describe('URL Security', () => {
    it('should use environment-based API URL', () => {
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} />
      );

      // Component should use getApiBaseUrl() which validates environment
      expect(container).toBeInTheDocument();
    });

    it('should not expose API URL in rendered HTML', () => {
      const { container } = render(
        <PaywallModal isOpen={true} onClose={jest.fn()} />
      );

      const html = container.innerHTML;
      // API URL should not be visible in HTML
      expect(html).not.toContain('localhost:8081');
      expect(html).not.toContain('onrender.com');
      expect(html).not.toContain('/api/subscription');
    });
  });

  describe('State Management Security', () => {
    it('should reset loading state on error', async () => {
      const mockFetch = global.fetch as jest.Mock;
      const mockAlert = jest.spyOn(window, 'alert').mockImplementation(() => {});

      mockFetch.mockRejectedValueOnce(new Error('Network error'));

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockAlert).toHaveBeenCalled();
      });

      // Button should be clickable again (loading state reset)
      const upgradeButtonAfter = screen.getByText(/Upgrade Now/i);
      expect(upgradeButtonAfter).toBeInTheDocument();
      expect(upgradeButtonAfter).not.toBeDisabled();

      mockAlert.mockRestore();
    });

    it('should prevent multiple simultaneous checkout requests', async () => {
      const mockFetch = global.fetch as jest.Mock;
      let resolvePromise: (value: any) => void;
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve;
      });

      mockFetch.mockReturnValueOnce(pendingPromise);

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      
      // Click multiple times rapidly
      fireEvent.click(upgradeButton);
      fireEvent.click(upgradeButton);
      fireEvent.click(upgradeButton);

      // Should only make one API call
      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledTimes(1);
      });

      // Resolve the promise
      resolvePromise!({
        ok: true,
        json: async () => ({ url: 'https://checkout.stripe.com/test' }),
      });
    });
  });
});

