import {
  getUserFacingFetchError,
  isLikelyNetworkError,
  getSafeErrorMessage,
  logClientIssue,
} from '../api';

describe('isLikelyNetworkError', () => {
  it('is true for Failed to fetch (Chrome after ERR_NETWORK_CHANGED / offline)', () => {
    expect(isLikelyNetworkError(new TypeError('Failed to fetch'))).toBe(true);
  });

  it('is true for Firefox network message', () => {
    expect(isLikelyNetworkError(new TypeError('NetworkError when attempting to fetch resource.'))).toBe(true);
  });

  it('is true for AbortError', () => {
    const e = new Error('Aborted');
    e.name = 'AbortError';
    expect(isLikelyNetworkError(e)).toBe(true);
  });

  it('is false for HTTP-style application errors', () => {
    expect(isLikelyNetworkError(new Error('Failed to update chatbot'))).toBe(false);
  });

  it('is false for non-Error', () => {
    expect(isLikelyNetworkError('string')).toBe(false);
  });
});

describe('getUserFacingFetchError', () => {
  it('returns connection message for network failures', () => {
    const msg = getUserFacingFetchError(new TypeError('Failed to fetch'), 'fallback');
    expect(msg).toContain('connection');
    expect(msg).not.toBe('fallback');
  });

  it('delegates to getSafeErrorMessage for other errors', () => {
    const msg = getUserFacingFetchError(new Error('Chatbot not found'), 'fallback');
    expect(msg).toBe(getSafeErrorMessage(new Error('Chatbot not found'), 'fallback'));
  });
});

describe('getSafeErrorMessage hardening', () => {
  it('normalizes CRLF in messages (UI / log hygiene)', () => {
    expect(getSafeErrorMessage(new Error('a\r\nb'), 'fb')).toBe('a b');
  });

  it('removes Unicode bidi overrides from reflected messages', () => {
    const msg = getSafeErrorMessage(new Error(`pretend\u202e`), 'fb');
    expect(msg).not.toMatch(/\u202e/);
    expect(msg).toContain('pretend');
  });
});

describe('logClientIssue', () => {
  it('falls back to scope client when label is unsafe', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    logClientIssue('bad$scope', new Error('x'));
    expect(warn.mock.calls[0][0]).toBe('[Prayer-Chat:client]');
    warn.mockRestore();
  });

  it('allows dotted diagnostic scopes', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    logClientIssue('dashboard.avatar.save', new Error('x'));
    expect(warn.mock.calls[0][0]).toBe('[Prayer-Chat:dashboard.avatar.save]');
    warn.mockRestore();
  });

  it('sanitizes error type name in production-style summary', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const e = new Error('hello');
    e.name = 'Type<script>Error';
    logClientIssue('chat.send', e);
    expect(String(warn.mock.calls[0][2])).toMatch(/^Error: hello$/);
    warn.mockRestore();
  });
});
