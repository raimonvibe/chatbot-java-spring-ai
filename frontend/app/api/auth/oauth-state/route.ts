import { randomBytes } from 'crypto';
import { NextResponse } from 'next/server';

const COOKIE_NAME = 'oauth_state';
const STATE_MAX_AGE_SEC = 600;

export async function POST() {
  const state = randomBytes(32).toString('hex');
  const res = NextResponse.json({ state });
  res.cookies.set({
    name: COOKIE_NAME,
    value: state,
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: STATE_MAX_AGE_SEC,
    secure: process.env.NODE_ENV === 'production',
  });
  return res;
}
