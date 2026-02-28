'use client';

import { motion, AnimatePresence } from 'framer-motion';
import { Book, Sparkles, Zap, Brain, CheckCircle } from 'lucide-react';
import { useState, useEffect } from 'react';
import { PulseLoader, BarLoader } from 'react-spinners';
import Lottie from 'lottie-react';

interface ChatbotCreationLoaderProps {
  isVisible: boolean;
  chatbotName?: string;
  isScanningWebsite?: boolean; // For longer processes like website scanning
  /** 'analysis' = fancy full-screen shown on chatbot preview while website is being analyzed */
  mode?: 'creating' | 'analysis';
}

const creatingSteps = [
  { icon: Sparkles, text: 'Initializing your chatbot...', color: 'text-gold-600' },
  { icon: Brain, text: 'Training AI with your content...', color: 'text-brown-600' },
  { icon: Zap, text: 'Optimizing responses...', color: 'text-gold-600' },
  { icon: Book, text: 'Finalizing your chatbot...', color: 'text-brown-600' },
];

const analysisSteps = [
  { icon: Sparkles, text: 'Discovering pages on your website...', color: 'text-gold-600' },
  { icon: Brain, text: 'Reading and understanding your content...', color: 'text-brown-600' },
  { icon: Zap, text: 'Building knowledge for your chatbot...', color: 'text-gold-600' },
  { icon: Book, text: 'Almost ready! Preparing to answer questions...', color: 'text-brown-600' },
];

export default function ChatbotCreationLoader({ isVisible, chatbotName, isScanningWebsite = false, mode = 'creating' }: ChatbotCreationLoaderProps) {
  const [currentStep, setCurrentStep] = useState(0);
  const loadingSteps = mode === 'analysis' ? analysisSteps : creatingSteps;
  const isAnalysis = mode === 'analysis';

  useEffect(() => {
    if (!isVisible) {
      setCurrentStep(0);
      return;
    }

    // Animate loading steps (slow enough for visitors to read each line)
    const stepInterval = setInterval(() => {
      setCurrentStep((prev) => (prev + 1) % loadingSteps.length);
    }, 5000);

    return () => {
      clearInterval(stepInterval);
    };
  }, [isVisible, loadingSteps.length]);

  if (!isVisible) return null;

  const CurrentIcon = loadingSteps[currentStep].icon;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 bg-gradient-to-br from-brown-900/95 via-brown-800/95 to-gold-900/95 backdrop-blur-md z-50 flex items-center justify-center"
      >
        <div className="relative w-full max-w-2xl mx-auto px-8">
          {/* Animated background circles */}
          <div className="absolute inset-0 overflow-hidden">
            <motion.div
              className="absolute top-1/4 left-1/4 w-64 h-64 bg-gold-500/20 rounded-full blur-3xl"
              animate={{
                scale: [1, 1.2, 1],
                opacity: [0.3, 0.5, 0.3],
              }}
              transition={{
                duration: 3,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
            />
            <motion.div
              className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-brown-500/20 rounded-full blur-3xl"
              animate={{
                scale: [1.2, 1, 1.2],
                opacity: [0.3, 0.5, 0.3],
              }}
              transition={{
                duration: 4,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
            />
          </div>

          {/* Main content */}
          <div className="relative z-10 text-center">
            {/* Title - opacity only so layout stays fixed */}
            <motion.h2
              key="title"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-4xl md:text-5xl font-bold text-white mb-4"
            >
              {isAnalysis ? 'Setting up your chatbot' : 'Creating Your Chatbot'}
            </motion.h2>

            {/* Subtitle for analysis mode */}
            {isAnalysis && (
              <motion.p
                key="analysis-subtitle"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="text-lg text-brown-200 mb-6 max-w-md mx-auto"
              >
                Analyzing your website so I can answer questions about it. This may take a minute.
              </motion.p>
            )}

            {/* Chatbot name - sanitized to prevent XSS */}
            {chatbotName && (
              <motion.p
                key={`name-${chatbotName}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="text-xl text-gold-300 mb-8 font-semibold"
              >
                &quot;{chatbotName.replace(/[<>"']/g, '')}&quot;
              </motion.p>
            )}

            {/* Current step with icon - opacity only to avoid layout jump */}
            <motion.div
              key={`step-${currentStep}`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="flex flex-col items-center gap-4 mb-8 min-h-[5rem]"
            >
              <div>
                <CurrentIcon className={`w-12 h-12 ${loadingSteps[currentStep].color}`} strokeWidth={2} />
              </div>
              <div className="flex items-center gap-3">
                <PulseLoader 
                  color="#d4af37" 
                  size={6} 
                  speedMultiplier={0.8}
                  margin={2}
                />
                <p className="text-xl text-brown-100 font-medium">
                  {loadingSteps[currentStep].text}
                </p>
              </div>
            </motion.div>

            {/* Progress bar with react-spinners BarLoader */}
            <div className="w-full mb-6">
              <BarLoader
                color="#d4af37"
                height={4}
                width="100%"
                speedMultiplier={0.6}
                cssOverride={{
                  borderRadius: '9999px',
                  background: 'rgba(139, 69, 19, 0.5)',
                }}
              />
            </div>

            {/* Static tip - no rotation, no movement, stays fixed */}
            <div className="text-sm text-brown-300 italic min-h-[1.5rem]">
              {isAnalysis
                ? "This may take a minute. You'll be able to ask questions about your site when it's ready."
                : "Please wait while your chatbot is being prepared."}
            </div>

            {/* Floating particles */}
            <div className="absolute inset-0 pointer-events-none overflow-hidden">
              {[...Array(12)].map((_, i) => (
                <motion.div
                  key={`particle-${i}`}
                  className="absolute w-2 h-2 bg-gold-400/40 rounded-full"
                  initial={{
                    x: `${Math.random() * 100}%`,
                    y: '100%',
                    opacity: 0,
                  }}
                  animate={{
                    y: '-5%',
                    opacity: [0, 1, 0],
                  }}
                  transition={{
                    duration: Math.random() * 3 + 2,
                    repeat: Infinity,
                    delay: Math.random() * 2,
                    ease: 'linear',
                  }}
                />
              ))}
            </div>
          </div>
        </div>
      </motion.div>
    </AnimatePresence>
  );
}

