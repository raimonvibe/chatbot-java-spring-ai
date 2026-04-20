import { timingSafeEqual } from 'crypto';
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';

const COOKIE_NAME = 'oauth_state';

function timingSafeStringEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  try {
    return timingSafeEqual(Buffer.from(a, 'utf8'), Buffer.from(b, 'utf8'));
  } catch {
    return false;
  }
}

function clearOAuthStateCookie(res: NextResponse) {
  res.cookies.set({
    name: COOKIE_NAME,
    value: '',
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 0,
    secure: process.env.NODE_ENV === 'production',
  });
}

export async function POST(request: Request) {
  let urlState = '';
  try {
    const body = (await request.json()) as { state?: unknown };
    urlState = typeof body.state === 'string' ? body.state : '';
  } catch {
    const res = NextResponse.json({ ok: false }, { status: 400 });
    clearOAuthStateCookie(res);
    return res;
  }

  const cookieStore = await cookies();
  const expected = cookieStore.get(COOKIE_NAME)?.value ?? '';
  const ok = expected.length > 0 && urlState.length > 0 && timingSafeStringEqual(expected, urlState);

  const res = ok
    ? NextResponse.json({ ok: true })
    : NextResponse.json({ ok: false }, { status: 403 });

  clearOAuthStateCookie(res);
  return res;
}
