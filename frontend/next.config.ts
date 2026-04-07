import type { NextConfig } from 'next';

/**
 * Proxy /api to the Java backend. Required on Vercel when the project Root Directory is `frontend/`,
 * because root-level vercel.json rewrites are not applied.
 *
 * Local `next dev`: NODE_ENV is development — no rewrites; the client uses http://localhost:8081 (see getApiBaseUrl).
 */
const backendProxyOrigin = (
  process.env.BACKEND_PROXY_ORIGIN ||
  'https://prayer-chat-backend-web-service.onrender.com'
).replace(/\/$/, '');

const nextConfig: NextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  async rewrites() {
    if (process.env.NODE_ENV === 'development') {
      return [];
    }
    return [
      {
        source: '/api/:path*',
        destination: `${backendProxyOrigin}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
