import LoadingSpinner from '@/components/ui/LoadingSpinner';

export default function ChatbotPreviewLoading() {
  return (
    <div className="min-h-[100dvh] flex items-center justify-center">
      <LoadingSpinner label="Loading chatbot preview…" />
    </div>
  );
}
