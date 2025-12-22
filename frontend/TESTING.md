# Frontend Testing Guide

This document explains how to run frontend tests in manageable parts.

## Test Structure

The frontend tests are organized into the following categories:

- **Unit Tests** (`components/__tests__/`, `lib/__tests__/`): Jest tests for individual components and utilities
- **E2E Tests** (`e2e/`): Playwright tests organized by:
  - `pages/`: Page-level tests (7 files)
  - `flows/`: User journey tests (3 files)
  - `components/`: Component integration tests (2 files)
  - `mobile-responsiveness.spec.ts`: Mobile-specific tests

## Running Tests

### Unit Tests (Jest)

```bash
# Run all unit tests in watch mode
npm test

# Run all unit tests once (CI mode)
npm run test:ci

# Run with coverage
npm run test:coverage
```

### E2E Tests (Playwright)

#### Run All E2E Tests
```bash
npm run test:e2e
```

#### Run Tests by Category

**Page Tests** (7 test files):
```bash
npm run test:e2e:pages
```
Tests: home, login, dashboard, pricing, chatbot-detail, chatbot-preview

**Flow Tests** (3 test files):
```bash
npm run test:e2e:flows
```
Tests: new-user-flow, create-chatbot-flow, subscription-upgrade-flow

**Component Tests** (2 test files):
```bash
npm run test:e2e:components
```
Tests: navigation-integration, chat-interface-integration

**Mobile Tests**:
```bash
npm run test:e2e:mobile
```

#### Run Tests by Feature

**Critical Path** (Home, Login, Dashboard):
```bash
npm run test:e2e:critical
```

**Authentication Flow**:
```bash
npm run test:e2e:auth
```

**Chatbot Features**:
```bash
npm run test:e2e:chatbot
```

**Subscription Features**:
```bash
npm run test:e2e:subscription
```

**UI Components**:
```bash
npm run test:e2e:ui
```

### Run Specific Test Files

You can also run individual test files directly:

```bash
# Single file
npx playwright test e2e/pages/home.spec.ts

# Multiple files
npx playwright test e2e/pages/home.spec.ts e2e/pages/login.spec.ts

# By pattern
npx playwright test e2e/pages/*.spec.ts
```

### Run Tests in Specific Browser

```bash
# Chromium only (fastest)
npx playwright test --project=chromium

# Firefox only
npx playwright test --project=firefox

# WebKit only
npx playwright test --project=webkit
```

### Run Tests with Filters

```bash
# Run only tests matching a pattern
npx playwright test --grep "should load"

# Run tests excluding a pattern
npx playwright test --grep-invert "mobile"
```

## Test Execution Strategy

### For Development
1. Run unit tests in watch mode: `npm test`
2. Run specific E2E test category as needed: `npm run test:e2e:pages`

### For CI/CD
1. Run all unit tests: `npm run test:ci`
2. Run E2E tests in parallel by category:
   ```bash
   npm run test:e2e:pages &
   npm run test:e2e:flows &
   npm run test:e2e:components &
   wait
   ```

### For Quick Validation
Run critical path tests: `npm run test:e2e:critical`

## Performance Tips

1. **Run tests in parallel**: Tests are automatically parallelized by default
2. **Use specific browsers**: Run only Chromium for faster feedback during development
3. **Run by category**: Instead of all tests, run specific categories
4. **Use grep filters**: Run only tests matching specific patterns

## Viewing Test Reports

After running tests, view the HTML report:

```bash
npm run test:e2e:report
```

This opens the Playwright HTML report in your browser showing:
- Test results
- Screenshots on failure
- Videos on failure
- Traces for debugging

## Troubleshooting

### Tests are slow
- Run tests by category instead of all at once
- Use `--project=chromium` to test only one browser
- Reduce `workers` in `playwright.config.ts` if system is overwhelmed

### Tests are flaky
- Increase timeouts in `playwright.config.ts`
- Check if the dev server is running properly
- Ensure backend is running for E2E tests

### Out of memory
- Reduce `workers` count in `playwright.config.ts`
- Run tests in smaller batches
- Close other applications

