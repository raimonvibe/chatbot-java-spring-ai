import { ReactNode } from 'react';

interface ErrorBannerProps {
  message: string;
  action?: ReactNode;
}

export default function ErrorBanner({ message, action }: ErrorBannerProps) {
  if (!message) return null;
  return (
    <div
      role="alert"
      className="rounded-lg border border-red-100 bg-red-50/90 px-4 py-3 text-sm text-red-800"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p>{message}</p>
        {action}
      </div>
    </div>
  );
}
