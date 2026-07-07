import { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-gradient-to-r from-brown-600 to-gold-600 text-white hover:from-brown-700 hover:to-gold-700',
  secondary:
    'border border-brown-300 bg-white/90 text-brown-800 hover:bg-brown-50',
  ghost: 'text-brown-700 hover:bg-brown-50/80',
  danger: 'border border-red-200 bg-red-50 text-red-700 hover:bg-red-100',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  children: ReactNode;
}

export default function Button({
  variant = 'primary',
  className = '',
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${variantClasses[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
