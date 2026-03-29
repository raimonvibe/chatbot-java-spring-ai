import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Contact Us - Prayer-Chat',
  description: 'Get in touch with the Prayer-Chat team. We\'re here to help with questions, support, and feedback about our Christian AI chatbot platform.',
  alternates: {
    canonical: 'https://prayer-chat.com/contact',
  },
  openGraph: {
    title: 'Contact Us - Prayer-Chat',
    description: 'Get in touch with the Prayer-Chat team. We\'re here to help with questions, support, and feedback about our Christian AI chatbot platform.',
    url: 'https://prayer-chat.com/contact',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Contact Us - Prayer-Chat',
    description: 'Get in touch with the Prayer-Chat team. We\'re here to help with questions, support, and feedback about our Christian AI chatbot platform.',
  },
};

export default function ContactLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
