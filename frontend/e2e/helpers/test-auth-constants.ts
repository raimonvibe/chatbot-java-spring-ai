/**
 * E2E test-only auth constants. Do not use outside Playwright E2E tests.
 * This is NOT a credential – signature is literally "mock_signature"; never use in production.
 * Format: valid JWT shape (header.payload.signature) so the app’s client-side checks accept it.
 * Secret scanners: see trivy-secret.yaml (paths under frontend/e2e are excluded).
 */
export const E2E_MOCK_AUTH_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature';

/** Substring that identifies this mock token in headers (for route mocks). */
export const E2E_MOCK_TOKEN_MARKER = 'mock_signature';
