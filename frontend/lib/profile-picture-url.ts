/**
 * True only for https URLs whose host is Google user-content CDN (profile avatars).
 * Uses URL parsing so hosts like evil-googleusercontent.com are rejected.
 */
export function isGoogleUserContentProfilePictureUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:') return false;
    const host = parsed.hostname.toLowerCase();
    return host === 'googleusercontent.com' || host.endsWith('.googleusercontent.com');
  } catch {
    return false;
  }
}
