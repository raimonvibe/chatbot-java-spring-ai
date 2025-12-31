import { Page, Route } from '@playwright/test';

/**
 * API Mock helper for E2E tests
 * Provides utilities for mocking API responses
 */
export class ApiMock {
  constructor(private page: Page) {}

  /**
   * Mock authentication endpoints
   */
  async mockAuthEndpoints(options: {
    loginSuccess?: boolean;
    registerSuccess?: boolean;
    user?: any;
  } = {}) {
    const {
      loginSuccess = true,
      registerSuccess = true,
      user = {
        id: 1,
        email: 'test@example.com',
        name: 'Test User',
        authProvider: 'LOCAL',
      },
    } = options;

    // Mock /api/auth/me endpoint (used by checkAuth)
    await this.page.route('**/api/auth/me', async (route) => {
      const authHeader = route.request().headers()['authorization'];
      // Login page calls /api/auth/me without Authorization header to check if already authenticated
      // If no header, return 401 (not authenticated) to stay on login page
      // If header present with token, return user (authenticated)
      // Check for valid JWT format (3 parts) or mock signature
      if (authHeader && (authHeader.includes('mock_signature') || authHeader.includes('eyJ'))) {
        // Return user data directly (checkAuth wraps this)
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(user),
        });
      } else {
        // No auth header or invalid token - not authenticated
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Unauthorized' }),
        });
      }
    });

    // Mock hybrid OAuth callback endpoint
    await this.page.route('**/api/auth/oauth2/callback', async (route) => {
      if (loginSuccess) {
        // Use valid JWT format for token
        const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature';
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            token: mockToken,
            user,
          }),
        });
      } else {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'OAuth authentication failed' }),
        });
      }
    });

    // Mock login endpoint
    await this.page.route('**/api/auth/login', async (route) => {
      if (loginSuccess) {
        // Use valid JWT format for token
        const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature';
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            token: mockToken,
            user,
          }),
        });
      } else {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Invalid credentials' }),
        });
      }
    });

    // Mock register endpoint
    await this.page.route('**/api/auth/register', async (route) => {
      if (registerSuccess) {
        // Use valid JWT format for token
        const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature';
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            token: mockToken,
            user,
          }),
        });
      } else {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Email already exists' }),
        });
      }
    });
  }

  /**
   * Mock chatbot endpoints
   */
  async mockChatbotEndpoints(chatbots: any[] = []) {
    // Mock get all chatbots
    await this.page.route('**/api/chatbots', async (route) => {
      const authHeader = route.request().headers()['authorization'];
      // Check if request has valid auth token (format: "Bearer <jwt_token>")
      // Accept tokens with mock_signature or valid JWT format (eyJ...)
      if (!authHeader || (!authHeader.includes('mock_signature') && !authHeader.includes('eyJ'))) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Unauthorized' }),
        });
        return;
      }

      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(chatbots),
        });
      } else if (route.request().method() === 'POST') {
        const newChatbot = {
          id: chatbots.length + 1,
          name: 'New Chatbot',
          description: 'Test chatbot',
          websiteUrl: 'https://example.com',
          active: true,
          createdAt: new Date().toISOString(),
        };
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newChatbot),
        });
      }
    });

    // Mock get single chatbot
    await this.page.route('**/api/chatbots/*', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      const id = parseInt(url.split('/').pop() || '0');

      if (method === 'GET') {
        const chatbot = chatbots.find((c) => c.id === id) || chatbots[0];
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(chatbot),
        });
      } else if (method === 'DELETE') {
        await route.fulfill({
          status: 204,
        });
      } else if (method === 'PUT' || method === 'PATCH') {
        const chatbot = chatbots.find((c) => c.id === id) || chatbots[0];
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...chatbot, ...route.request().postDataJSON() }),
        });
      }
    });
  }

  /**
   * Mock chat endpoints
   */
  async mockChatEndpoints() {
    await this.page.route('**/api/chat/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'This is a mock AI response to your question.',
          timestamp: new Date().toISOString(),
        }),
      });
    });
  }

  /**
   * Mock subscription endpoints
   */
  async mockSubscriptionEndpoints(options: {
    status?: string;
    plan?: string;
  } = {}) {
    const { status = 'ACTIVE', plan = 'FREE' } = options;

    // Mock subscription status
    await this.page.route('**/api/subscription/status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status,
          plan,
          currentPeriodEnd: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
        }),
      });
    });

    // Mock create checkout session
    await this.page.route('**/api/subscription/create-checkout-session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          url: 'https://checkout.stripe.com/mock',
        }),
      });
    });
  }

  /**
   * Mock all common endpoints
   */
  async mockAllEndpoints(options: {
    chatbots?: any[];
    user?: any;
    subscriptionStatus?: string;
    subscriptionPlan?: string;
  } = {}) {
    const { chatbots = [], user, subscriptionStatus, subscriptionPlan } = options;

    await this.mockAuthEndpoints({ user });
    await this.mockChatbotEndpoints(chatbots);
    await this.mockChatEndpoints();
    await this.mockSubscriptionEndpoints({
      status: subscriptionStatus,
      plan: subscriptionPlan,
    });
  }

  /**
   * Mock an API error response
   */
  async mockApiError(path: string, statusCode: number = 500, message: string = 'Server error') {
    await this.page.route(`**${path}`, async (route) => {
      await route.fulfill({
        status: statusCode,
        contentType: 'application/json',
        body: JSON.stringify({ error: message }),
      });
    });
  }

  /**
   * Mock a network error (request fails)
   */
  async mockNetworkError(path: string) {
    await this.page.route(`**${path}`, async (route) => {
      await route.abort('failed');
    });
  }

  /**
   * Mock a slow API response (for testing loading states)
   */
  async mockSlowResponse(path: string, delay: number = 2000, response: any = {}) {
    await this.page.route(`**${path}`, async (route) => {
      await new Promise((resolve) => setTimeout(resolve, delay));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(response),
      });
    });
  }

  /**
   * Clear all route mocks
   */
  async clearMocks() {
    await this.page.unroute('**/*');
  }
}
