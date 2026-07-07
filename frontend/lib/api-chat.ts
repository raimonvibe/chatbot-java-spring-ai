import { ApiError, sanitizeErrorMessage } from './api-errors';
import { getAuthHeaders, resolveApiBaseUrl } from './api-client';
import type { ChatResponse } from './api-types';

export async function sendMessage(
  chatbotId: number,
  message: string,
  sessionId?: string,
  language: string = 'en'
): Promise<ChatResponse> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chat/${chatbotId}`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({
      message,
      sessionId,
      language,
    }),
  });

  if (response.status === 401) {
    const err = new Error(
      'Your session expired. Please sign in again to use chat preview.'
    ) as ApiError;
    err.status = 401;
    throw err;
  }
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const raw = errorData?.error != null ? errorData.error : `HTTP ${response.status}: ${response.statusText}`;
    const errorMessage = sanitizeErrorMessage(raw) || 'Failed to send message. Please try again.';
    throw new Error(errorMessage);
  }

  return response.json();
}
