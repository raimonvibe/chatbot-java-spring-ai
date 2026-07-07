import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  label?: string;
  className?: string;
}

export default function LoadingSpinner({ label = 'Loading…', className = '' }: LoadingSpinnerProps) {
  return (
    <div className={`flex flex-col items-center justify-center gap-3 py-12 text-brown-700 ${className}`}>
      <Loader2 className="h-8 w-8 animate-spin" aria-hidden="true" />
      <p className="text-sm">{label}</p>
    </div>
  );
}
