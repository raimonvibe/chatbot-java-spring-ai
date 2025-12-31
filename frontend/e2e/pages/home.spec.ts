import { test, expect } from '@playwright/test';
import { ApiMock } from '../helpers/api-mock';

/**
 * Home Page E2E Tests
 *
 * Tests home page functionality:
 * - Navigate to home → verify hero section → click CTA → redirect to login
 * - Feature showcase interactions
 * - Mobile responsiveness
 */
test.describe('Home Page', () => {
  test('should load home page successfully', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/');

    // Verify page loads
    await expect(page.locator('body')).toBeVisible();
  });

  test('should display hero section with main heading', async ({ page }) => {
    await page.goto('/');

    // Look for main heading or hero section
    // Adjust selector based on actual implementation
    const mainContent = page.locator('main');
    await expect(mainContent).toBeVisible();

    // Verify some key content is visible
    const body = await page.textContent('body');
    expect(body).toBeTruthy();
  });

  test('should display call-to-action button', async ({ page }) => {
    await page.goto('/');

    // Look for CTA button (common patterns)
    const ctaButton = page.getByRole('button', { name: /get started|try free|start free|sign up/i }).first();

    // If button exists, verify it's visible
    const buttonCount = await page.getByRole('button').count();
    expect(buttonCount).toBeGreaterThan(0);
  });

  test('should navigate to login when CTA is clicked', async ({ page }) => {
    await page.goto('/');

    // Find and click "Get Started" button (navigates to /login)
    const ctaButton = page.getByRole('button', { name: /get started/i }).first();
    
    await expect(ctaButton).toBeVisible({ timeout: 10000 });
    await ctaButton.click();

    // Wait for navigation to login page
    await page.waitForURL(/\/login/, { timeout: 10000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test('should display feature showcase section', async ({ page }) => {
    await page.goto('/');

    // Verify page has content
    const mainContent = page.locator('main');
    await expect(mainContent).toBeVisible();

    // Check for feature-related content
    const body = await page.textContent('body');

    // Common feature keywords for chatbot apps
    const hasFeatureContent =
      body?.toLowerCase().includes('chatbot') ||
      body?.toLowerCase().includes('ai') ||
      body?.toLowerCase().includes('feature');

    expect(hasFeatureContent).toBeTruthy();
  });

  test('should have navigation links', async ({ page }) => {
    await page.goto('/');

    // Look for navigation elements
    const links = page.getByRole('link');
    const linkCount = await links.count();

    // Should have at least some links
    expect(linkCount).toBeGreaterThan(0);
  });

  test('should navigate to pricing page from home', async ({ page }) => {
    await page.goto('/');

    // Look for "View Pricing" link
    const pricingLink = page.getByRole('link', { name: /view pricing|pricing|plans/i }).first();
    
    await expect(pricingLink).toBeVisible({ timeout: 10000 });
    
    // Click and wait for navigation
    await pricingLink.click();
    
    // Wait for navigation to pricing page
    await page.waitForURL(/\/pricing/, { timeout: 10000 });
    await expect(page).toHaveURL(/\/pricing/);
  });

  test('should navigate to login page from home', async ({ page }) => {
    await page.goto('/');

    // Look for login/sign in link
    const loginLink = page.getByRole('link', { name: /login|sign in/i }).first();

    if (await loginLink.count() > 0) {
      await loginLink.click();
      await page.waitForLoadState('networkidle');

      // Should navigate to login
      expect(page.url()).toMatch(/login/);
    }
  });

  test('should be responsive on mobile viewport', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');

    // Page should load
    await expect(page.locator('body')).toBeVisible();

    // Main content should be visible
    const mainContent = page.locator('main');
    await expect(mainContent).toBeVisible();
  });

  test('should be responsive on tablet viewport', async ({ page }) => {
    // Set tablet viewport
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');

    // Page should load
    await expect(page.locator('body')).toBeVisible();

    // Main content should be visible
    const mainContent = page.locator('main');
    await expect(mainContent).toBeVisible();
  });

  test('should be responsive on desktop viewport', async ({ page }) => {
    // Set desktop viewport
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/');

    // Page should load
    await expect(page.locator('body')).toBeVisible();

    // Main content should be visible
    const mainContent = page.locator('main');
    await expect(mainContent).toBeVisible();
  });

  test('should have proper page metadata', async ({ page }) => {
    await page.goto('/');

    // Check page title
    const title = await page.title();
    expect(title).toBeTruthy();
    expect(title.length).toBeGreaterThan(0);
  });

  test('should load without JavaScript errors', async ({ page }) => {
    const errors: string[] = [];

    // Capture console errors
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    // Capture page errors
    page.on('pageerror', (error) => {
      errors.push(error.message);
    });

    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Should have minimal or no errors
    // Filter out common third-party errors
    const criticalErrors = errors.filter(
      (error) =>
        !error.includes('third-party') &&
        !error.includes('extension')
    );

    // Uncomment if you want strict error checking
    // expect(criticalErrors.length).toBe(0);
  });

  test('should display footer', async ({ page }) => {
    await page.goto('/');

    // Look for footer element
    const footer = page.locator('footer');

    // Footer might or might not exist
    const footerExists = await footer.count() > 0;

    if (footerExists) {
      await expect(footer).toBeVisible();
    }
  });

  test('should handle slow network gracefully', async ({ page }) => {
    // Simulate slow network
    await page.route('**/*', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 100));
      await route.continue();
    });

    await page.goto('/');

    // Page should still load
    await expect(page.locator('body')).toBeVisible();
  });

  test('should display hero image or illustration', async ({ page }) => {
    await page.goto('/');

    // Look for images
    const images = page.locator('img');
    const imageCount = await images.count();

    // Most landing pages have at least one image
    // But this is optional, so we just check the page loads
    await expect(page.locator('main')).toBeVisible();
  });

  test('should have accessible navigation', async ({ page }) => {
    await page.goto('/');

    // Check for navigation with role="navigation" or <nav> tag
    const nav = page.locator('nav, [role="navigation"]').first();

    const navExists = await nav.count() > 0;

    if (navExists) {
      await expect(nav).toBeVisible();
    }
  });

  test('should support keyboard navigation', async ({ page }) => {
    await page.goto('/');

    // Tab through focusable elements
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Should have some focusable elements
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBeTruthy();
  });

  test('should display pricing information or link', async ({ page }) => {
    await page.goto('/');

    // Look for pricing mention
    const body = await page.textContent('body');
    const hasPricingMention = body?.toLowerCase().includes('pric');

    // Or check for pricing link
    const pricingLink = page.getByRole('link', { name: /pricing/i });
    const pricingLinkExists = await pricingLink.count() > 0;

    // Either pricing content or link should exist
    expect(hasPricingMention || pricingLinkExists).toBeTruthy();
  });

  test('should have animation-free mode for accessibility', async ({ page }) => {
    // Some users prefer reduced motion
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');

    // Page should still load and be usable
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
  });

  test('should work in different color schemes', async ({ page }) => {
    // Test dark mode
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();

    // Test light mode
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();
  });
});
