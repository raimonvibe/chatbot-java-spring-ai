import React from 'react';
import { render, screen } from '@testing-library/react';
import Footer from '../Footer';

describe('Footer', () => {
  it('should render copyright and RaimonVibe link', () => {
    render(<Footer />);
    const year = new Date().getFullYear();
    expect(screen.getByText(new RegExp(`© ${year}`))).toBeInTheDocument();
    const link = screen.getByRole('link', { name: 'RaimonVibe' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://www.raimonvibe.eu/');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('should have internal links only for Contact, Privacy, Legal (no open redirect)', () => {
    render(<Footer />);
    expect(screen.getByRole('link', { name: /Contact/i })).toHaveAttribute('href', '/contact');
    expect(screen.getByRole('link', { name: /Privacy Notice/i })).toHaveAttribute('href', '/privacy');
    expect(screen.getByRole('link', { name: /Legal Notice/i })).toHaveAttribute('href', '/legal');
    const contactHref = screen.getByRole('link', { name: /Contact/i }).getAttribute('href');
    expect(contactHref).not.toMatch(/^https?:/);
    expect(contactHref).toMatch(/^\/contact$/);
  });

  it('should render footer as landmark with accessible nav', () => {
    render(<Footer />);
    const footer = screen.getByRole('contentinfo');
    expect(footer).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Footer links' });
    expect(nav).toBeInTheDocument();
  });

  it('should not contain script or dangerous HTML', () => {
    const { container } = render(<Footer />);
    const scripts = container.querySelectorAll('script');
    expect(scripts.length).toBe(0);
    const iframes = container.querySelectorAll('iframe');
    expect(iframes.length).toBe(0);
  });
});
