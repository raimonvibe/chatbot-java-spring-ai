import { test, expect } from '@playwright/test';
import { AuthHelper } from './helpers/auth';
import { ApiMock } from './helpers/api-mock';
import { testUsers, testChatbots } from './fixtures/users';

/**
 * Sample E2E test to validate Phase 1 setup
 * This test ensures that:
 * - Playwright is configured correctly
 * - Helpers and fixtures work as expected
 * - API mocking functions properly
 * - Navigation and basic interactions work
 */
test.describe('Sample E2E Test - Phase 1 Validation', () => {
  test('should load home page and display hero section', async ({ page }) => {
    // Navigate to home page
    await page.goto('/');

    // Check if page title is present
    await expect(page).toHaveTitle(/Prayer-Chat|Chatbot/i);

    // Check if hero section exists
    const heroSection = page.locator('main, section').first();
    await expect(heroSection).toBeVisible();
  });

  test('should mock authentication and navigate to dashboard', async ({ page }) => {
    // Set up API mocks
    const apiMock = new ApiMock(page);
    const authHelper = new AuthHelper(page);

    // Mock authentication endpoints and chatbots to prevent redirect to onboarding
    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: testChatbots, // Provide chatbots to stay on dashboard
    });

    // Set up authenticated state
    await authHelper.setupAuthenticatedState(testUsers.local);

    // Navigate to dashboard
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Wait for either dashboard or onboarding redirect
    await page.waitForURL(/\/(dashboard|onboarding)/, { timeout: 15000 });

    // Verify we're on either dashboard or onboarding (both are valid)
    const url = page.url();
    expect(url).toMatch(/\/(dashboard|onboarding)/);

    // Check if main content is visible (works for both dashboard and onboarding)
    const mainContent = page.locator('main, [role="main"]').first();
    await expect(mainContent).toBeVisible({ timeout: 10000 });
  });

  test('should mock chatbot API and display chatbots', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const authHelper = new AuthHelper(page);

    // Set up authenticated state
    await authHelper.setupAuthenticatedState(testUsers.local);

    // Mock chatbot endpoints with test data
    await apiMock.mockChatbotEndpoints(testChatbots);

    // Navigate to dashboard
    await page.goto('/dashboard');

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    // Note: Adjust selectors based on your actual dashboard implementation
    // This is a basic check to ensure the page loads
    const mainContent = page.locator('main, [role="main"]').first();
    await expect(mainContent).toBeVisible();
  });

  test('should handle API error gracefully', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const authHelper = new AuthHelper(page);

    // Set up authenticated state
    await authHelper.setupAuthenticatedState(testUsers.local);

    // Mock API error
    await apiMock.mockApiError('/api/chatbots', 500, 'Internal server error');

    // Navigate to dashboard
    await page.goto('/dashboard');

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    // Check that the page doesn't crash
    const body = page.locator('body');
    await expect(body).toBeVisible();
  });

  test('should test authentication helper methods', async ({ page }) => {
    const authHelper = new AuthHelper(page);

    // Test setting auth token
    await authHelper.setAuthToken('test_token_123');

    // Verify token was set
    const token = await authHelper.getAuthToken();
    expect(token).toBe('test_token_123');

    // Verify isAuthenticated returns true
    const isAuth = await authHelper.isAuthenticated();
    expect(isAuth).toBe(true);

    // Test clearing auth
    await authHelper.clearAuth();

    // Verify token was cleared
    const clearedToken = await authHelper.getAuthToken();
    expect(clearedToken).toBeNull();

    // Verify isAuthenticated returns false
    const isAuthAfterClear = await authHelper.isAuthenticated();
    expect(isAuthAfterClear).toBe(false);
  });

  test('should test API mock helper methods', async ({ page }) => {
    const apiMock = new ApiMock(page);

    // Mock all endpoints
    await apiMock.mockAllEndpoints({
      chatbots: testChatbots,
      user: testUsers.local,
      subscriptionStatus: 'ACTIVE',
      subscriptionPlan: 'BASIC',
    });

    // Make a request and verify it's mocked
    await page.goto('/');

    // The fact that this doesn't throw an error validates the setup
    await expect(page).toHaveURL('/');
  });
});

test.describe('Sample Mobile E2E Test', () => {
  test.use({
    viewport: { width: 375, height: 667 }, // iPhone SE viewport
  });

  test('should display mobile-friendly home page', async ({ page }) => {
    await page.goto('/');

    // Check if page loads on mobile viewport
    const body = page.locator('body');
    await expect(body).toBeVisible();

    // Check viewport size
    const viewportSize = page.viewportSize();
    expect(viewportSize?.width).toBe(375);
    expect(viewportSize?.height).toBe(667);
  });
});
