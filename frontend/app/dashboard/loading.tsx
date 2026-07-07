import LoadingSpinner from '@/components/ui/LoadingSpinner';

export default function DashboardLoading() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <LoadingSpinner label="Loading your chatbots…" />
    </div>
  );
}
