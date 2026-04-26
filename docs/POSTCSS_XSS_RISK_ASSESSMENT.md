# PostCSS XSS Advisory Risk Assessment

## Advisory

- Package: `postcss`
- Affected: `< 8.5.10`
- Advisory summary: unescaped `</style>` in stringified CSS can break out of inline `<style>` and enable XSS in specific embedding flows.

## Project Impact Assessment

Current project usage does **not** include the vulnerable pattern:

- No runtime usage of `postcss.parse(...).toResult().css` on user-provided CSS.
- No feature that accepts arbitrary end-user CSS and injects it into inline `<style>...</style>`.
- Existing `dangerouslySetInnerHTML` usage in frontend is limited to structured JSON-LD (`application/ld+json`), not CSS.

Based on this, the advisory is currently assessed as **not exploitable in this codebase's active runtime paths**.

## Why Dependabot Still Flags It

`next@16.2.4` currently pins `postcss@8.4.31` as a transitive dependency.  
At time of writing, this cannot be upgraded independently without upstream Next.js changes.

## Mitigations and Guardrails

- Do not implement features that parse and re-stringify user CSS into inline `<style>` tags.
- If such a feature is added in the future, escape `</style>` before HTML embedding (or avoid inline style embedding entirely).
- Keep dependency monitoring enabled and upgrade Next.js as soon as it ships a patched transitive `postcss`.

## Operational Decision

- Status: **Accepted risk (temporary)**
- Condition: no runtime user-CSS stringify flow exists
- Exit criteria: upgrade to Next.js release that includes patched `postcss` transitive dependency
