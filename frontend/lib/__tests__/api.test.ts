import {
  sendMessage,
  getChatbot,
  getQuickReplies,
  getAllChatbots,
  createChatbot,
  analyzeWebsite,
  getEmbedCode,
  deleteModalWebsiteScanNote,
  websiteScanFieldsFromSubscriptionApi,
  type ChatResponse,
  type Chatbot,
  type SubscriptionStatus,
} from '../api'

// Mock fetch globally
const mockFetch = global.fetch as jest.MockedFunction<typeof fetch>

describe('API Module', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  describe('sendMessage', () => {
    it('should send a message and return a chat response', async () => {
      const mockResponse: ChatResponse = {
        message: 'Hello! How can I help you?',
        sessionId: 'session_123',
        timestamp: Date.now(),
        chatbotId: 1,
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response)

      const result = await sendMessage(1, 'Hello', 'session_123', 'en')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/chat/1'),
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: 'Hello',
            sessionId: 'session_123',
            language: 'en',
          }),
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('should use default language if not provided', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          message: 'Response',
          sessionId: 'session_123',
          timestamp: Date.now(),
          chatbotId: 1,
        }),
      } as Response)

      await sendMessage(1, 'Hello')

      const callBody = JSON.parse(mockFetch.mock.calls[0][1]?.body as string)
      expect(callBody.language).toBe('en')
    })

    it('should throw an error when the request fails', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => ({ error: 'Failed to send message' }),
      } as Response)

      await expect(sendMessage(1, 'Hello')).rejects.toThrow('Failed to send message')
    })

    it('should handle network errors', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Network error'))

      await expect(sendMessage(1, 'Hello')).rejects.toThrow('Network error')
    })
  })

  describe('getChatbot', () => {
    it('should fetch a chatbot by ID', async () => {
      const mockChatbot: Chatbot = {
        id: 1,
        name: 'Test Bot',
        description: 'A test chatbot',
        primaryLanguage: 'en',
        supportedLanguages: ['en', 'es'],
        brandingConfig: '{}',
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockChatbot,
      } as Response)

      const result = await getChatbot(1)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/chatbots\/1/),
        expect.any(Object)
      )
      expect(result).toEqual(mockChatbot)
    })

    it('should throw an error when chatbot is not found', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(getChatbot(999)).rejects.toThrow('Failed to fetch chatbot')
    })
  })

  describe('getQuickReplies', () => {
    it('should fetch quick replies for a chatbot', async () => {
      const mockReplies = ['Hello', 'Help', 'About']

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockReplies,
      } as Response)

      const result = await getQuickReplies(1)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/chatbots\/1\/quick-replies/),
        expect.any(Object)
      )
      expect(result).toEqual(mockReplies)
    })

    it('should return empty array if request fails', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      const result = await getQuickReplies(1)

      expect(result).toEqual([])
    })

    it('should return empty array if response is not an array', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ data: 'not an array' }),
      } as Response)

      const result = await getQuickReplies(1)

      expect(result).toEqual([])
    })

    it('should handle network errors gracefully', async () => {
      mockFetch.mockImplementationOnce(() => Promise.reject(new Error('Network error')))

      const result = await getQuickReplies(1)

      expect(result).toEqual([])
    })
  })

  describe('getAllChatbots', () => {
    it('should fetch all chatbots', async () => {
      const mockChatbots: Chatbot[] = [
        {
          id: 1,
          name: 'Bot 1',
          description: 'First bot',
          primaryLanguage: 'en',
          supportedLanguages: ['en'],
          brandingConfig: '{}',
        },
        {
          id: 2,
          name: 'Bot 2',
          description: 'Second bot',
          primaryLanguage: 'es',
          supportedLanguages: ['es'],
          brandingConfig: '{}',
        },
      ]

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockChatbots,
      } as Response)

      const result = await getAllChatbots()

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/chatbots$/),
        expect.any(Object)
      )
      expect(result).toEqual(mockChatbots)
    })

    it('should throw an error when request fails', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
      } as Response)

      await expect(getAllChatbots()).rejects.toThrow('Failed to fetch chatbots')
    })
  })

  describe('createChatbot', () => {
    it('should create a new chatbot', async () => {
      const newChatbotData = {
        name: 'New Bot',
        description: 'A new test bot',
        websiteUrl: 'https://example.com',
        primaryLanguage: 'en',
      }

      const mockCreatedChatbot: Chatbot = {
        id: 3,
        name: newChatbotData.name,
        description: newChatbotData.description,
        primaryLanguage: 'en',
        supportedLanguages: ['en'],
        brandingConfig: '{}',
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockCreatedChatbot,
      } as Response)

      const result = await createChatbot(newChatbotData)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/chatbots'),
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(newChatbotData),
        })
      )
      expect(result).toEqual(mockCreatedChatbot)
    })

    it('should throw an error when creation fails', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        json: async () => ({ error: 'Failed to create chatbot' }),
      } as Response)

      await expect(
        createChatbot({
          name: 'Bot',
          description: 'Desc',
          websiteUrl: 'https://example.com',
        })
      ).rejects.toThrow('Failed to create chatbot')
    })
  })

  describe('analyzeWebsite', () => {
    it('should analyze a website for a chatbot', async () => {
      const mockAnalysisResult = {
        success: true,
        data: { pages: 5, keywords: ['test', 'example'] },
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockAnalysisResult,
      } as Response)

      const result = await analyzeWebsite(1, 'https://example.com')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/chatbots/1/analyze'),
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ websiteUrl: 'https://example.com' }),
        })
      )
      expect(result).toEqual(mockAnalysisResult)
    })

    it('should throw an error when analysis fails', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
      } as Response)

      await expect(
        analyzeWebsite(1, 'https://example.com')
      ).rejects.toThrow('Failed to analyze website')
    })
  })

  describe('getEmbedCode', () => {
    it('should fetch embed code for a chatbot (plain text)', async () => {
      const mockEmbedCode = '<script src="https://example.com/embed.js"></script>'

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: {
          get: jest.fn((header: string) => {
            if (header === 'content-type') return 'text/html';
            return null;
          }),
        },
        json: async () => ({}), // Always provide json() method
        text: async () => mockEmbedCode,
      } as unknown as Response)

      const result = await getEmbedCode(1)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/chatbots\/1\/embed/),
        expect.any(Object)
      )
      expect(result).toBe(mockEmbedCode)
    })

    it('should fetch embed code for a chatbot (JSON)', async () => {
      const mockEmbedCode = '<script src="https://example.com/embed.js"></script>'
      const mockJsonData = { embedCode: mockEmbedCode }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: {
          get: jest.fn((header: string) => {
            if (header === 'content-type') return 'application/json';
            return null;
          }),
        },
        json: async () => mockJsonData,
        text: async () => '', // Always provide text() method
      } as unknown as Response)

      const result = await getEmbedCode(1)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/chatbots\/1\/embed/),
        expect.any(Object)
      )
      expect(result).toBe(mockEmbedCode)
    })

    it('should throw an error when request fails (404)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        headers: {
          get: jest.fn((header: string) => {
            if (header === 'content-type') return 'text/plain';
            return null;
          }),
        },
        json: async () => ({ error: 'Not found' }), // Always provide json() method
        text: async () => 'Not found',
      } as unknown as Response)

      // When text response, it uses the text directly
      await expect(getEmbedCode(1)).rejects.toThrow('Not found')
    })

    it('should throw a session message when unauthorized (401)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: {
          get: jest.fn(() => null),
        },
        json: async () => ({}),
        text: async () => '',
      } as unknown as Response)

      await expect(getEmbedCode(1)).rejects.toThrow('Please sign in again')
    })

    it('should throw an error when payment required (402)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 402,
        headers: {
          get: jest.fn((header: string) => {
            if (header === 'content-type') return 'application/json';
            return null;
          }),
        },
        json: async () => ({ error: 'Upgrade to paid tier for integration script access' }),
        text: async () => 'Upgrade to paid tier for integration script access',
      } as unknown as Response)

      await expect(getEmbedCode(1)).rejects.toThrow('Upgrade to paid tier')
    })
  })

  describe('deleteModalWebsiteScanNote', () => {
    const baseStatus = {
      isPreviewMode: false,
      canAccessIntegrationScript: true,
      maxChatbots: 1,
      currentChatbotCount: 1,
    } satisfies SubscriptionStatus;

    it('mentions plural scans when remaining > 1', () => {
      const text = deleteModalWebsiteScanNote({ ...baseStatus, websiteScansRemaining: 4 });
      expect(text).toContain('4 website scans left');
      expect(text).toContain('does not reset');
    });

    it('uses singular when remaining is 1', () => {
      const text = deleteModalWebsiteScanNote({ ...baseStatus, websiteScansRemaining: 1 });
      expect(text).toContain('1 website scan left');
    });

    it('handles zero remaining', () => {
      const text = deleteModalWebsiteScanNote({ ...baseStatus, websiteScansRemaining: 0 });
      expect(text).toContain('no website scans left');
    });

    it('falls back when remaining is missing', () => {
      const text = deleteModalWebsiteScanNote({ ...baseStatus });
      expect(text).toContain('Scan limits stay tied');
    });
  })

  describe('websiteScanFieldsFromSubscriptionApi', () => {
    it('maps numeric fields from API', () => {
      const out = websiteScanFieldsFromSubscriptionApi({
        hasSubscription: true,
        status: 'ACTIVE',
        plan: 'BASIC',
        isActive: true,
        canUseChatbot: true,
        websiteScansRemaining: 2,
        websiteScansMonthlyQuota: 5,
        websiteScansUsedThisMonth: 3,
        websiteScansDailyLimit: 3,
        websiteScansUsedRollingDay: 1,
      });
      expect(out).toEqual({
        websiteScansRemaining: 2,
        websiteScansMonthlyQuota: 5,
        websiteScansUsedThisMonth: 3,
        websiteScansDailyLimit: 3,
        websiteScansUsedRollingDay: 1,
      });
    });

    it('returns empty when remaining is absent', () => {
      expect(websiteScanFieldsFromSubscriptionApi({ hasSubscription: true, status: 'ACTIVE', plan: 'FREE', isActive: true, canUseChatbot: false })).toEqual({});
    });
  })
})
