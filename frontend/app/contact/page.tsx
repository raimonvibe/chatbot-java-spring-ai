'use client';

import { useState } from 'react';

export default function ContactPage() {
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle');

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setStatus('submitting');

    const form = e.currentTarget;
    const formData = new FormData(form);

    try {
      const response = await fetch('https://formspree.io/f/xwplqeky', {
        method: 'POST',
        body: formData,
        headers: {
          Accept: 'application/json',
        },
      });

      if (response.ok) {
        setStatus('success');
        form.reset();
      } else {
        setStatus('error');
      }
    } catch {
      setStatus('error');
    }
  };

  return (
    <div className="max-w-2xl mx-auto py-8 px-4">
      <h1 className="text-3xl md:text-4xl font-bold mb-4 bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
        Contact Us
      </h1>
      <p className="text-brown-800 mb-8 text-lg">
        Have a question, feedback, or need support for Prayer-Chat? We&apos;d love to hear from you!
      </p>

      <div className="bg-gradient-to-br from-brown-50 to-gold-50 rounded-lg shadow-sm border border-brown-200 p-6 mb-8">
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Honeypot field for spam prevention */}
          <input type="text" name="_gotcha" style={{ display: 'none' }} />

          <div>
            <label htmlFor="name" className="block text-base font-medium text-brown-800 mb-2">
              Name <span className="text-red-600">*</span>
            </label>
            <input
              type="text"
              id="name"
              name="name"
              required
              className="w-full px-4 py-3 text-base border border-brown-300 rounded-md focus:outline-none focus:ring-2 focus:ring-gold-600 focus:border-transparent bg-white"
              placeholder="Your name"
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-base font-medium text-brown-800 mb-2">
              Email <span className="text-red-600">*</span>
            </label>
            <input
              type="email"
              id="email"
              name="email"
              required
              className="w-full px-4 py-3 text-base border border-brown-300 rounded-md focus:outline-none focus:ring-2 focus:ring-gold-600 focus:border-transparent bg-white"
              placeholder="your.email@example.com"
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <label htmlFor="subject" className="block text-base font-medium text-brown-800 mb-2">
              Subject <span className="text-red-600">*</span>
            </label>
            <input
              type="text"
              id="subject"
              name="subject"
              required
              className="w-full px-4 py-3 text-base border border-brown-300 rounded-md focus:outline-none focus:ring-2 focus:ring-gold-600 focus:border-transparent bg-white"
              placeholder="What is this about?"
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <label htmlFor="message" className="block text-base font-medium text-brown-800 mb-2">
              Message <span className="text-red-600">*</span>
            </label>
            <textarea
              id="message"
              name="message"
              required
              rows={6}
              className="w-full px-4 py-3 text-base border border-brown-300 rounded-md focus:outline-none focus:ring-2 focus:ring-gold-600 focus:border-transparent resize-vertical bg-white"
              placeholder="Your message..."
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <button
              type="submit"
              disabled={status === 'submitting'}
              className="w-full bg-gradient-to-r from-brown-600 to-gold-600 hover:from-brown-700 hover:to-gold-700 disabled:bg-brown-300 text-white font-medium py-3 px-6 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-gold-600 focus:ring-offset-2 text-lg"
            >
              {status === 'submitting' ? 'Sending...' : 'Send Message'}
            </button>
          </div>

          {status === 'success' && (
            <div className="bg-green-50 border border-green-200 text-green-800 px-4 py-3 rounded-md">
              <p className="font-medium">Message sent successfully!</p>
              <p className="text-sm mt-1">We&apos;ll get back to you as soon as possible.</p>
            </div>
          )}

          {status === 'error' && (
            <div className="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-md">
              <p className="font-medium">Something went wrong</p>
              <p className="text-sm mt-1">Please try again or email us directly at info@raimonvibe.com</p>
            </div>
          )}
        </form>
      </div>

      <div className="bg-brown-50 border border-brown-200 rounded-lg p-4 mb-6">
        <h2 className="text-lg font-semibold text-brown-800 mb-2">Alternative Contact</h2>
        <div className="space-y-2 text-brown-700">
          <p>
            <strong>Email:</strong>{' '}
            <a href="mailto:info@raimonvibe.com" className="text-gold-700 hover:text-gold-800 underline">
              info@raimonvibe.com
            </a>
          </p>
          <p className="text-sm text-brown-600">
            For a postal address (e.g. formal or legal mail), see the <em>Data Controller</em> section in our{' '}
            <a href="/privacy" className="text-gold-700 hover:text-gold-800 underline">
              Privacy Notice
            </a>
            .
          </p>
        </div>
      </div>

      <p className="text-base text-brown-700 mt-8">
        By submitting this form, you agree to our{' '}
        <a href="/privacy" className="text-gold-700 hover:text-gold-800 underline">
          Privacy Notice
        </a>
        . Your information will only be used to respond to your inquiry.
      </p>
    </div>
  );
}
