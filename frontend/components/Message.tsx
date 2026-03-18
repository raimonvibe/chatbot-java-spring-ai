'use client';

import { motion } from 'framer-motion';
import type { Message as MessageType } from '@/lib/api';
import { Book, User } from 'lucide-react';

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
      <div className={`flex gap-2 max-w-[80%] ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
        {/* Avatar */}
        <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
          isUser ? 'bg-gradient-to-br from-brown-600 to-gold-600' : 'bg-brown-700'
        }`}>
          {isUser ? (
            <User className="w-5 h-5 text-white" strokeWidth={2} />
          ) : (
            <Book className="w-5 h-5 text-white" strokeWidth={2} />
          )}
        </div>

        {/* Message Content */}
        <div
          className={`
            rounded-2xl px-4 py-3 shadow-sm
            ${
              isUser
                ? 'bg-gradient-to-br from-brown-600 to-gold-600 text-white border-2 border-brown-700'
                : 'bg-brown-50 text-brown-900 border-2 border-brown-200'
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
            className={`text-xs mt-1 block ${isUser ? 'text-brown-100' : 'text-brown-500'}`}
          >
            {new Date(message.timestamp).toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </motion.time>
        </div>
      </div>
    </motion.div>
  );
}
