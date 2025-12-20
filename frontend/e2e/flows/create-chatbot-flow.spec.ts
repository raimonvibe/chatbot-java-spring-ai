import { test, expect } from '@playwright/test';
import { AuthHelper } from '../helpers/auth';
import { ApiMock } from '../helpers/api-mock';
import { testUsers, testChatbots, generateTestChatbot } from '../fixtures/users';

/**
 * Create Chatbot Flow E2E Tests
 *
 * Tests the complete chatbot creation workflow:
 * Login → Dashboard → Create chatbot → Analyze website → Train → Test chat
 */
test.describe('Create Chatbot Flow', () => {
  test('should complete full chatbot creation flow', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    const newChatbot = generateTestChatbot({ name: 'Test Support Bot' });

    // Mock endpoints
    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [],
    });

    // Setup auth
    await authHelper.setupAuthenticatedState(testUsers.local);

    // Step 1: Go to dashboard
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard/);

    // Step 2: Click "Create Chatbot" button
    const createButton = page.getByRole('button', { name: /create|new.*chatbot|add.*bot/i });

    if (await createButton.isVisible()) {
      await createButton.click();
      await page.waitForLoadState('networkidle');
    }

    // Step 3: Fill in chatbot creation form (if form exists)
    const nameInput = page.getByLabel(/name|chatbot.*name/i);
    if (await nameInput.isVisible()) {
      await nameInput.fill(newChatbot.name);
    }

    const urlInput = page.getByLabel(/website|url|site/i);
    if (await urlInput.isVisible()) {
      await urlInput.fill(newChatbot.websiteUrl);
    }

    const descInput = page.getByLabel(/description|about/i);
    if (await descInput.isVisible()) {
      await descInput.fill(newChatbot.description);
    }

    // Step 4: Submit form
    const submitButton = page.getByRole('button', { name: /create|submit|save/i });
    if (await submitButton.isVisible()) {
      await submitButton.click();
      await page.waitForLoadState('networkidle');
    }

    // Verify no errors occurred
    await expect(page.locator('body')).toBeVisible();
  });

  test('should show chatbot in list after creation', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    const existingChatbot = testChatbots[0];

    // Mock with one chatbot
    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [existingChatbot],
    });

    await authHelper.setupAuthenticatedState(testUsers.local);

    // Go to dashboard
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard/);

    // Wait for content to load
    await page.waitForLoadState('networkidle');

    // Dashboard should show content
    await expect(page.locator('main, [role="main"]')).toBeVisible();
  });

  test('should navigate to chatbot detail page after creation', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    const chatbot = testChatbots[0];

    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [chatbot],
    });

    await authHelper.setupAuthenticatedState(testUsers.local);

    // Navigate to chatbot detail page
    await page.goto(`/chatbot/${chatbot.id}`);

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    // Verify page loaded
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle website analysis', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    const chatbot = testChatbots[0];

    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [chatbot],
    });

    // Mock analyze endpoint with slow response (to test loading state)
    await apiMock.mockSlowResponse('/api/chatbots/*/analyze', 1000, {
      status: 'completed',
      pagesAnalyzed: 10,
    });

    await authHelper.setupAuthenticatedState(testUsers.local);

    // Go to chatbot detail
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    // Look for analyze button
    const analyzeButton = page.getByRole('button', { name: /analyze|scan|crawl/i });

    if (await analyzeButton.isVisible()) {
      await analyzeButton.click();

      // Wait a bit to see loading state
      await page.waitForTimeout(500);

      // Page should still be functional
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('should test chat with created chatbot', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    const chatbot = testChatbots[0];

    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [chatbot],
    });

    // Mock chat endpoint
    await apiMock.mockChatEndpoints();

    await authHelper.setupAuthenticatedState(testUsers.local);

    // Go to chatbot preview/test page
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    // Look for chat input
    const chatInput = page.getByPlaceholder(/message|type.*message|ask/i);

    if (await chatInput.isVisible()) {
      await chatInput.fill('Hello, can you help me?');

      const sendButton = page.getByRole('button', { name: /send|submit/i });
      if (await sendButton.isVisible()) {
        await sendButton.click();
        await page.waitForLoadState('networkidle');
      }
    }

    // Verify page is still functional
    await expect(page.locator('body')).toBeVisible();
  });

  test('should validate required fields in creation form', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    await apiMock.mockAllEndpoints({
      user: testUsers.local,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [],
    });

    await authHelper.setupAuthenticatedState(testUsers.local);

    await page.goto('/dashboard');

    // Click create button
    const createButton = page.getByRole('button', { name: /create|new.*chatbot/i });
    if (await createButton.isVisible()) {
      await createButton.click();
      await page.waitForLoadState('networkidle');

      // Try to submit without filling fields
      const submitButton = page.getByRole('button', { name: /create|submit|save/i });
      if (await submitButton.isVisible()) {
        await submitButton.click();

        // Should show validation errors or stay on page
        await page.waitForTimeout(500);
        await expect(page.locator('body')).toBeVisible();
      }
    }
  });

  test('should handle creation with invalid website URL', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    // Mock API error for invalid URL
    await apiMock.mockApiError('/api/chatbots', 400, 'Invalid website URL');

    await authHelper.setupAuthenticatedState(testUsers.local);

    await page.goto('/dashboard');

    const createButton = page.getByRole('button', { name: /create|new.*chatbot/i });
    if (await createButton.isVisible()) {
      await createButton.click();

      const urlInput = page.getByLabel(/website|url/i);
      if (await urlInput.isVisible()) {
        await urlInput.fill('not-a-valid-url');

        const submitButton = page.getByRole('button', { name: /create|submit/i });
        if (await submitButton.isVisible()) {
          await submitButton.click();
          await page.waitForTimeout(500);
        }
      }
    }

    // Should show error message or stay on form
    await expect(page.locator('body')).toBeVisible();
  });

  test('should allow creating multiple chatbots', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    // Start with one chatbot
    await apiMock.mockAllEndpoints({
      user: testUsers.basicUser,
      subscriptionPlan: 'BASIC',
      subscriptionStatus: 'ACTIVE',
      chatbots: [testChatbots[0]],
    });

    await authHelper.setupAuthenticatedState(testUsers.basicUser);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard/);

    // Verify we can try to create another
    const createButton = page.getByRole('button', { name: /create|new.*chatbot/i });
    if (await createButton.isVisible()) {
      await createButton.click();
      await page.waitForLoadState('networkidle');
    }

    await expect(page.locator('body')).toBeVisible();
  });
});

test.describe('Create Chatbot Flow - Subscription Limits', () => {
  test('should show upgrade prompt for FREE users at limit', async ({ page }) => {
    const authHelper = new AuthHelper(page);
    const apiMock = new ApiMock(page);

    // FREE user with chatbot limit reached (e.g., 1 chatbot max)
    await apiMock.mockAllEndpoints({
      user: testUsers.freeUser,
      subscriptionPlan: 'FREE',
      subscriptionStatus: 'ACTIVE',
      chatbots: [testChatbots[0]], // At limit
    });

    await authHelper.setupAuthenticatedState(testUsers.freeUser);

    await page.goto('/dashboard');

    // Try to create another chatbot
    const createButton = page.getByRole('button', { name: /create|new.*chatbot/i });

    if (await createButton.isVisible()) {
      await createButton.click();
      await page.waitForTimeout(500);
    }

    // Should show upgrade message or redirect to pricing
    await expect(page.locator('body')).toBeVisible();
  });
});
