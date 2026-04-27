import { Page } from '@playwright/test';
import { E2E_MOCK_AUTH_TOKEN } from './test-auth-constants';

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

    // Mock /api/auth/me endpoint (used by checkAuth).
    // App auth is cookie-based now (credentials: include), so tests should not
    // depend on legacy Authorization headers.
    await this.page.route('**/api/auth/me', async (route) => {
      if (loginSuccess) {
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
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            token: E2E_MOCK_AUTH_TOKEN,
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
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            token: E2E_MOCK_AUTH_TOKEN,
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
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            token: E2E_MOCK_AUTH_TOKEN,
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

    // Mock analysis-status so chatbot preview/detail page leaves loading state
    await this.page.route('**/api/chatbots/*/analysis-status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ready: true, pagesIndexed: 1 }),
      });
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

    // Mock subscription status (full shape for account page)
    await this.page.route('**/api/subscription/status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          hasSubscription: plan !== 'FREE',
          status,
          plan,
          isActive: status === 'ACTIVE',
          canUseChatbot: plan !== 'FREE',
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

    // Mock create portal session (account page "Manage subscription")
    await this.page.route('**/api/subscription/create-portal-session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          portalUrl: 'https://billing.stripe.com/session/mock',
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
