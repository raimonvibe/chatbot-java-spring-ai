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
      const errorData = await response.json().catch(() => ({ error: 'Upgrade required' }));
      throw new Error(errorData.error || 'Upgrade to paid tier for integration script access');
    }
    const errorData = await response.json().catch(() => ({ error: 'Failed to get embed code' }));
    throw new Error(errorData.error || 'Failed to get embed code');
  }

  const data = await response.json();
  return data.embedCode || data;
}
