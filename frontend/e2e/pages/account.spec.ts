import { test, expect } from '@playwright/test';
import { AuthHelper } from '../helpers/auth';
import { ApiMock } from '../helpers/api-mock';
import { testUsers } from '../fixtures/users';

/**
 * Account Page E2E Tests
 *
 * - Redirect to login when not authenticated
 * - Show profile and subscription when authenticated
 * - Links to Privacy, Legal, Contact and Back to Dashboard
 * - Manage subscription and Sign out actions
 */
test.describe('Account Page', () => {
  test('should redirect to login when not authenticated', async ({ page }) => {
    const apiMock = new ApiMock(page);
    await apiMock.mockUnauthenticated();
    await page.goto('/account');
    await page.waitForURL(/\/login/, { timeout: 10000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test('should show account page for authenticated user', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);
    await apiMock.mockAllEndpoints({
      user: testUsers.google,
      subscriptionPlan: 'FREE',
      subscriptionStatus: 'ACTIVE',
      chatbots: [],
    });
    await authHelper.setupAuthenticatedState(testUsers.google);

    await page.goto('/account');
    await page.waitForLoadState('networkidle');

    await expect(page.getByRole('heading', { name: /Account/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Profile' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Subscription' })).toBeVisible();
  });

  test('should display user email and Sign in with Google', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);
    const googleUser = { ...testUsers.google, email: 'test@gmail.com', username: 'test@gmail.com' };
    await apiMock.mockAllEndpoints({
      user: googleUser,
      subscriptionPlan: 'FREE',
      subscriptionStatus: 'ACTIVE',
      chatbots: [],
    });
    await authHelper.setupAuthenticatedState(googleUser);

    await page.goto('/account');
    await page.waitForLoadState('networkidle');

    const main = page.getByRole('main');
    await expect(main.getByText('test@gmail.com')).toBeVisible();
    await expect(main.getByText('You are signed in with Google.')).toBeVisible();
  });

  test('should have Manage subscription and Sign out buttons', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);
    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [],
    });
    await authHelper.setupAuthenticatedState(testUsers.local);

    await page.goto('/account');
    await page.waitForLoadState('networkidle');

    await expect(page.getByRole('button', { name: /Manage subscription/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Sign out/i })).toBeVisible();
  });

  test('should have links to Privacy, Legal, Contact and Dashboard', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);
    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'FREE',
      subscriptionStatus: 'ACTIVE',
      chatbots: [],
    });
    await authHelper.setupAuthenticatedState(testUsers.local);

    await page.goto('/account');
    await page.waitForLoadState('networkidle');

    const main = page.getByRole('main');
    await expect(main.getByRole('link', { name: 'Privacy Notice' })).toHaveAttribute('href', '/privacy');
    await expect(main.getByRole('link', { name: 'Legal Notice' })).toHaveAttribute('href', '/legal');
    await expect(main.getByRole('link', { name: 'Contact' })).toHaveAttribute('href', '/contact');
    await expect(main.getByRole('link', { name: /Back to Dashboard/i })).toHaveAttribute('href', '/dashboard');
  });
});
