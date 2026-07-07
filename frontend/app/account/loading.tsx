import LoadingSpinner from '@/components/ui/LoadingSpinner';

export default function AccountLoading() {
  return (
    <main className="min-h-screen flex items-center justify-center text-brown-50">
      <LoadingSpinner label="Loading account…" className="text-brown-100" />
    </main>
  );
}
