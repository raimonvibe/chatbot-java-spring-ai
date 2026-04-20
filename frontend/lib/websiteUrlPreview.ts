/**
 * Client-side URL preview and light validation for chatbot website onboarding.
 * The backend remains the source of truth; this guides users (IDN/homograph visibility, fragments, scheme).
 */

/** Aligned with {@code UrlValidationService.MAX_URL_LENGTH} (backend). */
export const WEBSITE_URL_MAX_INPUT_LENGTH = 2048;

export type WebsiteUrlClientPreview = {
  ok: boolean;
  /** Normalized https? URL with fragment removed, suitable to send to the API when {@link ok}. */
  displayHref: string | null;
  hostname: string | null;
  /** Non-fatal notices (e.g. RFC1918) — still ok unless combined with server rejection. */
  notices: string[];
  /** Blocking reasons when ok is false. */
  issues: string[];
};

const BLOCKED_PREFIX = /^(javascript|data|vbscript|file|about|blob|jar|gopher|chrome|whatsapp):/i;

/** C0 controls and DEL (trim does not remove these from ends; interior ASCII space caught separately). */
const DISALLOWED_ASCII_CONTROLS = /[\u0000-\u001F\u007F]/;

/**
 * Line/paragraph separators checked on raw input: `String#trim()` removes \\u2028/\\u2029 from ends in JS,
 * which would otherwise evade validation — reject if they appear anywhere in the pasted string.
 */
const UNICODE_LINE_SEPARATORS = /[\u0085\u2028\u2029]/;

function hasScheme(input: string): boolean {
  return /^[a-z][a-z0-9+.-]*:/i.test(input);
}

function hasAsciiWhitespaceOutsideTrim(s: string): boolean {
  return /[\t\n\v\f\r ]/.test(s);
}

/**
 * Parse and canonicalize user input similarly to typical browser "add https" behavior.
 * Rejects non-http(s) schemes and unparseable input.
 */
export function previewWebsiteUrlInput(raw: string): WebsiteUrlClientPreview {
  const notices: string[] = [];
  const issues: string[] = [];
  const trimmed = raw.trim();

  if (!trimmed) {
    issues.push('Enter a website URL.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  if (raw.length > WEBSITE_URL_MAX_INPUT_LENGTH) {
    issues.push(`URL is too long (maximum ${WEBSITE_URL_MAX_INPUT_LENGTH} characters).`);
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  if (UNICODE_LINE_SEPARATORS.test(raw)) {
    issues.push('This address contains invalid line-separator characters.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  if (DISALLOWED_ASCII_CONTROLS.test(trimmed)) {
    issues.push('This address contains invalid control characters.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  if (hasAsciiWhitespaceOutsideTrim(trimmed)) {
    issues.push('Website URLs cannot contain spaces or line breaks.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  let toParse = trimmed;
  if (!hasScheme(trimmed)) {
    toParse = `https://${trimmed}`;
  }

  if (BLOCKED_PREFIX.test(toParse)) {
    issues.push('Only http(s) website URLs are allowed.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  let u: URL;
  try {
    u = new URL(toParse);
  } catch {
    issues.push('Could not parse this address — check spelling and try again.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  if (u.protocol !== 'http:' && u.protocol !== 'https:') {
    issues.push('Only http(s) website URLs are allowed.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  if (u.username !== '' || u.password !== '') {
    issues.push('URLs with a username or password are not allowed. Use the site address only.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  const hostname = u.hostname;
  if (!hostname || hostname.includes('..')) {
    issues.push('Invalid hostname.');
    return { ok: false, displayHref: null, hostname: null, notices, issues };
  }

  const lower = hostname.toLowerCase();
  if (
    lower === 'localhost' ||
    lower.endsWith('.localhost') ||
    lower.endsWith('.local') ||
    /^127\./.test(lower) ||
    lower === '0.0.0.0' ||
    lower.startsWith('192.168.') ||
    lower.startsWith('10.') ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(lower)
  ) {
    notices.push('This address looks private or local — it may be rejected by the server.');
  }

  u.hash = '';
  const displayHref = u.toString();

  return {
    ok: true,
    displayHref,
    hostname,
    notices,
    issues,
  };
}

/** Show punycode / IDN note when input or resolved host clearly involves internationalized domains. */
export function shouldShowIdnHostnameNote(raw: string, hostname: string | null): boolean {
  if (!hostname || !raw.trim()) return false;
  if (hostname.includes('xn--')) return true;
  return !/^[\x00-\x7F]*$/.test(raw.trim());
}
