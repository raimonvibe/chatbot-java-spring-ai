export interface ChatbotTheme {
  primaryColor: string;
  secondaryColor: string;
  borderRadius: string;
}

export function parseBrandingConfig(configJson: string | undefined): ChatbotTheme {
  const fallback = { primaryColor: '#8B5E34', secondaryColor: '#E8DCC4', borderRadius: '12px' };
  if (!configJson || !configJson.trim()) return fallback;
  if (configJson.length > 4096) return fallback;
  try {
    const o = JSON.parse(configJson) as Record<string, unknown>;
    const primaryColor = typeof o.primaryColor === 'string' ? o.primaryColor.trim() : fallback.primaryColor;
    const secondaryColor = typeof o.secondaryColor === 'string' ? o.secondaryColor.trim() : fallback.secondaryColor;
    const borderRadius = typeof o.borderRadius === 'string' ? o.borderRadius.trim() : fallback.borderRadius;
    if (!/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(primaryColor)) return fallback;
    if (!/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(secondaryColor)) return fallback;
    if (!/^[0-9]+(px|em|rem)?$/.test(borderRadius)) {
      return { primaryColor, secondaryColor, borderRadius: fallback.borderRadius };
    }
    return { primaryColor, secondaryColor, borderRadius };
  } catch {
    return fallback;
  }
}

export function parseChatbotId(raw: string | string[] | undefined): number | null {
  if (raw == null) return null;
  const s = typeof raw === 'string' ? raw : raw[0];
  if (s == null || s.length === 0) return null;
  const n = parseInt(s, 10);
  if (!Number.isInteger(n) || n < 1 || !Number.isFinite(n)) return null;
  return n;
}

export function getHostname(url: string | undefined): string {
  if (!url) return 'your-website.com';
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url.replace(/^https?:\/\//, '').split('/')[0] || 'your-website.com';
  }
}

export function getSafeWebsitePreviewUrl(url: string | undefined): string | null {
  if (!url?.trim()) return null;
  const trimmed = url.trim();
  const withScheme = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const parsed = new URL(withScheme);
    const protocol = parsed.protocol.toLowerCase();
    if (protocol !== 'http:' && protocol !== 'https:') return null;
    if (!parsed.hostname) return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

export function chatbotIdMatchesRoute(chatbot: { id: number | string }, routeId: number): boolean {
  const cid = typeof chatbot.id === 'number' ? chatbot.id : Number(chatbot.id);
  return Number.isFinite(cid) && cid === routeId;
}
