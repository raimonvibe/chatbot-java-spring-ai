import { checkAuth, createChatbot, getAllChatbots, getChatbot } from '../api';

const mockFetch = global.fetch as jest.MockedFunction<typeof fetch>;

describe('API Authentication Security Tests (Cookie-First)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockFetch.mockClear();
  });

  it('uses credentials include for authenticated requests', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] } as Response);
    await getAllChatbots();
    const options = mockFetch.mock.calls[0][1] as RequestInit;
    expect(options.credentials).toBe('include');
  });

  it('does not send Authorization header from localStorage token', async () => {
    window.localStorage.setItem('authToken', 'header.payload.signature');
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] } as Response);
    await getAllChatbots();
    const headers = (mockFetch.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });

  it('checkAuth returns authenticated user on 200', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ id: 1, email: 'test@example.com' }),
    } as Response);
    await expect(checkAuth()).resolves.toEqual({
      authenticated: true,
      user: { id: 1, email: 'test@example.com' },
    });
  });

  it('checkAuth returns unauthenticated on 401', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 } as Response);
    await expect(checkAuth()).resolves.toEqual({ authenticated: false });
  });

  it('keeps JSON content-type on create/update style requests', async () => {
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

    await createChatbot({ name: 'New Bot', description: 'Test', websiteUrl: 'https://example.com' });
    const headers = (mockFetch.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers['Content-Type']).toBe('application/json');
  });

  it('works for chatbot reads with cookie auth mode', async () => {
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
    await expect(getChatbot(1)).resolves.toBeDefined();
  });
});

