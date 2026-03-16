import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
  Sparkles: () => <span data-testid="icon-sparkles" />,
  Trash2: () => <span data-testid="icon-trash" />,
  LogOut: () => <span data-testid="icon-logout" />,
  CreditCard: () => <span data-testid="icon-creditcard" />,
  User: () => <span data-testid="icon-user" />,
}));

jest.mock('@/components/ChatbotCreationLoader', () => () => null);
jest.mock('@/components/PaywallModal', () => ({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) =>
  isOpen ? <div data-testid="paywall-modal"><button onClick={onClose}>Close</button></div> : null
);

const mockReplace = jest.fn();
const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush, back: jest.fn(), pathname: '/dashboard', prefetch: jest.fn() }),
}));

const mockGetAllChatbots = jest.fn();
const mockCreatePortalSession = jest.fn();
const mockLogout = jest.fn();
const mockDeleteChatbot = jest.fn();
const mockGetEmbedCode = jest.fn();
const mockUpdateChatbot = jest.fn();

jest.mock('@/lib/api', () => ({
  getAllChatbots: (...args: unknown[]) => mockGetAllChatbots(...args),
  createChatbotFromUrl: jest.fn(),
  analyzeWebsite: jest.fn(),
  getEmbedCode: (...args: unknown[]) => mockGetEmbedCode(...args),
  deleteChatbot: (...args: unknown[]) => mockDeleteChatbot(...args),
  deleteAllChatbots: jest.fn(),
  checkAuth: jest.fn(),
  logout: (...args: unknown[]) => mockLogout(...args),
  createPortalSession: (...args: unknown[]) => mockCreatePortalSession(...args),
  updateChatbot: (...args: unknown[]) => mockUpdateChatbot(...args),
  isApiError: (e: unknown): e is Error & { status?: number; upgradeRequired?: boolean } =>
    e instanceof Error && 'status' in e,
  getSafeErrorMessage: (e: unknown, fallback: string) =>
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
    mockGetAllChatbots.mockResolvedValue([minimalChatbot]);
    mockLogout.mockResolvedValue({});
  });

  describe('Authentication security', () => {
    it('should redirect to /login when getAllChatbots returns 401 (unauthenticated)', async () => {
      mockGetAllChatbots.mockRejectedValueOnce(Object.assign(new Error('Unauthorized'), { status: 401 }));

      render(<Dashboard />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith('/login');
      });
    });

    it('should redirect to /login on network/CORS error (status 0 or undefined)', async () => {
      mockGetAllChatbots.mockRejectedValueOnce(new Error('Failed to fetch'));

      render(<Dashboard />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith('/login');
      });
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
    it('should have Dashboard and Account links with correct hrefs', async () => {
      render(<Dashboard />);
      await waitFor(() => expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument());

      expect(screen.getByRole('link', { name: /Dashboard/i })).toHaveAttribute('href', '/dashboard');
      expect(screen.getByRole('link', { name: /Account/i })).toHaveAttribute('href', '/account');
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

      const subButton = screen.getByRole('button', { name: /Subscription/i });
      fireEvent.click(subButton);

      await waitFor(() => {
        expect(alertSpy).toHaveBeenCalledWith(expect.stringMatching(/Invalid billing portal/i));
      });
      alertSpy.mockRestore();
    });
  });

  describe('Smoke', () => {
    it('should show dashboard title and nav when authenticated with chatbots', async () => {
      render(<Dashboard />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Prayer-Chat Dashboard/i })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Dashboard/i })).toBeInTheDocument();
      });
    });
  });
});
