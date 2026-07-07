import { ApiError, isApiError, sanitizeErrorMessage } from './api-errors';
import { getAuthHeaders, resolveApiBaseUrl } from './api-client';
import type {
  AnalysisStatus,
  Chatbot,
  ChristianContentAnalysis,
  JesusTeachingsPreviewResponse,
} from './api-types';

export async function analyzeChristianContent(
  chatbotId: number,
  maxVerses: number = 20,
  similarityThreshold: number = 0.5
): Promise<ChristianContentAnalysis> {
  const headers = getAuthHeaders();
  const response = await fetch(
    `${resolveApiBaseUrl()}/api/chatbots/${chatbotId}/analyze-christian-content?maxVerses=${maxVerses}&similarityThreshold=${similarityThreshold}`,
    {
      method: 'POST',
      credentials: 'include',
      headers,
    }
  );

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const err = new Error(errorData.error || 'Failed to analyze Christian content') as Error & { code?: string };
    err.code = errorData.code;
    throw err;
  }

  return response.json();
}

export async function getChatbot(chatbotId: number): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}`, {
    credentials: 'include',
    headers,
    cache: 'no-store',
  });

  if (!response.ok) {
    const err = new Error('Failed to fetch chatbot') as ApiError;
    err.status = response.status;
    throw err;
  }

  return response.json();
}

export async function updateChatbot(chatbotId: number, updates: Partial<Chatbot>): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
    body: JSON.stringify(updates),
  });

  if (!response.ok) {
    if (response.status === 404) throw new Error('Chatbot not found');
    if (response.status === 403) throw new Error('You do not have permission to update this chatbot');
    throw new Error('Failed to update chatbot');
  }

  return response.json();
}

export async function deleteChatbot(chatbotId: number): Promise<void> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });
  if (response.status === 204) {
    return;
  }
  if (response.status === 401) {
    const err = new Error('Please sign in again to continue.') as ApiError;
    err.status = 401;
    throw err;
  }
  if (response.status === 404) {
    throw new Error('Chatbot not found');
  }
  if (response.status === 403) {
    throw new Error('You do not have permission to delete this chatbot');
  }
  const data = await response.json().catch(() => ({}));
  throw new Error((data as { error?: string }).error || 'Failed to delete chatbot');
}

export async function previewJesusTeachings(
  chatbotId: number,
  maxTeachings: number = 5
): Promise<JesusTeachingsPreviewResponse> {
  const headers = getAuthHeaders();
  const response = await fetch(
    `${resolveApiBaseUrl()}/api/chatbots/${chatbotId}/preview-jesus-teachings?maxTeachings=${maxTeachings}`,
    {
      method: 'POST',
      credentials: 'include',
      headers,
    }
  );

  if (!response.ok) {
    if (response.status === 404) throw new Error('Chatbot not found');
    if (response.status === 403) throw new Error('You do not have permission to preview teachings');
    throw new Error('Failed to preview Jesus teachings');
  }

  return response.json();
}

export async function getQuickReplies(chatbotId: number): Promise<string[]> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}/quick-replies`, {
      credentials: 'include',
      headers,
    });

    if (!response.ok) {
      return [];
    }

    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function getAllChatbots(): Promise<Chatbot[]> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots`, {
    credentials: 'include',
    headers,
    cache: 'no-store',
  });

  if (!response.ok) {
    const error = new Error(`Failed to fetch chatbots: ${response.status}`);
    (error as ApiError).status = response.status;
    throw error;
  }

  return response.json();
}

export async function createChatbotFromUrl(websiteUrl: string): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/onboarding`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ websiteUrl }),
  });

  if (!response.ok) {
    const errorData = (await response.json().catch(() => ({}))) as Record<string, unknown>;
    const errMsg =
      typeof errorData.error === 'string' && errorData.error.trim()
        ? sanitizeErrorMessage(errorData.error)
        : `HTTP ${response.status}: ${response.statusText}`;
    const error = new Error(errMsg || `HTTP ${response.status}`) as ApiError;
    error.status = response.status;
    if (typeof errorData.upgradeRequired === 'boolean') {
      error.upgradeRequired = errorData.upgradeRequired;
    }
    if (typeof errorData.estimatedPages === 'number' && typeof errorData.maxPages === 'number') {
      error.websiteTooLarge = true;
      error.estimatedPages = errorData.estimatedPages;
      error.maxPages = errorData.maxPages;
    }
    if (response.status === 402) {
      error.upgradeRequired = true;
    }
    throw error;
  }

  return response.json();
}

export async function createChatbot(data: {
  name: string;
  description: string;
  websiteUrl: string;
  primaryLanguage?: string;
}): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    if (response.status === 402) {
      const errorData = await response.json().catch(() => ({ error: 'Upgrade required' }));
      const error = new Error(errorData.error || 'Website too large for preview mode. Upgrade to continue.') as ApiError;
      error.status = 402;
      error.upgradeRequired = true;
      error.estimatedPages = errorData.estimatedPages;
      error.maxPages = errorData.maxPages;
      throw error;
    }

    const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
    const errorMessage = errorData.error || `HTTP ${response.status}: ${response.statusText}`;
    throw new Error(errorMessage);
  }

  return response.json();
}

const ANALYSIS_STATUS_FETCH_TIMEOUT_MS = 10000;

export async function getAnalysisStatus(chatbotId: number): Promise<AnalysisStatus> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), ANALYSIS_STATUS_FETCH_TIMEOUT_MS);
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}/analysis-status`, {
      credentials: 'include',
      headers,
      signal: controller.signal,
    });
    if (!response.ok) {
      const err = new Error('Failed to get analysis status') as ApiError;
      err.status = response.status;
      throw err;
    }
    const data = await response.json();
    return { ready: !!data.ready, pagesIndexed: data.pagesIndexed ?? 0 };
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function pollUntilAnalysisReady(
  chatbotId: number,
  options: { intervalMs?: number; timeoutMs?: number } = {}
): Promise<AnalysisStatus> {
  const { intervalMs = 1000, timeoutMs = 120000 } = options;
  const start = Date.now();
  let lastStatus: AnalysisStatus = { ready: false, pagesIndexed: 0 };
  while (Date.now() - start < timeoutMs) {
    try {
      const status = await getAnalysisStatus(chatbotId);
      lastStatus = status;
      if (status.ready) return status;
    } catch (e) {
      if (isApiError(e)) {
        const s = e.status;
        if (s === 401 || s === 403 || s === 404) {
          console.warn('Analysis status poll stopped:', s);
          return lastStatus;
        }
      }
      console.warn('Analysis status poll failed, retrying:', e);
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return lastStatus;
}

export async function analyzeWebsite(chatbotId: number, websiteUrl: string): Promise<unknown> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}/analyze`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ websiteUrl }),
  });

  if (!response.ok) {
    let errorData: { error?: unknown } = {};
    try {
      if (typeof response.json === 'function') {
        errorData = await response.json();
      }
    } catch {
      // Ignore parse errors; use fallback message
    }
    const message = sanitizeErrorMessage(errorData?.error) || 'Failed to analyze website';
    const err = new Error(message) as ApiError;
    if (response.status === 402) {
      err.status = 402;
      err.upgradeRequired = true;
    }
    throw err;
  }

  return response.json();
}

export async function getEmbedCode(chatbotId: number): Promise<string> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/chatbots/${chatbotId}/embed`, {
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    if (response.status === 401) {
      const err = new Error(
        'Please sign in again to copy your embed code. Refresh the page, sign in, and try once more.'
      ) as ApiError;
      err.status = 401;
      throw err;
    }
    if (response.status === 402) {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const errorData = await response.json().catch(() => ({ error: 'Upgrade required' }));
        throw new Error(errorData.error || 'Upgrade to paid tier for integration script access');
      }
      const errorText = await response.text().catch(() => 'Upgrade required');
      throw new Error(errorText || 'Upgrade to paid tier for integration script access');
    }
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      const errorData = await response.json().catch(() => ({ error: 'Failed to get embed code' }));
      throw new Error(errorData.error || 'Failed to get embed code');
    }
    const errorText = await response.text().catch(() => '');
    throw new Error(errorText || 'Failed to get embed code');
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    const data = await response.json();
    return data.embedCode || data;
  }
  return await response.text();
}
