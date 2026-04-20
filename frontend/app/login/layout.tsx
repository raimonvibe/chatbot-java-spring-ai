import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Login - Prayer-Chat',
  description: 'Sign in to Prayer-Chat to create and manage your Christian AI chatbots with biblical wisdom.',
  alternates: {
    canonical: 'https://www.prayer-chat.com/login',
  },
  openGraph: {
    title: 'Login - Prayer-Chat',
    description: 'Sign in to Prayer-Chat to create and manage your Christian AI chatbots with biblical wisdom.',
    url: 'https://www.prayer-chat.com/login',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Login - Prayer-Chat',
    description: 'Sign in to Prayer-Chat to create and manage your Christian AI chatbots with biblical wisdom.',
  },
  robots: {
    index: false,
    follow: true,
  },
};

export default function LoginLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
