import {
  getSubscriptionStatusFromApi,
  createPortalSession,
} from '../api';

const mockFetch = global.fetch as jest.MockedFunction<typeof fetch>;

const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { store = {}; },
  };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

describe('getSubscriptionStatusFromApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorageMock.clear();
  });

  it('should call GET /api/subscription/status with cookie auth request settings', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        hasSubscription: false,
        status: 'FREE',
        plan: 'FREE',
        isActive: false,
        canUseChatbot: false,
      }),
    } as Response);

    await getSubscriptionStatusFromApi();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/subscription/status'),
      expect.objectContaining({
        method: 'GET',
        credentials: 'include',
        headers: expect.any(Object),
      })
    );
    const headers = (mockFetch.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers?.Authorization).toBeUndefined();
  });

  it('should return parsed subscription status when ok', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        hasSubscription: true,
        status: 'ACTIVE',
        plan: 'BASIC',
        isActive: true,
        canUseChatbot: true,
        currentPeriodEnd: '2025-03-01T00:00:00Z',
      }),
    } as Response);

    const result = await getSubscriptionStatusFromApi();

    expect(result).toEqual({
      hasSubscription: true,
      status: 'ACTIVE',
      plan: 'BASIC',
      isActive: true,
      canUseChatbot: true,
      currentPeriodEnd: '2025-03-01T00:00:00Z',
    });
  });

  it('should return null when response is not ok', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false } as Response);
    const result = await getSubscriptionStatusFromApi();
    expect(result).toBeNull();
  });

  it('should return null when fetch throws', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'));
    const result = await getSubscriptionStatusFromApi();
    expect(result).toBeNull();
  });
});

describe('createPortalSession (account page usage)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorageMock.clear();
  });

  it('should use POST and credentials: include', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ portalUrl: 'https://billing.stripe.com/session/test' }),
    } as Response);

    await createPortalSession('https://example.com/account');

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/subscription/create-portal-session'),
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      })
    );
  });

  it('should send returnUrl in body when provided', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ portalUrl: 'https://billing.stripe.com/session/test' }),
    } as Response);

    await createPortalSession('https://example.com/account');

    const body = JSON.parse((mockFetch.mock.calls[0][1] as RequestInit).body as string);
    expect(body).toEqual({ returnUrl: 'https://example.com/account' });
  });

  it('should throw on invalid portal URL response', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    } as Response);

    await expect(createPortalSession()).rejects.toThrow('Invalid portal URL');
  });
});
