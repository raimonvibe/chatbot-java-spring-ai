import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Pricing Plans - Prayer-Chat',
  description: 'Choose the perfect plan for your Christian AI chatbot. Start with a free trial or upgrade to Pro for unlimited chatbots and messages.',
  alternates: {
    canonical: 'https://prayer-chat.com/pricing',
  },
  openGraph: {
    title: 'Pricing Plans - Prayer-Chat',
    description: 'Choose the perfect plan for your Christian AI chatbot. Start with a free trial or upgrade to Pro for unlimited chatbots and messages.',
    url: 'https://prayer-chat.com/pricing',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Pricing Plans - Prayer-Chat',
    description: 'Choose the perfect plan for your Christian AI chatbot. Start with a free trial or upgrade to Pro for unlimited chatbots and messages.',
  },
};

export default function PricingLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
