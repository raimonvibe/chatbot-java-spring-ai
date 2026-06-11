'use client';

import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Book, Sparkles, TrendingUp, AlertCircle, CheckCircle2 } from 'lucide-react';
import { ClipLoader } from 'react-spinners';
import { analyzeChristianContent, type ChristianContentAnalysis, type VerseMatch } from '@/lib/api';
import { copyTextToClipboard } from '@/lib/clipboard';

interface ChristianContentAnalysisProps {
  chatbotId: number;
  chatbotName: string;
  /** Optional: website URL for this chatbot (enables "Run Website Analysis" CTA when no content) */
  websiteUrl?: string;
  /** Optional: called when user clicks "Run Website Analysis" (e.g. to trigger scan). Run Christian Analysis again after scan completes. */
  onRunWebsiteAnalysis?: () => void | Promise<void>;
}

export default function ChristianContentAnalysisComponent({ 
  chatbotId, 
  chatbotName,
  websiteUrl,
  onRunWebsiteAnalysis,
}: ChristianContentAnalysisProps) {
  const [analysis, setAnalysis] = useState<ChristianContentAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [maxVerses, setMaxVerses] = useState(20);
  const [similarityThreshold, setSimilarityThreshold] = useState(0.5);

  const handleAnalyze = async () => {
    setLoading(true);
    setError(null);
    setErrorCode(null);
    setAnalysis(null);

    try {
      const result = await analyzeChristianContent(chatbotId, maxVerses, similarityThreshold);
      setAnalysis(result);
    } catch (err: any) {
      setError(err.message || 'Failed to analyze Christian content');
      setErrorCode(err.code ?? null);
      console.error('Error analyzing Christian content:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-lg p-6 border border-brown-200">
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-gradient-to-br from-brown-100 to-gold-100 rounded-lg">
          <Book className="w-6 h-6 text-brown-700" />
        </div>
        <div>
          <h3 className="text-xl font-bold text-brown-800">Christian Content Analysis</h3>
          <p className="text-sm text-brown-600">AI-powered Bible verse matching for {chatbotName}</p>
        </div>
      </div>

      {/* Analysis Controls */}
      <div className="mb-6 space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label htmlFor="maxVerses" className="block text-sm font-medium text-brown-700 mb-2">
              Max Verses: {maxVerses}
            </label>
            <input
              type="range"
              id="maxVerses"
              min="5"
              max="50"
              value={maxVerses}
              onChange={(e) => setMaxVerses(Number(e.target.value))}
              className="w-full h-2 bg-brown-200 rounded-lg appearance-none cursor-pointer"
            />
          </div>
          <div>
            <label htmlFor="threshold" className="block text-sm font-medium text-brown-700 mb-2">
              Similarity Threshold: {(similarityThreshold * 100).toFixed(0)}%
            </label>
            <input
              type="range"
              id="threshold"
              min="0.1"
              max="1.0"
              step="0.05"
              value={similarityThreshold}
              onChange={(e) => setSimilarityThreshold(Number(e.target.value))}
              className="w-full h-2 bg-brown-200 rounded-lg appearance-none cursor-pointer"
            />
          </div>
        </div>

        <button
          onClick={handleAnalyze}
          disabled={loading}
          className="w-full px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg font-semibold hover:shadow-xl transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? (
            <>
              <ClipLoader color="#ffffff" size={20} speedMultiplier={0.8} />
              <span>Analyzing...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-5 h-5" />
              Analyze Christian Content
            </>
          )}
        </button>
      </div>

      {/* Error Message */}
      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg flex items-start gap-3"
          >
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-red-800">Error</p>
              <p className="text-sm text-red-600">{error}</p>
              {errorCode === 'NO_WEBSITE_CONTENT' && websiteUrl && onRunWebsiteAnalysis && (
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => onRunWebsiteAnalysis()}
                    className="px-3 py-1.5 text-sm font-medium bg-brown-600 text-white rounded-lg hover:bg-brown-700 transition-colors"
                  >
                    Run Website Analysis first
                  </button>
                  <span className="text-xs text-brown-600">Then try Christian Content Analysis again in a few minutes.</span>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Analysis Results */}
      <AnimatePresence>
        {analysis && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-4"
          >
            {/* Summary Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="bg-brown-50 rounded-lg p-4 text-center">
                <div className="text-2xl font-bold text-brown-800">{analysis.versesAboveThreshold || 0}</div>
                <div className="text-xs text-brown-600 mt-1">Relevant Verses</div>
              </div>
              <div className="bg-gold-50 rounded-lg p-4 text-center">
                <div className="text-2xl font-bold text-gold-800">
                  {((analysis.averageSimilarity || 0) * 100).toFixed(0)}%
                </div>
                <div className="text-xs text-gold-600 mt-1">Avg Similarity</div>
              </div>
              <div className="bg-brown-50 rounded-lg p-4 text-center">
                <div className="text-2xl font-bold text-brown-800">{(analysis.totalVersesAnalyzed || 0).toLocaleString()}</div>
                <div className="text-xs text-brown-600 mt-1">Verses Analyzed</div>
              </div>
              <div className="bg-gold-50 rounded-lg p-4 text-center">
                <div className="text-2xl font-bold text-gold-800">
                  {(analysis.relevantVerses?.length || 0) > 0 ? '✓' : '—'}
                </div>
                <div className="text-xs text-gold-600 mt-1">Matches Found</div>
              </div>
            </div>

            {/* Relevant Verses */}
            {(analysis.relevantVerses?.length || 0) > 0 ? (
              <div className="space-y-3">
                <h4 className="text-lg font-semibold text-brown-800 flex items-center gap-2">
                  <TrendingUp className="w-5 h-5" />
                  Top Matching Verses
                </h4>
                <div className="space-y-3 max-h-96 overflow-y-auto">
                  {(analysis.relevantVerses || []).map((verse, index) => (
                    <VerseCard key={verse.id || `verse-${index}`} verse={verse} rank={index + 1} />
                  ))}
                </div>
              </div>
            ) : (
              <div className="text-center py-8 text-brown-600">
                <AlertCircle className="w-12 h-12 mx-auto mb-3 text-brown-400" />
                <p>No verses found above the similarity threshold.</p>
                <p className="text-sm mt-2">Try lowering the threshold or ensure your website content is analyzed.</p>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function VerseCard({ verse, rank }: { verse: VerseMatch; rank: number }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    // Fallback-aware copy: navigator.clipboard is unavailable on non-HTTPS/some iframes
    const ok = await copyTextToClipboard(`${verse.reference} - ${verse.text}`);
    if (!ok) return;
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      className="bg-gradient-to-br from-brown-50 to-gold-50 rounded-lg p-4 border border-brown-200 hover:shadow-md transition-shadow"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <span className="px-2 py-1 bg-brown-600 text-white text-xs font-bold rounded">
              #{rank}
            </span>
            <span className="font-semibold text-brown-800">{verse.reference}</span>
            <span className="px-2 py-1 bg-gold-200 text-gold-800 text-xs font-medium rounded">
              {verse.similarityPercentage}% match
            </span>
          </div>
          <p className="text-brown-700 text-sm leading-relaxed mb-2">{verse.text}</p>
          <div className="flex items-center gap-2 text-xs text-brown-600">
            <span>{verse.book} {verse.chapter}:{verse.verse}</span>
            {verse.translation && <span>• {verse.translation}</span>}
          </div>
        </div>
        <button
          onClick={handleCopy}
          className="p-2 text-brown-600 hover:bg-brown-100 rounded-lg transition-colors"
          title="Copy verse"
        >
          {copied ? (
            <CheckCircle2 className="w-5 h-5 text-green-600" />
          ) : (
            <Book className="w-5 h-5" />
          )}
        </button>
      </div>
    </motion.div>
  );
}

