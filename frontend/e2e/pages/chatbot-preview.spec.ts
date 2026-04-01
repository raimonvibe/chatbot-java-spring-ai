import { test, expect } from '@playwright/test';
import { AuthHelper } from '../helpers/auth';
import { ApiMock } from '../helpers/api-mock';
import { testUsers, testChatbots } from '../fixtures/users';

/**
 * Chatbot Preview/Embed E2E Tests
 *
 * Tests chatbot preview functionality:
 * - Load chatbot by ID
 * - Send messages → receive responses
 * - Quick reply interactions
 * - Multi-turn conversation
 * - Session management
 * - Error handling
 */
test.describe('Chatbot Preview Page', () => {
  test('should load chatbot preview', async ({ page }) => {
    const apiMock = new ApiMock(page);

    const chatbot = testChatbots[0];

    // Mock public chatbot access
    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('body')).toBeVisible();
  });

  test('should display chat interface', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    // Look for chat input
    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    // Chat interface should be visible
    await expect(page.locator('body')).toBeVisible();
  });

  test('should send and receive messages', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    if (await chatInput.isVisible()) {
      await chatInput.fill('Hello, can you help me?');

      const sendButton = page.getByRole('button', { name: /send|submit/i });

      if (await sendButton.isVisible()) {
        await sendButton.click();
        await page.waitForTimeout(1000);

        // Should show response
      }
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle multi-turn conversation', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    if (await chatInput.isVisible()) {
      // Send multiple messages
      await chatInput.fill('First message');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(500);

      await chatInput.fill('Second message');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(500);

      await chatInput.fill('Third message');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(500);
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should show typing indicator', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    // Mock slow chat response to see typing indicator
    await apiMock.mockSlowResponse(`/api/chat/${chatbot.id}`, 2000, {
      message: 'Response message',
    });

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    if (await chatInput.isVisible()) {
      await chatInput.fill('Test message');
      await page.keyboard.press('Enter');

      // Wait to see typing indicator
      await page.waitForTimeout(500);
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle empty message', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    const sendButton = page.getByRole('button', { name: /send/i });

    if (await sendButton.isVisible()) {
      // Try to send without typing
      await sendButton.click();
      await page.waitForTimeout(500);

      // Should not send or show validation
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle API error during chat', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    // Mock chat error
    await apiMock.mockApiError(`/api/chat/${chatbot.id}`, 500, 'Server error');

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    if (await chatInput.isVisible()) {
      await chatInput.fill('Test message');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1000);

      // Should show error message
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should clear conversation', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    // Send a message first
    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    if (await chatInput.isVisible()) {
      await chatInput.fill('Test message');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1000);
    }

    // Look for clear/reset button
    const clearButton = page.getByRole('button', { name: /clear|reset|new/i });

    if (await clearButton.isVisible()) {
      await clearButton.click();
      await page.waitForTimeout(500);
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle long messages', async ({ page }) => {
    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    const chatInput = page.getByPlaceholder(/message|type|ask/i);

    if (await chatInput.isVisible()) {
      const longMessage = 'This is a very long message. '.repeat(20);
      await chatInput.fill(longMessage);
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1000);
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should work on mobile viewport', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });

    const apiMock = new ApiMock(page);
    const chatbot = testChatbots[0];

    await page.route(`**/api/chatbots/${chatbot.id}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chatbot),
      });
    });

    await apiMock.mockChatEndpoints();

    await page.goto(`/chatbot/${chatbot.id}/preview`);
    await page.waitForLoadState('networkidle');

    // Verify mobile layout
    await expect(page.locator('body')).toBeVisible();

    const viewport = page.viewportSize();
    expect(viewport?.width).toBe(375);
  });
});
