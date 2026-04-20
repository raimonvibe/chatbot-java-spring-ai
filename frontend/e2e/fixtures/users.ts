/**
 * Test user fixtures for E2E tests
 * Provides mock user data for different scenarios
 */

export interface TestUser {
  id: number;
  email: string;
  username: string;
  name: string;
  authProvider: 'LOCAL' | 'GOOGLE';
  password?: string;
  googleId?: string;
}

export interface TestChatbot {
  id: number;
  name: string;
  description: string;
  websiteUrl: string;
  primaryLanguage: string;
  customPrompt: string;
  active: boolean;
  embedCode?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TestSubscription {
  id: number;
  plan: 'FREE' | 'BASIC' | 'PRO';
  status: 'ACTIVE' | 'INACTIVE' | 'CANCELLED' | 'PAST_DUE';
  currentPeriodStart: string;
  currentPeriodEnd: string;
  stripeCustomerId?: string;
  stripeSubscriptionId?: string;
}

/**
 * Default test users
 */
export const testUsers = {
  /** User with local authentication */
  local: {
    id: 1,
    email: 'test@example.com',
    username: 'testuser',
    name: 'Test User',
    authProvider: 'LOCAL',
    password: 'Test1234!',
  } as TestUser,

  /** User with Google authentication */
  google: {
    id: 2,
    email: 'googleuser@gmail.com',
    username: 'googleuser',
    name: 'Google User',
    authProvider: 'GOOGLE',
    googleId: 'google_123456',
  } as TestUser,

  /** Admin user */
  admin: {
    id: 3,
    email: 'admin@example.com',
    username: 'admin',
    name: 'Admin User',
    authProvider: 'LOCAL',
    password: 'Admin1234!',
  } as TestUser,

  /** User with free subscription */
  freeUser: {
    id: 4,
    email: 'free@example.com',
    username: 'freeuser',
    name: 'Free User',
    authProvider: 'LOCAL',
    password: 'Free1234!',
  } as TestUser,

  /** User with basic subscription */
  basicUser: {
    id: 5,
    email: 'basic@example.com',
    username: 'basicuser',
    name: 'Basic User',
    authProvider: 'LOCAL',
    password: 'Basic1234!',
  } as TestUser,

  /** User with pro subscription */
  proUser: {
    id: 6,
    email: 'pro@example.com',
    username: 'prouser',
    name: 'Pro User',
    authProvider: 'LOCAL',
    password: 'Pro1234!',
  } as TestUser,
};

/**
 * Test chatbots
 */
export const testChatbots: TestChatbot[] = [
  {
    id: 1,
    name: 'Customer Support Bot',
    description: 'Helps customers with common questions',
    websiteUrl: 'https://example.com',
    primaryLanguage: 'en',
    customPrompt: 'You are a helpful customer support assistant.',
    active: true,
    embedCode: '<script src="https://example.com/embed/1"></script>',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  },
  {
    id: 2,
    name: 'Sales Assistant',
    description: 'Assists with sales inquiries',
    websiteUrl: 'https://sales.example.com',
    primaryLanguage: 'en',
    customPrompt: 'You are a sales assistant helping potential customers.',
    active: true,
    embedCode: '<script src="https://example.com/embed/2"></script>',
    createdAt: '2025-01-02T00:00:00Z',
    updatedAt: '2025-01-02T00:00:00Z',
  },
  {
    id: 3,
    name: 'Inactive Bot',
    description: 'This bot is inactive',
    websiteUrl: 'https://inactive.example.com',
    primaryLanguage: 'en',
    customPrompt: 'You are a helpful assistant.',
    active: false,
    createdAt: '2025-01-03T00:00:00Z',
    updatedAt: '2025-01-03T00:00:00Z',
  },
];

/**
 * Test subscriptions
 */
export const testSubscriptions: Record<string, TestSubscription> = {
  free: {
    id: 1,
    plan: 'FREE',
    status: 'ACTIVE',
    currentPeriodStart: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString(),
    currentPeriodEnd: new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString(),
  },
  basic: {
    id: 2,
    plan: 'BASIC',
    status: 'ACTIVE',
    currentPeriodStart: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString(),
    currentPeriodEnd: new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString(),
    stripeCustomerId: 'cus_test_basic',
    stripeSubscriptionId: 'sub_test_basic',
  },
  pro: {
    id: 3,
    plan: 'PRO',
    status: 'ACTIVE',
    currentPeriodStart: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString(),
    currentPeriodEnd: new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString(),
    stripeCustomerId: 'cus_test_pro',
    stripeSubscriptionId: 'sub_test_pro',
  },
  pastDue: {
    id: 4,
    plan: 'BASIC',
    status: 'PAST_DUE',
    currentPeriodStart: new Date(Date.now() - 45 * 24 * 60 * 60 * 1000).toISOString(),
    currentPeriodEnd: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString(),
    stripeCustomerId: 'cus_test_past_due',
    stripeSubscriptionId: 'sub_test_past_due',
  },
  cancelled: {
    id: 5,
    plan: 'BASIC',
    status: 'CANCELLED',
    currentPeriodStart: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString(),
    currentPeriodEnd: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
    stripeCustomerId: 'cus_test_cancelled',
    stripeSubscriptionId: 'sub_test_cancelled',
  },
};

/**
 * Generate a cryptographically secure random number for test IDs
 */
function getSecureRandomInt(max: number): number {
  const array = new Uint32Array(1);
  crypto.getRandomValues(array);
  return array[0] % max;
}

/**
 * Generate a random test user
 */
export function generateTestUser(overrides: Partial<TestUser> = {}): TestUser {
  const randomId = getSecureRandomInt(10000);
  return {
    id: randomId,
    email: `test${randomId}@example.com`,
    username: `testuser${randomId}`,
    name: `Test User ${randomId}`,
    authProvider: 'LOCAL',
    password: 'Test1234!',
    ...overrides,
  };
}

/**
 * Generate a random test chatbot
 */
export function generateTestChatbot(overrides: Partial<TestChatbot> = {}): TestChatbot {
  const randomId = getSecureRandomInt(10000);
  const now = new Date().toISOString();
  return {
    id: randomId,
    name: `Test Bot ${randomId}`,
    description: `Test chatbot ${randomId}`,
    websiteUrl: `https://test${randomId}.example.com`,
    primaryLanguage: 'en',
    customPrompt: 'You are a helpful assistant.',
    active: true,
    embedCode: `<script src="https://example.com/embed/${randomId}"></script>`,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}
