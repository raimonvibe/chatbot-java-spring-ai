import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Privacy Notice - Prayer-Chat',
  description: 'Learn how Prayer-Chat protects your privacy and handles your data in compliance with GDPR regulations.',
  openGraph: {
    title: 'Privacy Notice - Prayer-Chat',
    description: 'Learn how Prayer-Chat protects your privacy and handles your data in compliance with GDPR regulations.',
    url: 'https://prayer-chat.com/privacy',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Privacy Notice - Prayer-Chat',
    description: 'Learn how Prayer-Chat protects your privacy and handles your data in compliance with GDPR regulations.',
  },
};

export default function PrivacyPage() {
  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <h1 className="text-3xl md:text-4xl font-bold mb-6 bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
        Privacy Notice
      </h1>

      <div className="space-y-6 text-brown-800 text-base md:text-lg leading-relaxed">
        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">1. Data Controller</h2>
          <p>
            RaimonVibe<br />
            Timpaan 1-B<br />
            1628 MT Hoorn<br />
            Netherlands<br />
            Email: info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">2. Data Collection and Processing</h2>
          <p className="mb-3">
            Our Prayer-Chat AI chatbot service processes the following data:
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>Account Information:</strong> When you create an account, we collect your email address and authentication credentials provided through Google OAuth.</li>
            <li><strong>Website URLs:</strong> URLs of websites you provide for chatbot creation are analyzed to extract content for training your chatbot.</li>
            <li><strong>Chat Conversations:</strong> Messages exchanged with your chatbots are stored to provide the service and improve chatbot responses.</li>
            <li><strong>Chatbot Configurations:</strong> Settings and preferences you configure for your chatbots, including biblical verse integration preferences.</li>
            <li><strong>Usage Data:</strong> We track chatbot usage, message counts, and service utilization to enforce limits and manage subscriptions.</li>
            <li><strong>Payment Information:</strong> If you subscribe to our paid plan, payment processing is handled securely by Stripe. We do not store your credit card details.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">3. Purpose of Data Processing</h2>
          <p className="mb-2">We process your data for the following purposes:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li>To provide AI chatbot creation and management services</li>
            <li>To analyze website content and create customized chatbots</li>
            <li>To integrate biblical wisdom and Christian values into chatbot responses</li>
            <li>To manage your account and authentication</li>
            <li>To process payments and manage subscriptions</li>
            <li>To enforce usage limits and prevent abuse</li>
            <li>To improve chatbot quality and service performance</li>
            <li>To provide customer support</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">4. AI Processing and Third-Party Services</h2>
          <p className="mb-2">
            To provide our AI chatbot service, we use the following third-party AI providers:
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>OpenAI:</strong> For natural language processing and chatbot responses</li>
            <li><strong>Anthropic (Claude):</strong> For advanced AI reasoning and content analysis</li>
          </ul>
          <p className="mt-3">
            Your chatbot conversations and website content may be processed by these AI services to generate responses.
            These providers have their own privacy policies and data processing practices.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">5. Analytics and Tracking</h2>
          <p>
            We do not use third-party analytics tools or tracking cookies. We only collect essential usage data
            necessary to provide and improve the service.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">6. Data Storage and Security</h2>
          <p className="mb-2">
            We implement appropriate technical and organizational measures to protect your data:
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>All data is stored securely in encrypted databases</li>
            <li>Website content analyzed for chatbot creation is stored only as long as necessary</li>
            <li>Chat conversation history is retained to provide service continuity</li>
            <li>All data transmission occurs over encrypted HTTPS connections</li>
            <li>Authentication is handled through secure Google OAuth</li>
            <li>Access to data is restricted to authorized personnel only</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">7. Data Sharing</h2>
          <p className="mb-2">We share your data only with the following service providers:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>Authentication Provider:</strong> Google OAuth for secure account authentication</li>
            <li><strong>Payment Processor:</strong> Stripe for subscription payment processing</li>
            <li><strong>AI Service Providers:</strong> OpenAI and Anthropic for chatbot processing and responses</li>
            <li><strong>Cloud Hosting:</strong> Render.com or similar platforms for secure application hosting</li>
          </ul>
          <p className="mt-3">
            We do not sell, rent, or share your personal data with any third parties for marketing purposes.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">8. Your Rights (GDPR)</h2>
          <p className="mb-2">Under the General Data Protection Regulation (GDPR), you have the following rights:</p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>Right to Access:</strong> Request a copy of the personal data we hold about you</li>
            <li><strong>Right to Rectification:</strong> Request correction of inaccurate personal data</li>
            <li><strong>Right to Erasure:</strong> Request deletion of your personal data and chatbots</li>
            <li><strong>Right to Restriction:</strong> Request restriction of processing of your personal data</li>
            <li><strong>Right to Data Portability:</strong> Receive your personal data in a structured, machine-readable format</li>
            <li><strong>Right to Object:</strong> Object to processing of your personal data</li>
            <li><strong>Right to Withdraw Consent:</strong> Withdraw consent at any time where processing is based on consent</li>
          </ul>
          <p className="mt-3">
            To exercise any of these rights, please contact us at info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">9. Data Retention</h2>
          <p>
            We retain your account information and chatbot data for as long as your account is active or as needed
            to provide services. If you delete your account, we will delete your personal data within 30 days,
            except where we are required to retain it for legal purposes. Chat conversation history may be retained
            for service improvement unless you request deletion.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">10. International Data Transfers</h2>
          <p>
            Your data may be transferred to and processed in countries outside the European Economic Area (EEA),
            including the United States where our AI service providers are located. We ensure that appropriate
            safeguards are in place to protect your data in accordance with GDPR requirements.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">11. Children&apos;s Privacy</h2>
          <p>
            Our service is not directed to individuals under the age of 16. We do not knowingly collect personal
            information from children under 16. If you become aware that a child has provided us with personal data,
            please contact us at info@raimonvibe.com
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">12. Biblical Content and Privacy</h2>
          <p>
            When you enable biblical verse integration in your chatbots, we use publicly available biblical texts
            to enhance responses. No personal religious beliefs or practices are tracked or stored beyond your
            chatbot configuration preferences.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">13. Changes to This Privacy Notice</h2>
          <p>
            We may update this Privacy Notice from time to time. We will notify you of any changes by posting
            the new Privacy Notice on this page and updating the &quot;Last Updated&quot; date below.
          </p>
        </section>

        <section>
          <h2 className="text-xl md:text-2xl font-semibold mb-3 text-brown-800">14. Contact Us</h2>
          <p>
            If you have any questions about this Privacy Notice or our data practices, please contact us at:
          </p>
          <p className="mt-2">
            Email: info@raimonvibe.com<br />
            Address: Timpaan 1-B, 1628 MT Hoorn, Netherlands
          </p>
        </section>

        <section className="pt-6 border-t border-brown-200">
          <p className="text-sm text-brown-600">
            <strong>Last Updated:</strong> {new Date().toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}
          </p>
        </section>
      </div>
    </div>
  );
}
