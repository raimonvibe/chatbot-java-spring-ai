import { checkAuth, getAllChatbots, getChatbot, createChatbot } from '../api';

// Mock fetch globally
const mockFetch = global.fetch as jest.MockedFunction<typeof fetch>;

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};

  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString();
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
  };
})();

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
});

describe('API Authentication Security Tests', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorageMock.clear();
    // Reset fetch mock
    mockFetch.mockClear();
  });

  describe('JWT Token Security', () => {
    it('should include valid JWT token in Authorization header', async () => {
      // Valid JWT token (3 parts separated by dots)
      const validToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature';
      localStorageMock.setItem('authToken', validToken);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      expect(headers).toHaveProperty('Authorization');
      expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${validToken}`);
    });

    it('should not include token if localStorage is empty', async () => {
      // No token in localStorage
      localStorageMock.clear();

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should not have Authorization header if no token
      expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
    });

    it('should reject malformed JWT tokens (wrong number of parts)', async () => {
      // Invalid token - only 2 parts (should be 3)
      const invalidToken = 'header.payload';
      localStorageMock.setItem('authToken', invalidToken);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should not include invalid token
      expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
      
      // Token should be removed from localStorage
      expect(localStorageMock.getItem('authToken')).toBeNull();
    });

    it('should reject tokens with suspicious characters (header injection attempt)', async () => {
      // Token with newline character (header injection attempt)
      const maliciousToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\neyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature';
      localStorageMock.setItem('authToken', maliciousToken);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should not include malicious token
      expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
      
      // Token should be removed
      expect(localStorageMock.getItem('authToken')).toBeNull();
    });

    it('should reject tokens with invalid characters', async () => {
      // Token with invalid characters (not base64url)
      const invalidToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature<script>';
      localStorageMock.setItem('authToken', invalidToken);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should not include invalid token
      expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
      
      // Token should be removed
      expect(localStorageMock.getItem('authToken')).toBeNull();
    });

    it('should sanitize tokens by removing whitespace', async () => {
      // Token with whitespace
      const tokenWithWhitespace = '  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature  ';
      localStorageMock.setItem('authToken', tokenWithWhitespace);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should include trimmed token
      const authHeader = (headers as Record<string, string>)['Authorization'];
      expect(authHeader).toBeDefined();
      expect(authHeader).toContain('Bearer');
      // Should not contain leading/trailing whitespace
      expect(authHeader).not.toMatch(/^\s+|\s+$/);
    });

    it('should handle empty string tokens', async () => {
      localStorageMock.setItem('authToken', '');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should not include empty token
      expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
    });

    it('should handle localStorage errors gracefully', async () => {
      // Mock localStorage.getItem to throw an error
      const originalGetItem = localStorageMock.getItem;
      localStorageMock.getItem = jest.fn(() => {
        throw new Error('localStorage access denied');
      });

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      // Should not throw error
      await expect(getAllChatbots()).resolves.toBeDefined();

      // Restore original
      localStorageMock.getItem = originalGetItem;
    });
  });

  describe('API Functions with Authentication', () => {
    const validToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature';

    beforeEach(() => {
      localStorageMock.setItem('authToken', validToken);
    });

    it('should include token in checkAuth request', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ id: 1, email: 'test@example.com' }),
      } as Response);

      await checkAuth();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${validToken}`);
    });

    it('should include token in getAllChatbots request', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${validToken}`);
    });

    it('should include token in getChatbot request', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: 1,
          name: 'Test Bot',
          description: 'Test',
          primaryLanguage: 'en',
          supportedLanguages: ['en'],
          brandingConfig: '{}',
        }),
      } as Response);

      await getChatbot(1);

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${validToken}`);
    });

    it('should include token in createChatbot request', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: 1,
          name: 'New Bot',
          description: 'Test',
          primaryLanguage: 'en',
          supportedLanguages: ['en'],
          brandingConfig: '{}',
        }),
      } as Response);

      await createChatbot({
        name: 'New Bot',
        description: 'Test',
        websiteUrl: 'https://example.com',
      });

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${validToken}`);
    });
  });

  describe('Token Format Validation', () => {
    it('should accept valid JWT tokens with base64url characters', async () => {
      // Valid tokens with various base64url characters
      const validTokens = [
        'abc.def.ghi',
        'ABC123.def456.GHI789',
        'test-token_123.another_part.final_signature',
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0In0.signature',
      ];

      for (const token of validTokens) {
        localStorageMock.clear();
        localStorageMock.setItem('authToken', token);

        mockFetch.mockResolvedValueOnce({
          ok: true,
          json: async () => [],
        } as Response);

        await getAllChatbots();

        const fetchCall = mockFetch.mock.calls[mockFetch.mock.calls.length - 1];
        const options = fetchCall[1] as RequestInit;
        const headers = options.headers as HeadersInit;

        expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${token}`);
      }
    });

    it('should reject tokens with invalid characters', async () => {
      const invalidTokens = [
        'token<script>',
        'token with spaces',
        'token\nwith\nnewlines',
        'token\twith\ttabs',
        'token"with"quotes',
        'token\'with\'quotes',
        'token;with;semicolons',
      ];

      for (const token of invalidTokens) {
        localStorageMock.clear();
        localStorageMock.setItem('authToken', token);

        mockFetch.mockResolvedValueOnce({
          ok: true,
          json: async () => [],
        } as Response);

        await getAllChatbots();

        const fetchCall = mockFetch.mock.calls[mockFetch.mock.calls.length - 1];
        const options = fetchCall[1] as RequestInit;
        const headers = options.headers as HeadersInit;

        // Should not include invalid token
        expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
        
        // Token should be removed
        expect(localStorageMock.getItem('authToken')).toBeNull();
      }
    });
  });

  describe('Edge Cases', () => {
    it('should handle null token gracefully', async () => {
      localStorageMock.setItem('authToken', 'null');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // "null" as string is not a valid JWT, so should be rejected
      expect((headers as Record<string, string>)['Authorization']).toBeUndefined();
    });

    it('should handle very long tokens', async () => {
      // Create a very long but valid JWT token
      const longPart = 'a'.repeat(1000);
      const longToken = `${longPart}.${longPart}.${longPart}`;
      localStorageMock.setItem('authToken', longToken);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await getAllChatbots();

      const fetchCall = mockFetch.mock.calls[0];
      const options = fetchCall[1] as RequestInit;
      const headers = options.headers as HeadersInit;

      // Should include valid long token
      expect((headers as Record<string, string>)['Authorization']).toBe(`Bearer ${longToken}`);
    });

    it('should work in server-side rendering context (no window)', async () => {
      // Temporarily remove window
      const originalWindow = global.window;
      // @ts-ignore
      delete global.window;

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      // Should not throw error
      await expect(getAllChatbots()).resolves.toBeDefined();

      // Restore window
      global.window = originalWindow;
    });
  });
});

