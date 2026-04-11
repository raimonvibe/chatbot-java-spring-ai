import { isGoogleUserContentProfilePictureUrl } from '../profile-picture-url';

describe('isGoogleUserContentProfilePictureUrl', () => {
  it('accepts https Google user-content CDN', () => {
    expect(
      isGoogleUserContentProfilePictureUrl('https://lh3.googleusercontent.com/a/abc'),
    ).toBe(true);
  });

  it('rejects host that only contains substring in path', () => {
    expect(
      isGoogleUserContentProfilePictureUrl('https://evil.com/googleusercontent.com'),
    ).toBe(false);
  });

  it('rejects lookalike host googleusercontent.com.evil.com', () => {
    expect(
      isGoogleUserContentProfilePictureUrl('https://googleusercontent.com.evil.com/x'),
    ).toBe(false);
  });

  it('rejects non-https', () => {
    expect(isGoogleUserContentProfilePictureUrl('http://lh3.googleusercontent.com/x')).toBe(false);
  });
});
