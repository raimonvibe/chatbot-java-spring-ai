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

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';

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

export async function getEmbedCode(chatbotId: number): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/embed`, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to get embed code');
  }

  return response.text();
}
