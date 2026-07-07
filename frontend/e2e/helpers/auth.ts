import { Page, expect } from '@playwright/test';
import { ApiMock } from './api-mock';

/**
 * Authentication helper for E2E tests
 * Provides methods to log in, log out, and manage authentication state
 */
export class AuthHelper {
  constructor(private page: Page) {}

  /**
   * Log in with Google OAuth (mocked)
   * @param email User email
   * @param name User name
   */
  async loginWithGoogle(email: string = 'test@gmail.com', name: string = 'Test User') {
    const apiMock = new ApiMock(this.page);

    // Keep /login visible — authenticated /api/auth/me would auto-redirect away from the page.
    await apiMock.mockUnauthenticated();
    await apiMock.mockOAuthStateRoutes();
    await apiMock.mockOAuthCallback({ id: 1, email, name, authProvider: 'GOOGLE' });

    await this.page.route('**/api/chatbots', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: '[]',
        });
        return;
      }
      await route.continue();
    });

    await this.page.goto('/login');
    await this.page.waitForLoadState('networkidle');

    const googleButton = this.page.getByRole('button', { name: /continue with google|sign in with google|google/i });
    await expect(googleButton).toBeVisible({ timeout: 10000 });

    // Simulate OAuth callback locally — avoids brittle real Google redirects in E2E.
    await this.page.goto('/auth/callback?code=e2e-mock-code&state=' + 'a'.repeat(64));
    await this.page.waitForURL(/\/(dashboard|onboarding)/, { timeout: 15000 });

    await apiMock.mockAuthEndpoints({
      loginSuccess: true,
      user: { id: 1, email, name, authProvider: 'GOOGLE' },
    });

    await this.setAuthToken('authenticated');
  }

  /**
   * Log in with email and password
   * @param email User email
   * @param password User password
   */
  async loginWithCredentials(email: string, password: string) {
    // Navigate to login page
    await this.page.goto('/login');

    // Fill in credentials
    await this.page.getByLabel(/email/i).fill(email);
    await this.page.getByLabel(/password/i).fill(password);

    // Click login button
    await this.page.getByRole('button', { name: /sign in/i }).click();

    // Wait for redirect
    await this.page.waitForURL(/\/(dashboard|home)/);
  }

  /**
   * Register a new user
   * @param email User email
   * @param username Username
   * @param password User password
   */
  async register(email: string, username: string, password: string) {
    // Navigate to register page
    await this.page.goto('/register');

    // Fill in registration form
    await this.page.getByLabel(/email/i).fill(email);
    await this.page.getByLabel(/username/i).fill(username);
    await this.page.getByLabel(/password/i).fill(password);

    // Submit form
    await this.page.getByRole('button', { name: /sign up/i }).click();

    // Wait for redirect
    await this.page.waitForURL(/\/(dashboard|home)/);
  }

  /**
   * Sets an internal E2E authentication marker.
   * Cookie auth is the runtime source of truth; this avoids token-in-storage assumptions.
   */
  async setAuthToken(marker: string = 'authenticated') {
    const url = this.page.url();
    if (url === 'about:blank' || !/^https?:\/\//.test(url)) {
      await this.page.goto('/');
    }
    await this.page.evaluate((value) => {
      sessionStorage.setItem('e2e-auth-marker', value);
    }, marker);
  }

  /**
   * Gets the internal E2E authentication marker.
   */
  async getAuthToken(): Promise<string | null> {
    return await this.page.evaluate(() => {
      return sessionStorage.getItem('e2e-auth-marker');
    });
  }

  /**
   * Log out
   */
  async logout() {
    // Click logout button (adjust selector based on your app)
    await this.page.getByRole('button', { name: /log out/i }).click();

    // Wait for redirect to home or login
    await this.page.waitForURL(/\/(home|login)/);
  }

  /**
   * Clear authentication state
   */
  async clearAuth() {
    await this.page.goto('/');
    await this.page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
  }

  /**
   * Check if user is authenticated
   */
  async isAuthenticated(): Promise<boolean> {
    const marker = await this.getAuthToken();
    return marker === 'authenticated' || marker === 'new_token' || marker === 'mock_token_from_oauth';
  }

  /**
   * Set up authenticated state for tests
   * Bypasses login flow by setting user storage marker only (cookie auth app path).
   * @param user Mock user data
   */
  async setupAuthenticatedState(user = {
    id: 1,
    email: 'test@example.com',
    name: 'Test User',
    authProvider: 'LOCAL',
  }) {
    await this.page.goto('/');

    await this.setAuthToken('authenticated');

    // Set user data
    await this.page.evaluate((userData: { id: number; email: string; name: string; authProvider: string }) => {
      localStorage.setItem('user', JSON.stringify(userData));
    }, user);
  }
}
