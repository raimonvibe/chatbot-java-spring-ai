import React from 'react';
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import Dashboard from '../page';

jest.mock('framer-motion', () => ({
  motion: {
    div: ({ children, className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
      <div className={className} {...props}>{children}</div>
    ),
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

jest.mock('lucide-react', () => ({
  Book: () => <span data-testid="icon-book" />,
  Plus: () => <span data-testid="icon-plus" />,
  X: () => <span data-testid="icon-x" />,
  Eye: () => <span data-testid="icon-eye" />,
  Code: () => <span data-testid="icon-code" />,
  Copy: () => <span data-testid="icon-copy" />,
  CheckCircle: () => <span data-testid="icon-check" />,
  Crown: () => <span data-testid="icon-crown" />,
  LogOut: () => <span data-testid="icon-logout" />,
  CreditCard: () => <span data-testid="icon-creditcard" />,
  User: () => <span data-testid="icon-user" />,
  Loader2: () => <span data-testid="icon-loader2" />,
  Trash2: () => <span data-testid="icon-trash2" />,
  Palette: () => <span data-testid="icon-palette" />,
}));

jest.mock('@/components/ChatbotCreationLoader', () => () => null);
jest.mock('@/components/PaywallModal', () => ({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) =>
  isOpen ? <div data-testid="paywall-modal"><button onClick={onClose}>Close</button></div> : null
);

const mockReplace = jest.fn();
const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush, back: jest.fn(), pathname: '/dashboard', prefetch: jest.fn() }),
  usePathname: () => '/dashboard',
}));

const mockGetAllChatbots = jest.fn();
const mockCheckAuth = jest.fn();
const mockCreatePortalSession = jest.fn();
const mockLogout = jest.fn();
const mockGetEmbedCode = jest.fn();
const mockUpdateChatbot = jest.fn();
const mockGetSubscriptionStatusFromApi = jest.fn();

jest.mock('@/lib/api', () => ({
  getAllChatbots: (...args: unknown[]) => mockGetAllChatbots(...args),
  createChatbotFromUrl: jest.fn(),
  checkAuth: (...args: unknown[]) => mockCheckAuth(...args),
  getEmbedCode: (...args: unknown[]) => mockGetEmbedCode(...args),
  logout: (...args: unknown[]) => mockLogout(...args),
  createPortalSession: (...args: unknown[]) => mockCreatePortalSession(...args),
  updateChatbot: (...args: unknown[]) => mockUpdateChatbot(...args),
  getSubscriptionStatusFromApi: (...args: unknown[]) => mockGetSubscriptionStatusFromApi(...args),
  isApiError: (e: unknown): e is Error & { status?: number; upgradeRequired?: boolean } =>
    e instanceof Error && 'status' in e,
  getSafeErrorMessage: (e: unknown, fallback: string) =>
    e instanceof Error && typeof e.message === 'string' && e.message ? e.message.slice(0, 500) : fallback,
  getUserFacingFetchError: (e: unknown, fallback: string) =>
    e instanceof Error && typeof e.message === 'string' && e.message ? e.message.slice(0, 500) : fallback,
}));

const minimalChatbot = {
  id: 1,
  name: 'Test Bot',
  description: 'A test chatbot',
  primaryLanguage: 'en',
  supportedLanguages: ['en'],
  brandingConfig: '{}',
  websiteUrl: 'https://example.com',
};

describe('Dashboard Page', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // mockImplementation survives clearAllMocks better than mockResolvedValue for shared fn mocks
    mockCheckAuth.mockImplementation(() =>
      Promise.resolve({ authenticated: true, user: { id: 1, email: 'a@b.c' } })
    );
    mockGetAllChatbots.mockResolvedValue([minimalChatbot]);
    mockLogout.mockResolvedValue({});
    mockGetSubscriptionStatusFromApi.mockResolvedValue({
      hasSubscription: true,
      status: 'active',
      plan: 'BASIC',
      isActive: true,
      canUseChatbot: true,
    });
  });

  describe('Authentication security', () => {
    it('should redirect to /login when getAllChatbots returns 401 (unauthenticated)', async () => {
      mockGetAllChatbots.mockRejectedValueOnce(Object.assign(new Error('Unauthorized'), { status: 401 }));

      render(<Dashboard />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith('/login');
      });
    });

    it('should stay signed in on network error when checkAuth still succeeds', async () => {
      mockGetAllChatbots.mockRejectedValueOnce(new Error('Failed to fetch'));

      render(<Dashboard />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument();
      });

      expect(mockReplace).not.toHaveBeenCalledWith('/login');
      expect(screen.getByRole('alert')).toHaveTextContent(/Failed to fetch|connection was interrupted|Could not load your chatbots/i);
    });
  });

  describe('XSS prevention', () => {
    it('should render chatbot name and description as text only (no script execution)', async () => {
      const xssName = '<script>alert("xss")</script>SafeName';
      const xssDesc = '<img src=x onerror=alert("xss")>SafeDesc';
      mockGetAllChatbots.mockResolvedValueOnce([{
        ...minimalChatbot,
        id: 1,
        name: xssName,
        description: xssDesc,
      }]);

      const { container } = render(<Dashboard />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument();
      });

      expect(container.textContent).toContain('SafeName');
      expect(container.textContent).toContain('SafeDesc');
      const scripts = container.querySelectorAll('script');
      expect(scripts.length).toBe(0);
      const dangerousImg = container.querySelector('img[onerror]');
      expect(dangerousImg).toBeNull();
    });

  });

  describe('Navigation and links', () => {
    it('should have Account link and dashboard heading (nav is in header)', async () => {
      render(<Dashboard />);
      await waitFor(() => expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument());

      // Dashboard/Account/Subscription/Logout are in the top navbar (Header); page has Account in mobile overview card
      const accountLink = screen.getByRole('link', { name: /Open account|Account/i });
      expect(accountLink).toHaveAttribute('href', '/account');
    });

    it('should build chatbot preview link with numeric id only (no path traversal)', async () => {
      render(<Dashboard />);
      await waitFor(() => expect(screen.getByRole('link', { name: /Preview Chatbot/i })).toBeInTheDocument());

      const previewLink = screen.getByRole('link', { name: /Preview Chatbot/i });
      expect(previewLink).toHaveAttribute('href', '/chatbot/1');
      expect(previewLink.getAttribute('href')).not.toMatch(/\.\.\/|javascript:/);
    });
  });

  describe('Stripe portal redirect security', () => {
    it('should not redirect to non-Stripe URL and should alert user', async () => {
      mockGetAllChatbots.mockResolvedValueOnce([minimalChatbot]);
      mockCreatePortalSession.mockResolvedValueOnce('https://evil.com/phishing');
      const alertSpy = jest.spyOn(window, 'alert').mockImplementation(() => {});

      render(<Dashboard />);
      await waitFor(() => expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument());

      // Subscription button is in mobile overview card (nav links are in Header)
      const subButton = screen.getByRole('button', { name: /Subscription/i });
      fireEvent.click(subButton);

      await waitFor(() => {
        expect(alertSpy).toHaveBeenCalledWith(expect.stringMatching(/Invalid billing portal/i));
      });
      alertSpy.mockRestore();
    });
  });

  describe('Theme / color apply', () => {
    it('should call updateChatbot with non-blank name and websiteUrl when applying theme (backend @Valid)', async () => {
      const botWithoutWebsiteUrl = {
        ...minimalChatbot,
        id: 1,
        name: 'My Bot',
        websiteUrl: undefined as unknown as string,
      };
      mockGetAllChatbots.mockResolvedValueOnce([botWithoutWebsiteUrl]);
      mockUpdateChatbot.mockResolvedValueOnce({ ...minimalChatbot, brandingConfig: '{"primaryColor":"#7D9B69","secondaryColor":"#B5C9A8","borderRadius":"8px"}' });

      render(<Dashboard />);
      await waitFor(() => expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument());

      const themeButton = screen.getByRole('button', { name: /Apply Sage theme/i });
      fireEvent.click(themeButton);

      await waitFor(() => {
        expect(mockUpdateChatbot).toHaveBeenCalledWith(1, expect.any(Object));
      });
      const payload = mockUpdateChatbot.mock.calls[0][1];
      expect(payload.name).toBeTruthy();
      expect(String(payload.name).trim()).not.toBe('');
      expect(payload.websiteUrl).toBeTruthy();
      expect(String(payload.websiteUrl).trim()).not.toBe('');
      expect(JSON.parse(payload.brandingConfig)).toMatchObject({
        primaryColor: '#7D9B69',
        secondaryColor: '#B5C9A8',
        borderRadius: '8px',
      });
    });
  });

  describe('Smoke', () => {
    it('should show dashboard title and account link when authenticated with chatbots', async () => {
      render(<Dashboard />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Open account|Account/i })).toHaveAttribute('href', '/account');
      });
    });
  });
});
