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
  chatbotId: number;
  name: string;
  description: string;
  primaryLanguage: string;
  supportedLanguages: string[];
  brandingConfig: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export async function sendMessage(
  chatbotId: number,
  message: string,
  sessionId?: string,
  language: string = 'en'
): Promise<ChatResponse> {
  const response = await fetch(`${API_BASE_URL}/api/chat/${chatbotId}`, {
    method: 'POST',
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
    throw new Error('Failed to send message');
  }

  return response.json();
}

export async function getChatbot(chatbotId: number): Promise<Chatbot> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`);

  if (!response.ok) {
    throw new Error('Failed to fetch chatbot');
  }

  return response.json();
}

export async function getQuickReplies(chatbotId: number): Promise<string[]> {
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/quick-replies`);

  if (!response.ok) {
    return [];
  }

  const data = await response.json();
  return Array.isArray(data) ? data : [];
}
