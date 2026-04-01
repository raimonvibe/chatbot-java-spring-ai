import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import Header from '../components/Header';
import Footer from '../components/Footer';
import StructuredData from '../components/StructuredData';
import Providers from '../components/Providers';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: "Prayer-Chat - Christian AI Chatbot Platform",
  description:
    "Create AI-powered chatbots infused with Christian values and biblical wisdom for your ministry or business",
  icons: {
    icon: "/favicon.ico",
    apple: "/social.png",
  },
  metadataBase: new URL('https://prayer-chat.com'),
  alternates: {
    canonical: 'https://prayer-chat.com',
  },
  openGraph: {
    title: "Prayer-Chat - Christian AI Chatbot Platform",
    description: "Create AI-powered chatbots infused with Christian values and biblical wisdom for your ministry or business",
    url: 'https://prayer-chat.com',
    siteName: 'Prayer-Chat',
    locale: 'en_US',
    type: 'website',
    images: [
      {
        url: '/social.png',
        width: 1200,
        height: 630,
        alt: 'Prayer-Chat - Christian AI Chatbot Platform',
      },
    ],
  },
  twitter: {
    card: 'summary_large_image',
    title: "Prayer-Chat - Christian AI Chatbot Platform",
    description: "Create AI-powered chatbots infused with Christian values and biblical wisdom for your ministry or business",
    images: ['/social.png'],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-video-preview': -1,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },
};


export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <head>
        <StructuredData />
      </head>
      <body className={inter.className}>
        <Providers>
          <Header />
          {children}
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
