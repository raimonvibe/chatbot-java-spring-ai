'use client';

import { motion, AnimatePresence } from 'framer-motion';
import { Book, Sparkles, Zap, Brain, CheckCircle } from 'lucide-react';
import { useState, useEffect } from 'react';

interface ChatbotCreationLoaderProps {
  isVisible: boolean;
  chatbotName?: string;
}

const loadingSteps = [
  { icon: Sparkles, text: 'Initializing your chatbot...', color: 'text-gold-600' },
  { icon: Brain, text: 'Training AI with your content...', color: 'text-brown-600' },
  { icon: Zap, text: 'Optimizing responses...', color: 'text-gold-600' },
  { icon: Book, text: 'Finalizing your chatbot...', color: 'text-brown-600' },
];

export default function ChatbotCreationLoader({ isVisible, chatbotName }: ChatbotCreationLoaderProps) {
  const [currentStep, setCurrentStep] = useState(0);
  const [dots, setDots] = useState('');

  useEffect(() => {
    if (!isVisible) {
      setCurrentStep(0);
      return;
    }

    // Animate loading steps
    const stepInterval = setInterval(() => {
      setCurrentStep((prev) => (prev + 1) % loadingSteps.length);
    }, 2000);

    // Animate dots
    const dotsInterval = setInterval(() => {
      setDots((prev) => {
        if (prev === '...') return '';
        return prev + '.';
      });
    }, 500);

    return () => {
      clearInterval(stepInterval);
      clearInterval(dotsInterval);
    };
  }, [isVisible]);

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
            {/* Spinning book icon */}
            <div className="mb-8 flex justify-center">
              <motion.div
                animate={{
                  rotate: 360,
                  scale: [1, 1.1, 1],
                }}
                transition={{
                  rotate: {
                    duration: 3,
                    repeat: Infinity,
                    ease: 'linear',
                  },
                  scale: {
                    duration: 2,
                    repeat: Infinity,
                    ease: 'easeInOut',
                  },
                }}
                className="relative"
              >
                <div className="absolute inset-0 bg-gold-500/30 rounded-full blur-xl" />
                <Book className="w-24 h-24 text-gold-400 relative z-10" strokeWidth={1.5} />
              </motion.div>
            </div>

            {/* Title */}
            <motion.h2
              key="title"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="text-4xl md:text-5xl font-bold text-white mb-4"
            >
              Creating Your Chatbot
            </motion.h2>

            {/* Chatbot name */}
            {chatbotName && (
              <motion.p
                key={`name-${chatbotName}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="text-xl text-gold-300 mb-8 font-semibold"
              >
                "{chatbotName}"
              </motion.p>
            )}

            {/* Current step with icon */}
            <motion.div
              key={`step-${currentStep}`}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              className="flex flex-col items-center gap-4 mb-8"
            >
              <motion.div
                animate={{
                  rotate: [0, 10, -10, 0],
                }}
                transition={{
                  duration: 2,
                  repeat: Infinity,
                  ease: 'easeInOut',
                }}
              >
                <CurrentIcon className={`w-12 h-12 ${loadingSteps[currentStep].color}`} strokeWidth={2} />
              </motion.div>
              <p className="text-xl text-brown-100 font-medium">
                {loadingSteps[currentStep].text}
                <span className="inline-block w-8 text-left">{dots}</span>
              </p>
            </motion.div>

            {/* Progress bar */}
            <div className="w-full bg-brown-800/50 rounded-full h-2 mb-6 overflow-hidden">
              <motion.div
                className="h-full bg-gradient-to-r from-gold-500 via-gold-400 to-gold-500 rounded-full"
                initial={{ width: '0%' }}
                animate={{
                  width: ['0%', '25%', '50%', '75%', '90%'],
                }}
                transition={{
                  duration: 8,
                  repeat: Infinity,
                  ease: 'easeInOut',
                }}
              />
            </div>

            {/* Fun facts or tips */}
            <motion.div
              key={`tip-${currentStep}`}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="text-sm text-brown-300 italic"
            >
              {currentStep === 0 && "✨ Your chatbot is learning to understand your content..."}
              {currentStep === 1 && "🧠 Processing thousands of words to build knowledge..."}
              {currentStep === 2 && "⚡ Fine-tuning responses for the best user experience..."}
              {currentStep === 3 && "📚 Almost ready! Preparing your chatbot for launch..."}
            </motion.div>

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

