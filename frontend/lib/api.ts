export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

export interface ChatResponse {
  message: string;
  sessionId: string;
  timestamp: number;
  chatbotId: number;
}

export interface Chatbot {
  id: number;
  name: string;
  description: string;
  primaryLanguage: string;
  supportedLanguages: string[];
  brandingConfig: string;
}

export interface VerseMatch {
  id: number;
  reference: string;
  book: string;
  chapter: number;
  verse: number;
  text: string;
  translation: string;
  similarity: number;
  similarityPercentage: number;
}

export interface ChristianContentAnalysis {
  chatbotId: number;
  websiteUrl: string;
  themes?: string[];
  relevantVerses: VerseMatch[];
  averageSimilarity: number;
  totalVersesAnalyzed: number;
  versesAboveThreshold: number;
}

// Auto-detect backend URL based on environment
function getApiBaseUrl(): string {
  // Use explicit environment variable if set
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  
  // Auto-detect production domain
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    // Production domains - use Render backend
    if (hostname === 'prayer-chat.com' || hostname === 'www.prayer-chat.com') {
      return 'https://chatbot-backend-4mp4.onrender.com';
    }
    // Vercel preview/test deployments
    if (hostname.includes('vercel.app')) {
      return 'https://chatbot-backend-4mp4.onrender.com';
    }
  }
  
  // Default to localhost for local development
  return 'http://localhost:8081';
}

const API_BASE_URL = getApiBaseUrl();

// Analyze Christian content for a chatbot
export async function analyzeChristianContent(
  chatbotId: number,
  maxVerses: number = 20,
  similarityThreshold: number = 0.5
): Promise<ChristianContentAnalysis> {
  const response = await fetch(
    `${API_BASE_URL}/api/chatbots/${chatbotId}/analyze-christian-content?maxVerses=${maxVerses}&similarityThreshold=${similarityThreshold}`,
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || 'Failed to analyze Christian content');
  }

  return response.json();
}

// Check if user is authenticated
export async function checkAuth(): Promise<{ authenticated: boolean; user?: any }> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      method: 'GET',
      credentials: 'include',
    });

    if (response.ok) {
      const user = await response.json();
      return { authenticated: true, user };
    }
    return { authenticated: false };
  } catch (error) {
    return { authenticated: false };
  }
}

export async function sendMessage(
  chatbotId: number,
  message: string,
  sessionId?: string,
  language: string = 'en'
): Promise<ChatResponse> {
  const response = await fetch(`${API_BASE_URL}/api/chat/${chatbotId}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      message,
      sessionId,
      language,
    }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
    const errorMessage = errorData.error || `HTTP ${response.status}: ${response.statusText}`;
    throw new Error(errorMessage);
  }

  return response.json();
}

export async function getChatbot(chatbotId: number): Promise<Chatbot> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to fetch chatbot');
  }

  return response.json();
}

export async function getQuickReplies(chatbotId: number): Promise<string[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/quick-replies`, {
      credentials: 'include',
    });

    if (!response.ok) {
      return [];
    }

    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    // Return empty array on network errors
    return [];
  }
}

export async function getAllChatbots(): Promise<Chatbot[]> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots`, {
    credentials: 'include',
  });

  if (!response.ok) {
    const error = new Error(`Failed to fetch chatbots: ${response.status}`);
    (error as any).status = response.status;
    throw error;
  }

  return response.json();
}

/**
 * Simplified onboarding - create chatbot from website URL only
 * Auto-generates name and pre-configures Christian values
 */
export async function createChatbotFromUrl(websiteUrl: string): Promise<Chatbot> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/onboarding`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ websiteUrl }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Failed to create chatbot' }));
    throw new Error(error.error || 'Failed to create chatbot');
  }

  return response.json();
}

export async function createChatbot(data: {
  name: string;
  description: string;
  websiteUrl: string;
  primaryLanguage?: string;
}): Promise<Chatbot> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error('Failed to create chatbot');
  }

  return response.json();
}

export async function analyzeWebsite(chatbotId: number, websiteUrl: string): Promise<any> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/analyze`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ websiteUrl }),
  });

  if (!response.ok) {
    throw new Error('Failed to analyze website');
  }

  return response.json();
}

export interface SubscriptionStatus {
  isPreviewMode: boolean;
  canAccessIntegrationScript: boolean;
  maxChatbots: number;
  currentChatbotCount: number;
  plan?: string;
}

export async function getSubscriptionStatus(): Promise<SubscriptionStatus> {
  try {
    // Try to get user info which includes subscription status
    const authResult = await checkAuth();
    if (!authResult.authenticated || !authResult.user) {
      return {
        isPreviewMode: true,
        canAccessIntegrationScript: false,
        maxChatbots: 1,
        currentChatbotCount: 0,
      };
    }
    
    // Check if user has subscription by trying to access a paid feature
    // If embed endpoint returns 402, user is in preview mode
    // This is a simple heuristic - in production, you'd have a dedicated endpoint
    return {
      isPreviewMode: true, // Default to preview mode
      canAccessIntegrationScript: false,
      maxChatbots: 1,
      currentChatbotCount: 0,
    };
  } catch (error) {
    return {
      isPreviewMode: true,
      canAccessIntegrationScript: false,
      maxChatbots: 1,
      currentChatbotCount: 0,
    };
  }
}

export async function getEmbedCode(chatbotId: number): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/embed`, {
    credentials: 'include',
  });

  if (!response.ok) {
    if (response.status === 402) {
      // Payment required - user is in preview mode
      // Try to parse as JSON first, fallback to text
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const errorData = await response.json().catch(() => ({ error: 'Upgrade required' }));
        throw new Error(errorData.error || 'Upgrade to paid tier for integration script access');
      } else {
        const errorText = await response.text().catch(() => 'Upgrade required');
        throw new Error(errorText || 'Upgrade to paid tier for integration script access');
      }
    }
    // For other errors, try JSON first, then text
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      const errorData = await response.json().catch(() => ({ error: 'Failed to get embed code' }));
      throw new Error(errorData.error || 'Failed to get embed code');
    } else {
      const errorText = await response.text().catch(() => '');
      // Use error text if available, otherwise generic message
      throw new Error(errorText || 'Failed to get embed code');
    }
  }

  // Check content type to determine if response is JSON or plain text
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    const data = await response.json();
    return data.embedCode || data;
  } else {
    // Backend returns plain text (HTML/JS embed code)
    return await response.text();
  }
}

export async function deleteChatbot(chatbotId: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Chatbot not found');
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to delete this chatbot');
    }
    throw new Error('Failed to delete chatbot');
  }
}

export async function deleteAllChatbots(): Promise<{ message: string; deletedCount: number }> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to delete all chatbots');
  }

  return response.json();
}

export async function logout(): Promise<{ message: string; googleLogoutUrl?: string }> {
  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to logout');
  }

  const result = await response.json();
  
  // Clear all cookies on frontend
  document.cookie.split(";").forEach((c) => {
    const eqPos = c.indexOf("=");
    const name = eqPos > -1 ? c.substr(0, eqPos).trim() : c.trim();
    // Delete cookie for current domain and all paths
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`;
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=${window.location.hostname}`;
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=.${window.location.hostname}`;
  });
  
  // Clear localStorage and sessionStorage
  localStorage.clear();
  sessionStorage.clear();
  
  return result;
}
