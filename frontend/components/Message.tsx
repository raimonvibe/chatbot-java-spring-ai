'use client';

import { motion } from 'framer-motion';
import type { Message as MessageType } from '@/lib/api';
import { Book, User } from 'lucide-react';

interface MessageProps {
  message: MessageType;
  index: number;
  primaryColor?: string;
  secondaryColor?: string;
  assistantAvatarId?: string | null;
}

function hexToRgba(hex: string, alpha: number): string {
  const normalized = hex.trim().replace('#', '');
  const full = normalized.length === 3
    ? normalized.split('').map((ch) => ch + ch).join('')
    : normalized;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return `rgba(139, 69, 19, ${alpha})`;
  const n = parseInt(full, 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export default function Message({
  message,
  index,
  primaryColor = '#8B5E34',
  secondaryColor = '#E8DCC4',
  assistantAvatarId,
}: MessageProps) {
  const isUser = message.role === 'user';
  const validAssistantAvatar =
    !!assistantAvatarId && /^(?:[1-9]|1[0-2])$/.test(assistantAvatarId);

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
        <div
          className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center overflow-hidden"
          style={{ backgroundColor: isUser ? primaryColor : '#6B4F3A' }}
        >
          {isUser ? (
            <User className="w-5 h-5 text-white" strokeWidth={2} />
          ) : validAssistantAvatar ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={`/${assistantAvatarId}.png`} alt="" role="presentation" className="w-full h-full object-cover" />
          ) : (
            <Book className="w-5 h-5 text-white" strokeWidth={2} />
          )}
        </div>

        {/* Message Content */}
        <div
          className="rounded-2xl px-4 py-3 shadow-sm border-2"
          style={{
            background: isUser ? primaryColor : hexToRgba(secondaryColor, 0.22),
            color: isUser ? '#FFFFFF' : '#2F241A',
            borderColor: isUser ? hexToRgba(primaryColor, 0.75) : hexToRgba(secondaryColor, 0.6),
          }}
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
            className="text-xs mt-1 block"
            style={{ color: isUser ? 'rgba(255,255,255,0.85)' : '#6B4F3A' }}
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
