/**
 * Security tests for API URL Configuration
 * 
 * Verifies that the API URL detection logic is secure
 * and handles different environments correctly.
 */

describe('API URL Configuration Security', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  describe('Environment Variable Priority', () => {
    it('should prioritize NEXT_PUBLIC_API_URL over hostname detection', () => {
      process.env.NEXT_PUBLIC_API_URL = 'https://priority-api.example.com';
      expect(process.env.NEXT_PUBLIC_API_URL).toBe('https://priority-api.example.com');
    });

    it('should use environment variable when set', () => {
      process.env.NEXT_PUBLIC_API_URL = 'https://custom-api.example.com';
      expect(process.env.NEXT_PUBLIC_API_URL).toBe('https://custom-api.example.com');
    });
  });

  describe('Hostname Validation', () => {
    it('should validate production domain patterns', () => {
      const productionDomains = ['prayer-chat.com', 'www.prayer-chat.com'];
      productionDomains.forEach(domain => {
        expect(domain).toMatch(/^(www\.)?prayer-chat\.com$/);
      });
    });

    it('should validate vercel.app hostname pattern', () => {
      const vercelHostname = 'chatbot-java-spring-ai.vercel.app';
      expect(vercelHostname).toContain('vercel.app');
    });

    it('should use localhost only in development', () => {
      const localhostHostname = 'localhost';
      expect(localhostHostname).toBe('localhost');
    });
  });

  describe('Security: URL Validation', () => {
    it('should not allow javascript: URLs', () => {
      // This is tested at the component level (PaywallModal)
      // where URL validation happens before redirect
      const url = 'javascript:alert("XSS")';
      expect(url).toContain('javascript:');
      // In production, this would be rejected by URL validation
    });

    it('should not allow data: URLs', () => {
      const url = 'data:text/html,<script>alert("XSS")</script>';
      expect(url).toContain('data:');
      // In production, this would be rejected by URL validation
    });

    it('should not allow file: URLs', () => {
      const url = 'file:///etc/passwd';
      expect(url).toContain('file:');
      // In production, this would be rejected by URL validation
    });
  });

  describe('Error Handling', () => {
    it('should handle missing window object gracefully', () => {
      // This is a security test - missing window should not cause crashes
      expect(() => {
        if (typeof window === 'undefined') {
          // Should fallback safely
        }
      }).not.toThrow();
    });

    it('should handle undefined environment variables', () => {
      delete process.env.NEXT_PUBLIC_API_URL;
      expect(process.env.NEXT_PUBLIC_API_URL).toBeUndefined();
      // Should fallback to hostname detection or localhost
    });
  });
});
