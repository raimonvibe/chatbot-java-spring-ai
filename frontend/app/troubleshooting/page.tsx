import type { Metadata } from 'next';
import TroubleshootingContent from './TroubleshootingContent';

export const metadata: Metadata = {
  title: 'Troubleshooting Embed Issues | Prayer-Chat',
  description:
    'Beginner-friendly help for common Prayer-Chat embed problems: not showing, disappearing, styling, cache, HTTPS, and mobile issues.',
};

export default function TroubleshootingPage() {
  return <TroubleshootingContent />;
}
