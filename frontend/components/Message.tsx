'use client';

import { motion } from 'framer-motion';
import type { Message as MessageType } from '@/lib/api';

interface MessageProps {
  message: MessageType;
  index: number;
}

export default function Message({ message, index }: MessageProps) {
  const isUser = message.role === 'user';

  return (
    <motion.div
      initial={{ opacity: 0, y: 20, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{
        duration: 0.3,
        delay: index * 0.05,
        ease: 'easeOut',
      }}
      className={`flex w-full ${isUser ? 'justify-end' : 'justify-start'} mb-4`}
    >
      <div
        className={`
          max-w-[80%] rounded-2xl px-4 py-3 shadow-md
          ${
            isUser
              ? 'bg-gradient-to-br from-blue-500 to-purple-600 text-white'
              : 'bg-white text-gray-800 border border-gray-100'
          }
        `}
      >
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.3, delay: 0.1 }}
          className="text-sm md:text-base whitespace-pre-wrap break-words"
        >
          {message.content}
        </motion.p>
        <motion.time
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.3, delay: 0.2 }}
          className={`text-xs mt-1 block ${isUser ? 'text-blue-100' : 'text-gray-400'}`}
        >
          {new Date(message.timestamp).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </motion.time>
      </div>
    </motion.div>
  );
}
