'use client';

// Local list of allowed avatar ids. Must stay in sync with backend EmbedSecurity.ALLOWED_AVATAR_IDS
// and frontend lib/api.ts AVATAR_IDS, but we keep it local here so tests that mock '@/lib/api' do not break.
const AVATAR_IDS = ['1', '2', '3', '4', '5', '6'] as const;
type AvatarId = (typeof AVATAR_IDS)[number];

const AVATAR_LABELS: Record<string, string> = {
  '1': 'Virgin Mary (Mother of Jesus)',
  '2': 'Saint Joseph',
  '3': 'Jesus Christ',
  '4': 'Queen Esther',
  '5': 'Mary / Saint Monica',
  '6': 'Saint Martin de Porres',
};

interface AvatarPickerProps {
  currentAvatarId: string | null | undefined;
  onSelect: (avatarId: '' | AvatarId) => void;
  disabled?: boolean;
}

export default function AvatarPicker({ currentAvatarId, onSelect, disabled }: AvatarPickerProps) {
  const current = currentAvatarId && AVATAR_IDS.includes(currentAvatarId as AvatarId) ? currentAvatarId : '';

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-brown-700">Chatbot avatar</p>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => onSelect('')}
          disabled={disabled}
          aria-pressed={current === ''}
          aria-label="No avatar"
          className={`flex flex-col items-center gap-1 p-2 rounded-xl border-2 min-w-[72px] transition-colors ${
            current === ''
              ? 'border-brown-600 bg-brown-100 ring-2 ring-brown-400'
              : 'border-brown-200 bg-white hover:border-brown-300 hover:bg-brown-50'
          } ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
        >
          <span className="w-12 h-12 rounded-full bg-brown-200 flex items-center justify-center text-brown-500 text-xs font-medium">
            None
          </span>
          <span className="text-xs text-brown-600">No avatar</span>
        </button>
        {AVATAR_IDS.map((id) => (
          <button
            key={id}
            type="button"
            onClick={() => onSelect(id)}
            disabled={disabled}
            aria-pressed={current === id}
            aria-label={AVATAR_LABELS[id] ?? `Avatar ${id}`}
            className={`flex flex-col items-center gap-1 p-2 rounded-xl border-2 min-w-[72px] transition-colors ${
              current === id
                ? 'border-brown-600 bg-brown-100 ring-2 ring-brown-400'
                : 'border-brown-200 bg-white hover:border-brown-300 hover:bg-brown-50'
            } ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={`/${id}.png`}
              alt=""
              role="presentation"
              className="w-12 h-12 rounded-full object-cover border border-brown-200"
            />
            <span className="text-xs text-brown-600 max-w-[72px] truncate" title={AVATAR_LABELS[id]}>
              {AVATAR_LABELS[id]?.replace(/ \(.*\)$/, '') ?? id}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
