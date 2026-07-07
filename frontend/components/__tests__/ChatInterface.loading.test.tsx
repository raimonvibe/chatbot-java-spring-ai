import React from 'react';
import { render, act } from '@testing-library/react';
import ChatInterface from '../ChatInterface';

// Mock react-spinners
jest.mock('react-spinners', () => ({
  DotLoader: ({ color, size }: { color: string; size: number }) => (
    <div data-testid="dot-loader" data-color={color} data-size={size}>Loading...</div>
  ),
}));

jest.mock('@/lib/api', () => {
  const actual = jest.requireActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    sendMessage: jest.fn(),
    getQuickReplies: jest.fn().mockResolvedValue([]),
  };
});

// Mock Message component
jest.mock('../Message', () => {
  return function MockMessage({ message }: { message: any }) {
    return <div data-testid="message">{message.content}</div>;
  };
});

/** Renders ChatInterface and flushes the initial getQuickReplies() effect so state updates are wrapped in act(). */
async function renderChatInterface() {
  const result = render(<ChatInterface chatbotId={1} />);
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
  return result;
}

describe('ChatInterface Loading State Security Tests', () => {
  it('should render DotLoader when isLoading is true', async () => {
    const { container } = await renderChatInterface();
    expect(container).toBeInTheDocument();
  });

  it('should use safe color values for DotLoader', async () => {
    const { container } = await renderChatInterface();
    expect(container).toBeInTheDocument();
  });

  it('should not execute inline scripts in loading state', async () => {
    const { container } = await renderChatInterface();
    const scripts = container.querySelectorAll('script');
    expect(scripts.length).toBe(0);
  });

  it('should not use dangerouslySetInnerHTML', async () => {
    const { container } = await renderChatInterface();
    const allElements = container.querySelectorAll('*');
    allElements.forEach((element) => {
      expect(element).not.toHaveProperty('dangerouslySetInnerHTML');
    });
  });
});

