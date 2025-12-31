import { render, screen } from '@testing-library/react'
import Message from '../Message'
import type { Message as MessageType } from '@/lib/api'

describe('Message Component', () => {
  const mockUserMessage: MessageType = {
    id: '1',
    role: 'user',
    content: 'Hello, how are you?',
    timestamp: new Date('2024-01-01T12:00:00').getTime(),
  }

  const mockAssistantMessage: MessageType = {
    id: '2',
    role: 'assistant',
    content: "I'm doing well, thank you for asking!",
    timestamp: new Date('2024-01-01T12:00:30').getTime(),
  }

  describe('Rendering', () => {
    it('should render a user message correctly', () => {
      render(<Message message={mockUserMessage} index={0} />)

      expect(screen.getByText('Hello, how are you?')).toBeInTheDocument()
    })

    it('should render an assistant message correctly', () => {
      render(<Message message={mockAssistantMessage} index={0} />)

      expect(screen.getByText("I'm doing well, thank you for asking!")).toBeInTheDocument()
    })

    it('should display the timestamp in correct format', () => {
      render(<Message message={mockUserMessage} index={0} />)

      const timeElement = screen.getByText(/12:00/)
      expect(timeElement).toBeInTheDocument()
      expect(timeElement.tagName).toBe('TIME')
    })

    it('should handle multiline content correctly', () => {
      const multilineMessage: MessageType = {
        id: '3',
        role: 'assistant',
        content: 'Line 1\nLine 2\nLine 3',
        timestamp: Date.now(),
      }

      render(<Message message={multilineMessage} index={0} />)

      expect(screen.getByText(/Line 1/)).toBeInTheDocument()
    })
  })

  describe('Styling and Layout', () => {
    it('should apply correct classes for user messages', () => {
      const { container } = render(<Message message={mockUserMessage} index={0} />)

      // User messages should be on the right
      const messageContainer = container.querySelector('.justify-end')
      expect(messageContainer).toBeInTheDocument()
    })

    it('should apply correct classes for assistant messages', () => {
      const { container } = render(<Message message={mockAssistantMessage} index={0} />)

      // Assistant messages should be on the left
      const messageContainer = container.querySelector('.justify-start')
      expect(messageContainer).toBeInTheDocument()
    })

    it('should render user avatar icon', () => {
      const { container } = render(<Message message={mockUserMessage} index={0} />)

      // Check for avatar container with gradient background (user)
      const avatar = container.querySelector('.from-brown-600.to-gold-600')
      expect(avatar).toBeInTheDocument()
    })

    it('should render assistant avatar icon', () => {
      const { container } = render(<Message message={mockAssistantMessage} index={0} />)

      // Check for avatar container with solid background (assistant)
      const avatar = container.querySelector('.bg-brown-700')
      expect(avatar).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have proper semantic HTML structure', () => {
      const { container } = render(<Message message={mockUserMessage} index={0} />)

      const timeElement = container.querySelector('time')
      expect(timeElement).toBeInTheDocument()
    })

    it('should handle long content without breaking layout', () => {
      const longMessage: MessageType = {
        id: '4',
        role: 'user',
        content: 'A'.repeat(500),
        timestamp: Date.now(),
      }

      const { container } = render(<Message message={longMessage} index={0} />)

      const messageContent = container.querySelector('.whitespace-pre-wrap.break-words')
      expect(messageContent).toBeInTheDocument()
    })

    it('should handle special characters in content', () => {
      const specialCharMessage: MessageType = {
        id: '5',
        role: 'assistant',
        content: '<script>alert("XSS")</script> & other special chars: " \' <  >',
        timestamp: Date.now(),
      }

      render(<Message message={specialCharMessage} index={0} />)

      // Content should be rendered as text, not executed
      expect(screen.getByText(/script.*alert/)).toBeInTheDocument()
    })
  })

  describe('Animation and Performance', () => {
    it('should render with animation delay based on index', () => {
      const { rerender } = render(<Message message={mockUserMessage} index={0} />)
      expect(screen.getByText(mockUserMessage.content)).toBeInTheDocument()

      rerender(<Message message={mockUserMessage} index={5} />)
      expect(screen.getByText(mockUserMessage.content)).toBeInTheDocument()
    })
  })

  describe('Edge Cases', () => {
    it('should handle empty content gracefully', () => {
      const emptyMessage: MessageType = {
        id: '6',
        role: 'user',
        content: '',
        timestamp: Date.now(),
      }

      const { container } = render(<Message message={emptyMessage} index={0} />)

      expect(container.querySelector('p')).toBeInTheDocument()
    })

    it('should handle very old timestamps', () => {
      const oldMessage: MessageType = {
        id: '7',
        role: 'assistant',
        content: 'Old message',
        timestamp: new Date('2000-01-01').getTime(),
      }

      render(<Message message={oldMessage} index={0} />)

      // Just check that time element is rendered, don't check specific value due to timezone differences
      const timeElement = screen.getByText(/\d{2}:\d{2}/)
      expect(timeElement).toBeInTheDocument()
    })

    it('should handle invalid role gracefully', () => {
      const invalidMessage = {
        id: '8',
        role: 'invalid' as any,
        content: 'Test message',
        timestamp: Date.now(),
      }

      // Should not crash
      const { container } = render(<Message message={invalidMessage} index={0} />)
      expect(container).toBeInTheDocument()
    })
  })

  describe('Content Formatting', () => {
    it('should preserve whitespace in content', () => {
      const whitespaceMessage: MessageType = {
        id: '9',
        role: 'assistant',
        content: 'Line 1  \n  Line 2\n\nLine 3',
        timestamp: Date.now(),
      }

      const { container } = render(<Message message={whitespaceMessage} index={0} />)

      const messageContent = container.querySelector('.whitespace-pre-wrap')
      expect(messageContent).toBeInTheDocument()
      expect(messageContent?.textContent).toContain('Line 1')
      expect(messageContent?.textContent).toContain('Line 2')
      expect(messageContent?.textContent).toContain('Line 3')
    })

    it('should handle URLs in content as text', () => {
      const urlMessage: MessageType = {
        id: '10',
        role: 'assistant',
        content: 'Check out https://example.com for more info',
        timestamp: Date.now(),
      }

      render(<Message message={urlMessage} index={0} />)

      expect(screen.getByText(/https:\/\/example.com/)).toBeInTheDocument()
    })
  })
})
