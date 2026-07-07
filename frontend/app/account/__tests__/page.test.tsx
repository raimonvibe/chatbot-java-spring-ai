import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import AccountPage from '../page';

// Mock lucide-react so icons render as spans (avoid undefined in test env)
jest.mock('lucide-react', () => ({
  User: () => <span data-testid="icon-user" />,
  Mail: () => <span data-testid="icon-mail" />,
  CreditCard: () => <span data-testid="icon-creditcard" />,
  Shield: () => <span data-testid="icon-shield" />,
  LogOut: () => <span data-testid="icon-logout" />,
  ExternalLink: () => <span data-testid="icon-external" />,
  FileText: () => <span data-testid="icon-file" />,
  MessageCircle: () => <span data-testid="icon-message" />,
  Loader2: () => <span data-testid="icon-loader" />,
  Code: () => <span data-testid="icon-code" />,
  Copy: () => <span data-testid="icon-copy" />,
}));

const mockReplace = jest.fn();
const mockCheckAuth = jest.fn();
const mockGetSubscriptionStatusFromApi = jest.fn();
const mockLogout = jest.fn();
const mockCreatePortalSession = jest.fn();
const mockGetAllChatbots = jest.fn();
const mockGetEmbedCode = jest.fn();

// Mock location to avoid "Not implemented: navigation" when Manage subscription sets window.location.href.
const locationMock = { href: '', assign: jest.fn() };
beforeAll(() => {
  try {
    delete (window as unknown as { location?: unknown }).location;
    Object.defineProperty(window, 'location', { value: locationMock, writable: true, configurable: true });
  } catch {
    // location not configurable; tests that set location.href may log a navigation error
  }
});

jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: jest.fn(), back: jest.fn(), pathname: '/account', prefetch: jest.fn() }),
  usePathname: () => '/account',
  useSearchParams: () => ({
    get: jest.fn((_key: string) => null),
  }),
}));

let mockRequireAuthAuthenticated = true;

jest.mock('@/hooks/useRequireAuth', () => ({
  useRequireAuth: () => ({
    authenticated: mockRequireAuthAuthenticated,
    loading: false,
    networkError: false,
    user: null,
    refresh: jest.fn(),
  }),
}));

jest.mock('@/lib/api', () => ({
  checkAuth: (...args: unknown[]) => mockCheckAuth(...args),
  getSubscriptionStatusFromApi: (...args: unknown[]) => mockGetSubscriptionStatusFromApi(...args),
  logout: (...args: unknown[]) => mockLogout(...args),
  createPortalSession: (...args: unknown[]) => mockCreatePortalSession(...args),
  getAllChatbots: (...args: unknown[]) => mockGetAllChatbots(...args),
  getEmbedCode: (...args: unknown[]) => mockGetEmbedCode(...args),
}));

describe('Account Page', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRequireAuthAuthenticated = true;
    mockCheckAuth.mockResolvedValue({
      authenticated: true,
      user: { id: 1, username: 'user@test.com', email: 'user@test.com', authProvider: 'GOOGLE' },
    });
    mockGetSubscriptionStatusFromApi.mockResolvedValue({
      hasSubscription: false,
      plan: 'FREE',
      isActive: false,
      canUseChatbot: false,
    });
    mockLogout.mockResolvedValue({ message: 'OK' });
    mockGetAllChatbots.mockResolvedValue([]);
    mockGetEmbedCode.mockResolvedValue('<script>embed</script>');
  });

  describe('Authentication', () => {
    it('should show redirect state when not authenticated', async () => {
      mockRequireAuthAuthenticated = false;

      render(<AccountPage />);

      await waitFor(() => {
        expect(screen.getByText(/Redirecting to login/i)).toBeInTheDocument();
      });
    });

    it('should show account content when authenticated', async () => {
      render(<AccountPage />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Account/i })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument();
      });
    });
  });

  describe('Profile section', () => {
    it('should display user email from auth', async () => {
      render(<AccountPage />);

      await waitFor(() => {
        expect(screen.getByText('user@test.com')).toBeInTheDocument();
      });
    });

    it('should render malicious user content as text not HTML (XSS)', async () => {
      mockCheckAuth.mockResolvedValue({
        authenticated: true,
        user: {
          id: 1,
          username: '<script>alert("xss")</script>evil@test.com',
          email: '<script>alert("xss")</script>evil@test.com',
          authProvider: 'GOOGLE',
        },
      });

      const { container } = render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument());

      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);
      expect(container.textContent).toContain('evil@test.com');
    });
  });

  describe('Subscription section', () => {
    it('should show plan and Manage subscription button', async () => {
      mockGetSubscriptionStatusFromApi.mockResolvedValueOnce({
        hasSubscription: true,
        plan: 'BASIC',
        isActive: true,
        canUseChatbot: true,
        currentPeriodEnd: '2025-12-01T00:00:00Z',
      });

      render(<AccountPage />);

      await waitFor(() => {
        expect(screen.getByText('BASIC')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Manage subscription/i })).toBeInTheDocument();
      });
    });
  });

  describe('Stripe portal redirect security', () => {
    it('should not redirect to non-Stripe URL and should alert user', async () => {
      mockCreatePortalSession.mockResolvedValueOnce('https://evil.com/phishing');
      const alertSpy = jest.spyOn(window, 'alert').mockImplementation(() => {});

      render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('button', { name: /Manage subscription/i })).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /Manage subscription/i }));

      await waitFor(() => {
        expect(alertSpy).toHaveBeenCalledWith(expect.stringMatching(/Invalid billing portal/i));
      });
      expect(mockCreatePortalSession).toHaveBeenCalled();
      alertSpy.mockRestore();
    });

    it('should accept Stripe billing URL and call createPortalSession', async () => {
      const stripeUrl = 'https://billing.stripe.com/session/test123';
      mockCreatePortalSession.mockResolvedValueOnce(stripeUrl);
      const alertSpy = jest.spyOn(window, 'alert').mockImplementation(() => {});

      render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('button', { name: /Manage subscription/i })).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /Manage subscription/i }));

      await waitFor(() => expect(mockCreatePortalSession).toHaveBeenCalled());
      expect(alertSpy).not.toHaveBeenCalledWith(expect.stringMatching(/Invalid billing portal/i));
      alertSpy.mockRestore();
    });
  });

  describe('Error handling security', () => {
    it('should not expose sensitive data in alert when API error contains stack or secret', async () => {
      mockCreatePortalSession.mockRejectedValue(new Error('Error: apiKey is invalid'));

      render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('button', { name: /Manage subscription/i })).toBeInTheDocument());

      const alertSpy = jest.spyOn(window, 'alert').mockImplementation(() => {});
      fireEvent.click(screen.getByRole('button', { name: /Manage subscription/i }));

      await waitFor(() => expect(alertSpy.mock.calls.length).toBeGreaterThan(0), { timeout: 2000 });
      const msg = alertSpy.mock.calls[alertSpy.mock.calls.length - 1][0];
      expect(String(msg)).not.toContain('apiKey');
      expect(String(msg)).toMatch(/Something went wrong|contact support/i);
      alertSpy.mockRestore();
      mockCreatePortalSession.mockReset();
    });
  });

  describe('Sign out', () => {
    it('should call logout and redirect on Sign out', async () => {
      render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('button', { name: /Sign out/i })).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /Sign out/i }));

      await waitFor(() => {
        expect(mockLogout).toHaveBeenCalled();
        expect(mockReplace).toHaveBeenCalledWith('/');
      });
    });
  });

  describe('Legal links', () => {
    it('should have links to Privacy, Legal, Contact', async () => {
      render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('heading', { name: /^Account$/i })).toBeInTheDocument());

      expect(screen.getByRole('link', { name: /Privacy Notice/i })).toHaveAttribute('href', '/privacy');
      expect(screen.getByRole('link', { name: /Legal Notice/i })).toHaveAttribute('href', '/legal');
      expect(screen.getByRole('link', { name: /Contact/i })).toHaveAttribute('href', '/contact');
    });

    it('should have Back to Dashboard link', async () => {
      render(<AccountPage />);
      await waitFor(() => expect(screen.getByRole('heading', { name: /^Account$/i })).toBeInTheDocument());
      expect(screen.getByRole('link', { name: /Back to Dashboard/i })).toHaveAttribute('href', '/dashboard');
    });
  });
});
