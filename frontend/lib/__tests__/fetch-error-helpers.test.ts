import {
  getUserFacingFetchError,
  isLikelyNetworkError,
  getSafeErrorMessage,
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
