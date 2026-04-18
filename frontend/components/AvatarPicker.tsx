'use client';

// Local list of allowed avatar ids. Must stay in sync with backend EmbedSecurity.ALLOWED_AVATAR_IDS
// and frontend lib/api.ts AVATAR_IDS, but we keep it local here so tests that mock '@/lib/api' do not break.
const AVATAR_IDS = [
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  '10',
  '11',
  '12',
] as const;
type AvatarId = (typeof AVATAR_IDS)[number];

const AVATAR_LABELS: Record<string, string> = {
  '1': 'Virgin Mary (Mother of Jesus)',
  '2': 'Saint Joseph',
  '3': 'Jesus Christ',
  '4': 'Queen Esther',
  '5': 'Mary / Saint Monica',
  '6': 'Saint Martin de Porres',
  '7': 'Moses',
  '8': 'Peter',
  '9': 'Gabriel',
  '10': 'Mary Magdalene (Mary M.)',
  '11': 'Ruth',
  '12': 'Deborah',
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
      <div className="grid grid-cols-2 gap-2 min-[420px]:grid-cols-3 sm:grid-cols-3 md:grid-cols-4">
        <button
          type="button"
          onClick={() => onSelect('')}
          disabled={disabled}
          aria-pressed={current === ''}
          aria-label="No avatar"
          className={`flex min-w-0 flex-col items-center gap-1.5 rounded-xl border-2 p-2 transition-colors ${
            current === ''
              ? 'border-brown-600 bg-brown-100 ring-2 ring-brown-400'
              : 'border-brown-200 bg-white hover:border-brown-300 hover:bg-brown-50'
          } ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
        >
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brown-200 text-xs font-medium text-brown-500">
            None
          </span>
          <span className="w-full text-center text-xs leading-snug text-pretty text-brown-600">No avatar</span>
        </button>
        {AVATAR_IDS.map((id) => (
          <button
            key={id}
            type="button"
            onClick={() => onSelect(id)}
            disabled={disabled}
            aria-pressed={current === id}
            aria-label={AVATAR_LABELS[id] ?? `Avatar ${id}`}
            className={`flex min-w-0 flex-col items-center gap-1.5 rounded-xl border-2 p-2 transition-colors ${
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
              className="h-12 w-12 shrink-0 rounded-full border border-brown-200 object-cover"
            />
            <span className="w-full text-center text-[11px] leading-snug text-pretty text-brown-700 sm:text-xs">
              {AVATAR_LABELS[id] ?? id}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
