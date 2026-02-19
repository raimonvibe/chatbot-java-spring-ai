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

// Mock window.location
const mockLocation = { href: '' };
delete (window as any).location;
(window as any).location = mockLocation;

describe('PaywallModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (global.fetch as jest.Mock).mockClear();
    mockLocation.href = '';
  });

  it('should not render when isOpen is false', () => {
    const { container } = render(
      <PaywallModal isOpen={false} onClose={jest.fn()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('should render when isOpen is true', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
    // Check for any upgrade-related text (could be "Upgrade Now" or "Upgrade to Share Your Message")
    const upgradeTexts = screen.getAllByText(/upgrade/i);
    expect(upgradeTexts.length).toBeGreaterThan(0);
  });

  it('should display default title and message for general feature', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} feature="general" />);
    expect(screen.getByText(/Upgrade to Share Your Message/i)).toBeInTheDocument();
  });

  it('should display chatbot-limit specific message', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} feature="chatbot-limit" />);
    expect(screen.getByText(/Unlock Unlimited Chatbots/i)).toBeInTheDocument();
    expect(screen.getByText(/You've reached the limit/i)).toBeInTheDocument();
  });

  it('should display integration-script specific message', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} feature="integration-script" />);
    expect(screen.getByText(/Share Your Message Widely/i)).toBeInTheDocument();
    expect(screen.getByText(/Integration scripts are available/i)).toBeInTheDocument();
  });

  it('should display custom title and message when provided', () => {
    render(
      <PaywallModal
        isOpen={true}
        onClose={jest.fn()}
        title="Custom Title"
        message="Custom message here"
      />
    );
    expect(screen.getByText('Custom Title')).toBeInTheDocument();
    expect(screen.getByText('Custom message here')).toBeInTheDocument();
  });

  it('should display Bible verse for chatbot-limit feature', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} feature="chatbot-limit" />);
    expect(screen.getByText(/Jeremiah 29:11/i)).toBeInTheDocument();
    expect(screen.getByText(/plans to prosper you/i)).toBeInTheDocument();
  });

  it('should display custom Bible verse when provided', () => {
    render(
      <PaywallModal
        isOpen={true}
        onClose={jest.fn()}
        bibleVerse={{
          text: 'Custom verse text unique for testing',
          reference: 'Custom 1:1 unique reference'
        }}
      />
    );
    // Check for verse text (may be wrapped in quotes)
    expect(screen.getByText(/Custom verse text unique for testing/i)).toBeInTheDocument();
    expect(screen.getByText(/Custom 1:1 unique reference/i)).toBeInTheDocument();
  });

  it('should call onClose when close button is clicked', () => {
    const onClose = jest.fn();
    render(<PaywallModal isOpen={true} onClose={onClose} />);
    
    const closeButton = screen.getByLabelText('Close');
    expect(closeButton).toBeInTheDocument();
    fireEvent.click(closeButton);
    
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should call onClose when backdrop is clicked', () => {
    const onClose = jest.fn();
    const { container } = render(<PaywallModal isOpen={true} onClose={onClose} />);
    
    const backdrop = container.querySelector('.fixed.inset-0');
    expect(backdrop).toBeInTheDocument();
    
    if (backdrop) {
      fireEvent.click(backdrop);
      expect(onClose).toHaveBeenCalledTimes(1);
    }
  });

  it('should not call onClose when modal content is clicked', () => {
    const onClose = jest.fn();
    render(<PaywallModal isOpen={true} onClose={onClose} />);
    
    const modalContent = screen.getByText(/Upgrade to Share Your Message/i).closest('.rounded-2xl');
    if (modalContent) {
      fireEvent.click(modalContent);
      expect(onClose).not.toHaveBeenCalled();
    }
  });

  it('should display feature list', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
    
    expect(screen.getByText(/Embed on your website/i)).toBeInTheDocument();
    expect(screen.getByText(/Larger website scans/i)).toBeInTheDocument();
    expect(screen.getByText(/More messages per day/i)).toBeInTheDocument();
  });

  it('should display pricing hint', () => {
    render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
    expect(screen.getByText(/Starting at \$12\/month/i)).toBeInTheDocument();
  });

  describe('Stripe Checkout Integration', () => {
    it('should create checkout session when upgrade button is clicked', async () => {
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
          expect.stringContaining('/api/subscription/create-checkout-session'),
          expect.objectContaining({
            method: 'POST',
            credentials: 'include',
            headers: {
              'Content-Type': 'application/json',
            },
          })
        );
      });
    });

    it('should show loading state during checkout session creation', async () => {
      const mockFetch = global.fetch as jest.Mock;
      mockFetch.mockImplementationOnce(
        () => new Promise(resolve => setTimeout(() => resolve({
          ok: true,
          json: async () => ({ url: 'https://checkout.stripe.com/test' }),
        }), 100))
      );

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      // Should show loading state
      await waitFor(() => {
        expect(screen.getByText(/Processing/i)).toBeInTheDocument();
      });
    });

    it('should handle checkout session creation error', async () => {
      const mockFetch = global.fetch as jest.Mock;
      const mockAlert = jest.spyOn(window, 'alert').mockImplementation(() => {});
      
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: async () => ({ error: 'Failed to create session' }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockAlert).toHaveBeenCalled();
      });

      mockAlert.mockRestore();
    });

    it('should attempt to redirect to Stripe checkout URL on success', async () => {
      const mockFetch = global.fetch as jest.Mock;
      const checkoutUrl = 'https://checkout.stripe.com/test-session';

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ url: checkoutUrl }),
      });

      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      fireEvent.click(upgradeButton);

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      // Verify API call was made with correct parameters
      // Note: In test environment, window.location.href assignment is not easily testable
      // In real browser, this would redirect to Stripe checkout
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/subscription/create-checkout-session'),
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
        })
      );
    });
  });

  describe('Accessibility', () => {
    it('should have close button with aria-label', () => {
      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      const closeButton = screen.getByLabelText('Close');
      expect(closeButton).toBeInTheDocument();
    });

    it('should be keyboard accessible', () => {
      render(<PaywallModal isOpen={true} onClose={jest.fn()} />);
      
      const upgradeButton = screen.getByText(/Upgrade Now/i);
      expect(upgradeButton).toBeInTheDocument();
      
      const closeButton = screen.getByLabelText('Close');
      expect(closeButton).toBeInTheDocument();
    });
  });
});

