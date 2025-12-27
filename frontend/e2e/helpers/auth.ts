import { Page, expect } from '@playwright/test';

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
    // Navigate to login page
    await this.page.goto('/login');
    await this.page.waitForLoadState('networkidle');

    // Mock the auth/me endpoint to return authenticated user (this is checked by the login page useEffect)
    await this.page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 1,
          email,
          name,
          authProvider: 'GOOGLE',
        }),
      });
    });

    // Set auth state BEFORE setting up route (avoids race condition)
    // Use valid JWT format (3 parts) to pass validation
    const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature';
    await this.page.evaluate((userData: { id: number; email: string; name: string; authProvider: string }, token: string) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('user', JSON.stringify(userData));
    }, { id: 1, email, name, authProvider: 'GOOGLE' }, mockToken);

    // Intercept navigation to OAuth endpoint
    // Use a promise to handle navigation properly
    let navigationPromise: ReturnType<typeof this.page.goto> | null = null;
    
    await this.page.route('**/oauth2/authorization/google', async (route) => {
      // Fulfill the route immediately
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<html><body>OAuth intercepted</body></html>',
      });
      
      // Navigate to dashboard - don't await here to avoid blocking
      navigationPromise = this.page.goto('/dashboard').catch(() => null);
    });

    // Click the Google login button (button text is "Continue with Google")
    const googleButton = this.page.getByRole('button', { name: /continue with google|sign in with google|google/i });
    await expect(googleButton).toBeVisible({ timeout: 10000 });
    
    // Click button - this will trigger the OAuth redirect which we intercept
    await googleButton.click();
    
    // Wait for navigation promise if it was created
    if (navigationPromise) {
      await navigationPromise;
    }
    
    // Wait for navigation to dashboard or onboarding
    await this.page.waitForURL(/\/(dashboard|onboarding|home)/, { timeout: 15000 });
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
   * Set authentication token in local storage
   * Useful for bypassing the login flow in tests
   * Uses a valid JWT format (3 parts separated by dots) to pass validation
   */
  async setAuthToken(token: string = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature') {
    await this.page.goto('/');
    await this.page.evaluate((authToken) => {
      localStorage.setItem('authToken', authToken);
    }, token);
  }

  /**
   * Get authentication token from local storage
   */
  async getAuthToken(): Promise<string | null> {
    return await this.page.evaluate(() => {
      return localStorage.getItem('authToken');
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
    const token = await this.getAuthToken();
    return token !== null && token !== '';
  }

  /**
   * Set up authenticated state for tests
   * Bypasses login flow by setting token directly
   * @param user Mock user data
   */
  async setupAuthenticatedState(user = {
    id: 1,
    email: 'test@example.com',
    name: 'Test User',
    authProvider: 'LOCAL',
  }) {
    await this.page.goto('/');

    // Set auth token (use valid JWT format to pass validation)
    // Format: header.payload.signature
    await this.setAuthToken('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature');

    // Set user data
    await this.page.evaluate((userData: { id: number; email: string; name: string; authProvider: string }) => {
      localStorage.setItem('user', JSON.stringify(userData));
    }, user);
  }
}
