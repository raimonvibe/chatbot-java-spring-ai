import {
  previewWebsiteUrlInput,
  shouldShowIdnHostnameNote,
  WEBSITE_URL_MAX_INPUT_LENGTH,
} from '../websiteUrlPreview';

describe('previewWebsiteUrlInput', () => {
  it('adds https and strips hash for display', () => {
    const p = previewWebsiteUrlInput('example.com/path#frag');
    expect(p.ok).toBe(true);
    expect(p.displayHref).toBe('https://example.com/path');
    expect(p.hostname).toBe('example.com');
  });

  it('accepts explicit https', () => {
    const p = previewWebsiteUrlInput('https://sub.example.com/');
    expect(p.ok).toBe(true);
    expect(p.hostname).toBe('sub.example.com');
  });

  it('rejects javascript scheme', () => {
    const p = previewWebsiteUrlInput('javascript:alert(1)');
    expect(p.ok).toBe(false);
    expect(p.issues.length).toBeGreaterThan(0);
  });

  it('rejects non-http schemes', () => {
    const p = previewWebsiteUrlInput('ftp://example.com');
    expect(p.ok).toBe(false);
  });

  it('rejects unparseable input', () => {
    const p = previewWebsiteUrlInput(':::');
    expect(p.ok).toBe(false);
  });

  it('adds notice for localhost', () => {
    const p = previewWebsiteUrlInput('http://localhost:3000');
    expect(p.ok).toBe(true);
    expect(p.notices.some((n) => n.toLowerCase().includes('private'))).toBe(true);
  });

  it('rejects username/password in URL (parser ambiguity)', () => {
    const p = previewWebsiteUrlInput('https://user@example.com/');
    expect(p.ok).toBe(false);
    expect(p.issues.some((i) => i.toLowerCase().includes('password') || i.includes('username'))).toBe(true);
  });

  it('rejects embedded newlines and control characters', () => {
    expect(previewWebsiteUrlInput('https://a.com/\r\nb').ok).toBe(false);
    expect(previewWebsiteUrlInput('https://a.com/\u2028').ok).toBe(false);
    expect(previewWebsiteUrlInput('https://a.com/\u007f').ok).toBe(false);
  });

  it('rejects interior ASCII whitespace', () => {
    expect(previewWebsiteUrlInput('https://example.com/foo bar').ok).toBe(false);
  });

  it('rejects input longer than max length', () => {
    const long = 'a'.repeat(WEBSITE_URL_MAX_INPUT_LENGTH + 1);
    const p = previewWebsiteUrlInput(long);
    expect(p.ok).toBe(false);
    expect(p.issues.some((i) => i.toLowerCase().includes('too long'))).toBe(true);
  });

  it('rejects additional risky schemes', () => {
    expect(previewWebsiteUrlInput('jar:http://example.com!/').ok).toBe(false);
    expect(previewWebsiteUrlInput('gopher://example.com').ok).toBe(false);
  });
});

describe('shouldShowIdnHostnameNote', () => {
  it('is true for punycode host', () => {
    expect(shouldShowIdnHostnameNote('https://xn--fiqs8s.cn/', 'xn--fiqs8s.cn')).toBe(true);
  });

  it('is true when input contains non-ASCII', () => {
    expect(shouldShowIdnHostnameNote('https://例え.jp/', 'xn--r8jz45g.jp')).toBe(true);
  });

  it('is false for plain ASCII site', () => {
    expect(shouldShowIdnHostnameNote('https://example.com', 'example.com')).toBe(false);
  });
});
