export default function StructuredData() {
  const organizationSchema = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Prayer-Chat',
    legalName: 'RaimonVibe',
    url: 'https://prayer-chat.com',
    logo: 'https://prayer-chat.com/favicon.png',
    foundingDate: '2024',
    address: {
      '@type': 'PostalAddress',
      streetAddress: 'Timpaan 1-B',
      addressLocality: 'Hoorn',
      postalCode: '1628 MT',
      addressCountry: 'NL',
    },
    contactPoint: {
      '@type': 'ContactPoint',
      email: 'info@raimonvibe.com',
      contactType: 'Customer Support',
      availableLanguage: ['en', 'nl'],
    },
    sameAs: [
      'https://github.com/raimonvibe/chatbot-java-spring-ai',
    ],
  };

  const softwareApplicationSchema = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'Prayer-Chat',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web Browser',
    description: 'AI-powered chatbot platform infused with Christian values and biblical wisdom for ministries and businesses',
    offers: {
      '@type': 'AggregateOffer',
      priceCurrency: 'USD',
      lowPrice: '0',
      highPrice: '29',
      offers: [
        {
          '@type': 'Offer',
          name: 'Free Trial',
          price: '0',
          priceCurrency: 'USD',
          description: '1 Chatbot, 100 Messages/month, Basic website analysis',
        },
        {
          '@type': 'Offer',
          name: 'Pro Plan',
          price: '29',
          priceCurrency: 'USD',
          description: 'Unlimited Chatbots, Unlimited Messages, Advanced features',
        },
      ],
    },
    aggregateRating: {
      '@type': 'AggregateRating',
      ratingValue: '5.0',
      ratingCount: '1',
    },
    provider: {
      '@type': 'Organization',
      name: 'RaimonVibe',
      url: 'https://www.raimonvibe.eu/',
    },
    featureList: [
      'AI-Powered Chatbot Creation',
      'Christian Values Integration',
      'Biblical Wisdom',
      'Website Content Analysis',
      'Multi-Language Support',
      'Webhook Integration',
      'Quick Replies',
      'Conversation Export',
    ],
  };

  const websiteSchema = {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'Prayer-Chat',
    url: 'https://prayer-chat.com',
    description: 'Create AI-powered chatbots infused with Christian values and biblical wisdom for your ministry or business',
    publisher: {
      '@type': 'Organization',
      name: 'RaimonVibe',
    },
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: 'https://prayer-chat.com/search?q={search_term_string}',
      },
      'query-input': 'required name=search_term_string',
    },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationSchema) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(softwareApplicationSchema) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteSchema) }}
      />
    </>
  );
}
