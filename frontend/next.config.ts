import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  async redirects() {
    return [
      {
        source: '/:path*',
        has: [
          {
            type: 'host',
            value: 'www.prayer-chat.com',
          },
        ],
        destination: 'https://prayer-chat.com/:path*',
        permanent: true,
      },
    ];
  },
};

export default nextConfig;
