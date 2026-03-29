/**
 * Copy text to clipboard with fallback for environments where
 * navigator.clipboard is unavailable (e.g. non-HTTPS, some iframes).
 * Returns true if copy succeeded, false otherwise.
 */
export async function copyTextToClipboard(text: string): Promise<boolean> {
  if (typeof text !== 'string' || !text.trim()) return false;

  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // Fall through to execCommand fallback
  }

  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    textarea.style.top = '0';
    document.body.appendChild(textarea);
    textarea.select();
    textarea.setSelectionRange(0, text.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(textarea);
    return ok;
  } catch {
    return false;
  }
}
