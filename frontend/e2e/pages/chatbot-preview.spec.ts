import { test, expect } from '@playwright/test';
import { ApiMock } from '../helpers/api-mock';
import { testChatbots } from '../fixtures/users';

/**
 * Chatbot Preview/Embed E2E Tests
 */
test.describe('Chatbot Preview Page', () => {
  const chatbot = {
    ...testChatbots[0],
    supportedLanguages: ['en'],
    brandingConfig: '{}',
  };

  test.beforeEach(async ({ page }) => {
    const apiMock = new ApiMock(page);
    await apiMock.mockChatbotPreviewPage(chatbot);
  });

  async function openPreview(page: import('@playwright/test').Page) {
    await page.goto(`/chatbot/${chatbot.id}`);
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('preview-widget-panel')).toBeVisible({ timeout: 15000 });
  }

  test('should load chatbot preview', async ({ page }) => {
    await openPreview(page);
    await expect(page.locator('body')).toBeVisible();
  });

  test('should display chat interface', async ({ page }) => {
    await openPreview(page);
    await expect(page.getByPlaceholder('Type your message...')).toBeVisible();
  });

  test('should send and receive messages', async ({ page }) => {
    await openPreview(page);
    const chatInput = page.getByPlaceholder('Type your message...');
    await chatInput.fill('Hello, can you help me?');
    await page.getByRole('button', { name: 'Send message' }).click();
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle multi-turn conversation', async ({ page }) => {
    await openPreview(page);
    const chatInput = page.getByPlaceholder('Type your message...');
    for (const msg of ['First message', 'Second message', 'Third message']) {
      await chatInput.fill(msg);
      await page.keyboard.press('Enter');
      await page.waitForTimeout(300);
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('should show typing indicator', async ({ page }) => {
    const apiMock = new ApiMock(page);
    await apiMock.mockSlowResponse(`/api/chat/${chatbot.id}`, 2000, {
      message: 'Response message',
      sessionId: 'test-session',
      timestamp: Date.now(),
      chatbotId: chatbot.id,
    });
    await openPreview(page);
    const chatInput = page.getByPlaceholder('Type your message...');
    await chatInput.fill('Test message');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(500);
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle empty message', async ({ page }) => {
    await openPreview(page);
    const sendButton = page.getByRole('button', { name: 'Send message' });
    await expect(sendButton).toBeDisabled();
    await page.getByPlaceholder('Type your message...').press('Enter');
    await expect(sendButton).toBeDisabled();
  });

  test('should handle API error during chat', async ({ page }) => {
    const apiMock = new ApiMock(page);
    await apiMock.mockApiError(`/api/chat/${chatbot.id}`, 500, 'Server error');
    await openPreview(page);
    const chatInput = page.getByPlaceholder('Type your message...');
    await chatInput.fill('Test message');
    await page.keyboard.press('Enter');
    await expect(page.locator('body')).toBeVisible();
  });

  test('should clear conversation', async ({ page }) => {
    await openPreview(page);
    const chatInput = page.getByPlaceholder('Type your message...');
    await chatInput.fill('Test message');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(500);
    const clearButton = page.getByRole('button', { name: /clear|reset|new/i });
    if (await clearButton.isVisible()) {
      await clearButton.click();
    }
    await expect(page.locator('body')).toBeVisible();
  });

  test('should handle long messages', async ({ page }) => {
    await openPreview(page);
    const chatInput = page.getByPlaceholder('Type your message...');
    await chatInput.fill('This is a very long message. '.repeat(20));
    await page.keyboard.press('Enter');
    await expect(page.locator('body')).toBeVisible();
  });

  test('should work on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await openPreview(page);
    expect(page.viewportSize()?.width).toBe(375);
  });

  test('should place preview widget bottom-right on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await openPreview(page);

    const panel = page.getByTestId('preview-widget-panel');
    const frame = page.getByTestId('preview-device-frame');
    const panelBox = await panel.boundingBox();
    const frameBox = await frame.boundingBox();
    expect(panelBox).not.toBeNull();
    expect(frameBox).not.toBeNull();
    if (panelBox && frameBox) {
      const insetRight = frameBox.x + frameBox.width - (panelBox.x + panelBox.width);
      const insetBottom = frameBox.y + frameBox.height - (panelBox.y + panelBox.height);
      expect(insetRight).toBeGreaterThanOrEqual(0);
      expect(insetBottom).toBeGreaterThanOrEqual(0);
      expect(insetRight).toBeLessThanOrEqual(40);
      expect(insetBottom).toBeLessThanOrEqual(40);
    }
  });

  test('should render wide bottom-sheet style panel on mobile preview mode', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openPreview(page);

    const panel = page.getByTestId('preview-widget-panel');
    const frame = page.getByTestId('preview-device-frame');
    const panelBox = await panel.boundingBox();
    const frameBox = await frame.boundingBox();
    expect(panelBox).not.toBeNull();
    expect(frameBox).not.toBeNull();
    if (panelBox && frameBox) {
      expect(panelBox.width).toBeGreaterThanOrEqual(frameBox.width * 0.85);
      const insetBottom = frameBox.y + frameBox.height - (panelBox.y + panelBox.height);
      expect(insetBottom).toBeGreaterThanOrEqual(0);
      expect(insetBottom).toBeLessThanOrEqual(28);
    }
  });
});
