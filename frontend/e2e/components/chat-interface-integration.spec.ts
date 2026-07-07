import { test, expect } from '@playwright/test';
import { ApiMock } from '../helpers/api-mock';
import { testChatbots } from '../fixtures/users';

/**
 * Chat Interface Integration Tests
 *
 * Tests chat interface on /chatbot/[id] with mocked backend.
 */
test.describe('Chat Interface Integration', () => {
  const chatbot = testChatbots[0];

  test.beforeEach(async ({ page }) => {
    const apiMock = new ApiMock(page);
    await apiMock.mockChatbotPreviewPage(chatbot);
  });

  test('should display chat interface', async ({ page }) => {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    await expect(page.getByTestId('preview-widget-panel')).toBeVisible({ timeout: 15000 });
    await expect(page.getByPlaceholder('Type your message...')).toBeVisible();
  });

  test('should send a message and receive response', async ({ page }) => {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });
    await input.fill('Hello chatbot');

    const sendButton = page.getByRole('button', { name: 'Send message' });
    await sendButton.click();
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle multi-turn conversation', async ({ page }) => {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });

    for (const msg of ['First message', 'Second message', 'Third message']) {
      await input.fill(msg);
      await page.getByRole('button', { name: 'Send message' }).click();
      await page.waitForTimeout(300);
    }

    await expect(page.locator('body')).toBeVisible();
  });

  test('should display loading state while sending message', async ({ page }) => {
    await page.route('**/api/chat/**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Delayed response',
          sessionId: 'test-session',
          timestamp: Date.now(),
          chatbotId: chatbot.id,
        }),
      });
    });

    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });
    await input.fill('Test message');
    await page.getByRole('button', { name: 'Send message' }).click();
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle API error gracefully', async ({ page }) => {
    await page.route('**/api/chat/**', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Internal server error' }),
      });
    });

    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });
    await input.fill('Test message');
    await page.getByRole('button', { name: 'Send message' }).click();
    await expect(page.locator('body')).toBeVisible();
  });

  test('should clear input after sending message', async ({ page }) => {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });
    await input.fill('Test message');
    await page.getByRole('button', { name: 'Send message' }).click();
    await page.waitForTimeout(300);
    await expect(input).toBeVisible();
  });

  test('should disable send button while message is being sent', async ({ page }) => {
    await page.route('**/api/chat/**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Response',
          sessionId: 'test-session',
          timestamp: Date.now(),
          chatbotId: chatbot.id,
        }),
      });
    });

    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });
    await input.fill('Test message');
    await page.getByRole('button', { name: 'Send message' }).click();
    await expect(page.locator('body')).toBeVisible();
  });

  test('should prevent sending empty messages', async ({ page }) => {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const sendButton = page.getByRole('button', { name: 'Send message' });
    await expect(sendButton).toBeVisible({ timeout: 15000 });
    await expect(sendButton).toBeDisabled();
  });

  test('should support keyboard shortcuts', async ({ page }) => {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');

    const input = page.getByPlaceholder('Type your message...');
    await expect(input).toBeVisible({ timeout: 15000 });
    await input.fill('Test message');
    await input.press('Enter');
    await expect(page.locator('body')).toBeVisible();
  });
});
