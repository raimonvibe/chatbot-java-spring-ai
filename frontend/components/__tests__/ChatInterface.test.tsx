import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ChatInterface from '../ChatInterface'
import * as api from '@/lib/api'

// Mock the API module
jest.mock('@/lib/api', () => ({
  sendMessage: jest.fn(),
  getQuickReplies: jest.fn(),
}))

const mockSendMessage = api.sendMessage as jest.MockedFunction<typeof api.sendMessage>
const mockGetQuickReplies = api.getQuickReplies as jest.MockedFunction<typeof api.getQuickReplies>

/** Renders ChatInterface and flushes the initial getQuickReplies() effect so state updates are wrapped in act(). */
async function renderChatInterface() {
  const result = render(<ChatInterface />)
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
  return result
}

describe('ChatInterface Component', () => {
  // Suppress console.error during tests (we test error handling, so errors are expected)
  const originalError = console.error

  beforeAll(() => {
    console.error = jest.fn()
  })

  afterAll(() => {
    console.error = originalError
  })

  beforeEach(() => {
    jest.clearAllMocks()
    mockGetQuickReplies.mockResolvedValue([])
  })

  describe('Initial Rendering', () => {
    it('should render the chat interface with initial message', async () => {
      await renderChatInterface()

      expect(screen.getByText('Prayer-Chat Assistant')).toBeInTheDocument()
      expect(screen.getByText('Hello! How can I help you today?')).toBeInTheDocument()
      expect(screen.getByPlaceholderText('Type your message...')).toBeInTheDocument()
    })

    it('should load quick replies on mount', async () => {
      const mockQuickReplies = ['Hello', 'Help', 'About']
      mockGetQuickReplies.mockResolvedValue(mockQuickReplies)

      await renderChatInterface()

      await waitFor(() => {
        expect(mockGetQuickReplies).toHaveBeenCalledWith(1)
      })
    })

    it('should display quick reply buttons when available', async () => {
      const mockQuickReplies = ['Hello', 'Help', 'About']
      mockGetQuickReplies.mockResolvedValue(mockQuickReplies)

      await renderChatInterface()

      await waitFor(() => {
        expect(screen.getByText('Hello')).toBeInTheDocument()
        expect(screen.getByText('Help')).toBeInTheDocument()
        expect(screen.getByText('About')).toBeInTheDocument()
      })
    })

    it('should not display quick replies section when empty', async () => {
      mockGetQuickReplies.mockResolvedValue([])

      const { container } = await renderChatInterface()

      await waitFor(() => {
        const quickRepliesContainer = container.querySelector('.flex-wrap')
        expect(quickRepliesContainer).not.toBeInTheDocument()
      })
    })
  })

  describe('Sending Messages', () => {
    it('should send a message when send button is clicked', async () => {
      const user = userEvent.setup()
      const mockResponse = {
        message: 'Response from bot',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')
      const sendButton = screen.getByRole('button', { name: /send/i })

      await user.type(input, 'Hello bot')
      await user.click(sendButton)

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalledWith(1, 'Hello bot', '')
        expect(screen.getByText('Response from bot')).toBeInTheDocument()
      })
    })

    it('should send a message when Enter key is pressed', async () => {
      const user = userEvent.setup()
      const mockResponse = {
        message: 'Response from bot',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      await user.type(input, 'Hello bot{Enter}')

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalledWith(1, 'Hello bot', '')
      })
    })

    it('should not send a message with Shift+Enter', async () => {
      const user = userEvent.setup()

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      await user.type(input, 'Line 1{Shift>}{Enter}{/Shift}Line 2')

      expect(mockSendMessage).not.toHaveBeenCalled()
    })

    it('should not send empty messages', async () => {
      const user = userEvent.setup()

      await renderChatInterface()

      const sendButton = screen.getByRole('button', { name: /send/i })

      // Send button should be disabled when input is empty
      expect(sendButton).toBeDisabled()

      await user.click(sendButton)

      expect(mockSendMessage).not.toHaveBeenCalled()
    })

    it('should trim whitespace from messages', async () => {
      const user = userEvent.setup()
      const mockResponse = {
        message: 'Response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')
      const sendButton = screen.getByRole('button', { name: /send/i })

      await user.type(input, '   Hello   ')
      await user.click(sendButton)

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalledWith(1, 'Hello', '')
      })
    })

    it('should clear input after sending message', async () => {
      const user = userEvent.setup()
      const mockResponse = {
        message: 'Response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      render(<ChatInterface />)

      const input = screen.getByPlaceholderText('Type your message...') as HTMLInputElement

      await user.type(input, 'Hello')
      await user.click(screen.getByRole('button', { name: /send/i }))

      await waitFor(() => {
        expect(input.value).toBe('')
      })
    })
  })

  describe('Session Management', () => {
    it('should store session ID after first message', async () => {
      const user = userEvent.setup()
      const mockResponse1 = {
        message: 'First response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      const mockResponse2 = {
        message: 'Second response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage
        .mockResolvedValueOnce(mockResponse1)
        .mockResolvedValueOnce(mockResponse2)

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      // First message
      await user.type(input, 'Hello{Enter}')

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalledWith(1, 'Hello', '')
      })

      // Second message should include session ID
      await user.type(input, 'Second message{Enter}')

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalledWith(1, 'Second message', 'session_123')
      })
    })
  })

  describe('Loading State', () => {
    it('should show loading indicator while waiting for response', async () => {
      const user = userEvent.setup()
      let resolveMessage: (value: any) => void
      const messagePromise = new Promise((resolve) => {
        resolveMessage = resolve
      })
      mockSendMessage.mockReturnValue(messagePromise as any)

      render(<ChatInterface />)

      const input = screen.getByPlaceholderText('Type your message...')

      await user.type(input, 'Hello{Enter}')

      // Loading indicator should appear
      await waitFor(() => {
        const loadingIndicator = screen.getByRole('button', { name: /send/i })
        expect(loadingIndicator).toBeDisabled()
      })

      // Resolve the promise
      resolveMessage!({
        message: 'Response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      })

      // Loading should complete - verify by typing new text and checking button state
      await waitFor(() => {
        expect(screen.getByText('Response')).toBeInTheDocument()
      })

      // After loading completes, user should be able to send another message
      await user.type(input, 'New message')
      const sendButton = screen.getByRole('button', { name: /send/i })
      expect(sendButton).not.toBeDisabled()
    })

    it('should disable input and buttons while loading', async () => {
      const user = userEvent.setup()
      let resolveMessage: (value: any) => void
      const messagePromise = new Promise((resolve) => {
        resolveMessage = resolve
      })
      mockSendMessage.mockReturnValue(messagePromise as any)

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...') as HTMLInputElement
      const sendButton = screen.getByRole('button', { name: /send/i })

      await user.type(input, 'Hello{Enter}')

      await waitFor(() => {
        expect(input).toBeDisabled()
        expect(sendButton).toBeDisabled()
      })

      resolveMessage!({
        message: 'Response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      })

      await waitFor(() => {
        expect(input).not.toBeDisabled()
      })
    })
  })

  describe('Error Handling', () => {
    it('should display error message when API call fails', async () => {
      const user = userEvent.setup()
      mockSendMessage.mockRejectedValue(new Error('Network error'))

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      await user.type(input, 'Hello{Enter}')

      await waitFor(() => {
        expect(screen.getByText(/Sorry, I encountered an error/)).toBeInTheDocument()
      }, { timeout: 3000 })
    })

    it('should recover from error and allow new messages', async () => {
      const user = userEvent.setup()
      mockSendMessage
        .mockRejectedValueOnce(new Error('Network error'))
        .mockResolvedValueOnce({
          message: 'Success',
          sessionId: 'session_123',
          timestamp: Date.now(),
        chatbotId: 1,
      })

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      // First message fails
      await user.type(input, 'Hello{Enter}')

      await waitFor(() => {
        expect(screen.getByText(/Sorry, I encountered an error/)).toBeInTheDocument()
      }, { timeout: 3000 })

      // Second message succeeds
      await user.type(input, 'Retry{Enter}')

      await waitFor(() => {
        expect(screen.getByText('Success')).toBeInTheDocument()
      })
    })
  })

  describe('Quick Replies', () => {
    it('should send message when quick reply button is clicked', async () => {
      const user = userEvent.setup()
      const mockQuickReplies = ['Hello', 'Help']
      mockGetQuickReplies.mockResolvedValue(mockQuickReplies)

      const mockResponse = {
        message: 'Response to quick reply',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      await renderChatInterface()

      await waitFor(() => {
        expect(screen.getByText('Hello')).toBeInTheDocument()
      })

      const quickReplyButton = screen.getByText('Hello')
      await user.click(quickReplyButton)

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalledWith(1, 'Hello', '')
        expect(screen.getByText('Response to quick reply')).toBeInTheDocument()
      })
    })

    it('should disable quick reply buttons while loading', async () => {
      const user = userEvent.setup()
      const mockQuickReplies = ['Hello']
      mockGetQuickReplies.mockResolvedValue(mockQuickReplies)

      let resolveMessage: (value: any) => void
      const messagePromise = new Promise((resolve) => {
        resolveMessage = resolve
      })
      mockSendMessage.mockReturnValue(messagePromise as any)

      await renderChatInterface()

      await waitFor(() => {
        expect(screen.getByText('Hello')).toBeInTheDocument()
      })

      const quickReplyButton = screen.getByText('Hello')
      await user.click(quickReplyButton)

      await waitFor(() => {
        expect(quickReplyButton).toBeDisabled()
      })

      resolveMessage!({
        message: 'Response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      })

      await waitFor(() => {
        expect(quickReplyButton).not.toBeDisabled()
      })
    })
  })

  describe('Message Display', () => {
    it('should display user and assistant messages in conversation', async () => {
      const user = userEvent.setup()
      const mockResponse = {
        message: 'Bot response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      await user.type(input, 'User message{Enter}')

      await waitFor(() => {
        expect(screen.getByText('User message')).toBeInTheDocument()
        expect(screen.getByText('Bot response')).toBeInTheDocument()
      })
    })

    it('should maintain message history across multiple exchanges', async () => {
      const user = userEvent.setup()
      mockSendMessage
        .mockResolvedValueOnce({
          message: 'Response 1',
          sessionId: 'session_123',
          timestamp: Date.now(),
          chatbotId: 1,
        })
        .mockResolvedValueOnce({
          message: 'Response 2',
          sessionId: 'session_123',
          timestamp: Date.now() + 1000,
          chatbotId: 1,
        })

      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')

      await user.type(input, 'Message 1{Enter}')
      await waitFor(() => {
        expect(screen.getByText('Response 1')).toBeInTheDocument()
      })

      await user.type(input, 'Message 2{Enter}')
      await waitFor(() => {
        expect(screen.getByText('Response 2')).toBeInTheDocument()
      })

      // Both exchanges should be visible
      expect(screen.getByText('Message 1')).toBeInTheDocument()
      expect(screen.getByText('Response 1')).toBeInTheDocument()
      expect(screen.getByText('Message 2')).toBeInTheDocument()
      expect(screen.getByText('Response 2')).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have proper ARIA labels and roles', async () => {
      await renderChatInterface()

      const input = screen.getByPlaceholderText('Type your message...')
      expect(input).toHaveAttribute('type', 'text')

      const sendButton = screen.getByRole('button', { name: /send/i })
      expect(sendButton).toBeInTheDocument()
    })

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup()
      const mockResponse = {
        message: 'Response',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }
      mockSendMessage.mockResolvedValue(mockResponse)

      await renderChatInterface()

      // Tab to input field
      await user.tab()
      const input = screen.getByPlaceholderText('Type your message...')
      expect(input).toHaveFocus()

      // Type and send with Enter
      await user.keyboard('Test message{Enter}')

      await waitFor(() => {
        expect(mockSendMessage).toHaveBeenCalled()
      })
    })
  })
})
