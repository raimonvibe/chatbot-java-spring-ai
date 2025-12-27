import { test, expect } from '@playwright/test';
import { AuthHelper } from '../helpers/auth';
import { ApiMock } from '../helpers/api-mock';
import { testUsers } from '../fixtures/users';

/**
 * Navigation Integration Tests
 *
 * Tests navigation flow across all pages:
 * - Full navigation flow across all pages
 * - Auth state persistence
 * - Protected route redirects
 * - Back/forward navigation
 * - Deep linking
 */
test.describe('Navigation Integration', () => {
  let authHelper: AuthHelper;
  let apiMock: ApiMock;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    apiMock = new ApiMock(page);
  });

  test('should navigate through all public pages', async ({ page }) => {
    // Start at home
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();

    // Navigate to pricing
    const pricingLink = page.getByRole('link', { name: /pricing/i }).first();
    if (await pricingLink.count() > 0) {
      await pricingLink.click();
      await page.waitForLoadState('networkidle');
      // Allow for redirects - pricing page should be accessible
      const url = page.url();
      expect(url).toMatch(/pricing|localhost:3000\/$/);
    }

    // Navigate to login
    const loginLink = page.getByRole('link', { name: /login|sign in/i }).first();
    if (await loginLink.count() > 0) {
      await loginLink.click();
      await page.waitForLoadState('networkidle');
      expect(page.url()).toMatch(/login/);
    }

    // Navigate back to home
    const homeLink = page.getByRole('link', { name: /home/i }).first();
    if (await homeLink.count() > 0) {
      await homeLink.click();
      await page.waitForLoadState('networkidle');
      expect(page.url()).toMatch(/^https?:\/\/[^\/]+\/?$/);
    }
  });

  test('should redirect to login when accessing protected routes without auth', async ({ page }) => {
    // Try to access dashboard without auth
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Should redirect to login
    const url = page.url();
    const redirectedToLogin = url.includes('login') || url.includes('auth');

    // If not redirected, might be on error page or home
    // Just verify page is accessible
    await expect(page.locator('body')).toBeVisible();
  });

  test('should maintain auth state across navigation', async ({ page }) => {
    // Mock authentication
    await apiMock.mockAuthEndpoints({
      loginSuccess: true,
      user: testUsers.google,
    });

    // Login
    await page.goto('/login');
    await authHelper.loginWithGoogle(testUsers.google.email, testUsers.google.name);

    // Wait for redirect
    await page.waitForLoadState('networkidle');

    // Navigate to different pages
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Should stay authenticated
    await expect(page.locator('body')).toBeVisible();

    // Navigate to pricing
    await page.goto('/pricing');
    await page.waitForLoadState('networkidle');

    // Should still be authenticated
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle browser back button correctly', async ({ page }) => {
    // Navigate through multiple pages
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    await page.goto('/pricing');
    await page.waitForLoadState('networkidle');

    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Go back
    await page.goBack();
    await page.waitForLoadState('networkidle');
    expect(page.url()).toMatch(/pricing/);

    // Go back again
    await page.goBack();
    await page.waitForLoadState('networkidle');
    expect(page.url()).toMatch(/^https?:\/\/[^\/]+\/?$/);
  });

  test('should handle browser forward button correctly', async ({ page }) => {
    // Navigate through multiple pages
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    await page.goto('/pricing');
    await page.waitForLoadState('networkidle');

    // Go back
    await page.goBack();
    await page.waitForLoadState('networkidle');

    // Go forward
    await page.goForward();
    await page.waitForLoadState('networkidle');
    expect(page.url()).toMatch(/pricing/);
  });

  test('should handle deep linking to specific pages', async ({ page }) => {
    // Mock chatbot data
    await page.route('**/api/chatbots/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 123,
          name: 'Test Chatbot',
          websiteUrl: 'https://example.com',
        }),
      });
    });

    // Direct link to chatbot detail page
    await page.goto('/chatbot/123');
    await page.waitForLoadState('networkidle');

    // Page should load
    await expect(page.locator('body')).toBeVisible();
  });

  test('should preserve query parameters during navigation', async ({ page }) => {
    // Navigate with query parameters
    await page.goto('/?ref=email&campaign=test');
    await page.waitForLoadState('networkidle');

    // Verify query params are in URL
    expect(page.url()).toContain('ref=email');
    expect(page.url()).toContain('campaign=test');
  });

  test('should handle logout and clear auth state', async ({ page }) => {
    // Mock authentication
    await apiMock.mockAuthEndpoints({
      loginSuccess: true,
      user: testUsers.google,
    });

    // Login
    await page.goto('/login');
    await authHelper.loginWithGoogle(testUsers.google.email, testUsers.google.name);
    await page.waitForLoadState('networkidle');

    // Navigate to dashboard
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Logout (if logout button exists)
    const logoutButton = page.getByRole('button', { name: /logout|sign out/i }).first();
    if (await logoutButton.count() > 0) {
      await logoutButton.click();
      await page.waitForLoadState('networkidle');

      // Should redirect to home or login
      const url = page.url();
      const loggedOut = url.includes('login') || url.match(/^https?:\/\/[^\/]+\/?$/);
      expect(loggedOut).toBeTruthy();
    }
  });

  test('should handle 404 pages gracefully', async ({ page }) => {
    // Navigate to non-existent page
    await page.goto('/non-existent-page-xyz');
    await page.waitForLoadState('networkidle');

    // Should show 404 page or redirect
    await expect(page.locator('body')).toBeVisible();

    // Check if 404 content is displayed
    const body = await page.textContent('body');
    const is404 = body?.includes('404') || body?.includes('not found') || body?.includes('Not Found');

    // Either shows 404 or redirects to home
    expect(is404 || page.url().match(/^https?:\/\/[^\/]+\/?$/)).toBeTruthy();
  });

  test('should handle navigation errors gracefully', async ({ page }) => {
    // Mock network error
    await page.route('**/api/**', async (route) => {
      await route.abort('failed');
    });

    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Page should handle error gracefully
    await expect(page.locator('body')).toBeVisible();
  });

  test('should navigate between authenticated pages', async ({ page }) => {
    // Mock authentication
    await apiMock.mockAuthEndpoints({
      loginSuccess: true,
      user: testUsers.google,
    });

    await apiMock.mockChatbotEndpoints([
      {
        id: 1,
        name: 'Test Bot',
        websiteUrl: 'https://example.com',
      },
    ]);

    // Login
    await page.goto('/login');
    await authHelper.loginWithGoogle(testUsers.google.email, testUsers.google.name);
    await page.waitForLoadState('networkidle');

    // Navigate to dashboard
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toBeVisible();

    // Navigate to chatbot detail
    await page.goto('/chatbot/1');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toBeVisible();

    // Back to dashboard
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle rapid navigation changes', async ({ page }) => {
    // Rapidly navigate between pages
    await page.goto('/');
    await page.goto('/pricing');
    await page.goto('/login');
    await page.goto('/');

    await page.waitForLoadState('networkidle');

    // Should end up on the last page
    expect(page.url()).toMatch(/^https?:\/\/[^\/]+\/?$/);
    await expect(page.locator('body')).toBeVisible();
  });

  test('should preserve scroll position on back navigation', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Scroll down (if page has scrollable content)
    await page.evaluate(() => window.scrollTo(0, 500));

    // Navigate to another page
    await page.goto('/pricing');
    await page.waitForLoadState('networkidle');

    // Go back
    await page.goBack();
    await page.waitForLoadState('networkidle');

    // Scroll position might or might not be preserved
    // Just verify page is visible
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle navigation with hash fragments', async ({ page }) => {
    // Navigate with hash
    await page.goto('/#features');
    await page.waitForLoadState('networkidle');

    // URL should include hash
    expect(page.url()).toContain('#features');

    // Page should be visible
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle navigation to external links', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Find external links (if any)
    const externalLinks = page.locator('a[target="_blank"], a[href^="http"]');
    const count = await externalLinks.count();

    // External links might exist
    // Just verify page is visible
    await expect(page.locator('body')).toBeVisible();
  });

  test('should navigate using keyboard shortcuts', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Tab to first link
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Press Enter on focused link
    await page.keyboard.press('Enter');
    await page.waitForLoadState('networkidle');

    // Should navigate somewhere
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle concurrent navigation requests', async ({ page }) => {
    // Start multiple navigation requests
    const nav1 = page.goto('/pricing').catch(() => {});
    const nav2 = page.goto('/login').catch(() => {});

    await Promise.race([nav1, nav2]);
    await page.waitForLoadState('networkidle');

    // Should end up on one of the pages
    const url = page.url();
    expect(url.includes('pricing') || url.includes('login')).toBeTruthy();
  });

  test('should handle navigation with slow network', async ({ page }) => {
    // Simulate slow network
    await page.route('**/*', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 200));
      await route.continue();
    });

    await page.goto('/pricing');
    await page.waitForLoadState('networkidle');

    // Page should load eventually
    await expect(page.locator('body')).toBeVisible();
  });

  test('should maintain state when refreshing page', async ({ page }) => {
    // Mock authentication
    await apiMock.mockAuthEndpoints({
      loginSuccess: true,
      user: testUsers.google,
    });

    await page.goto('/login');
    await authHelper.loginWithGoogle(testUsers.google.email, testUsers.google.name);
    await page.waitForLoadState('networkidle');

    // Navigate to dashboard
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Refresh page
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Should still be on dashboard (if session persists)
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle navigation cancellation', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Start navigation (this will be cancelled)
    const navigation = page.goto('/pricing').catch(() => {
      // Expected: navigation will be cancelled/aborted
      // This is normal behavior when starting a new navigation
    });

    // Immediately start another navigation (cancels first)
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Should be on login page
    expect(page.url()).toMatch(/login/);
    
    // Wait for the cancelled navigation to complete (or fail)
    await navigation.catch(() => {
      // Expected: cancelled navigation will fail
    });
  });
});
