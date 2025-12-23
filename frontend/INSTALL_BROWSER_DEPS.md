# Installing Browser Dependencies for Mobile Safari and WebKit

## Problem
Mobile Safari and WebKit tests fail with:
```
Error: browserType.launch: 
╔══════════════════════════════════════════════════════╗
║ Host system is missing dependencies to run browsers. ║
║ Please install them with the following command:      ║
║                                                      ║
║     sudo npx playwright install-deps                 ║
╚══════════════════════════════════════════════════════╝
```

## Solution

### Option 1: Using Playwright (Recommended)
```bash
sudo npx playwright install-deps
```

### Option 2: Using apt-get (Alternative)
```bash
sudo apt-get update
sudo apt-get install libavif16
```

## Verification

After installation, verify the dependencies are installed:

```bash
# Test Mobile Safari
npm run test:e2e:pages -- --project="Mobile Safari" --grep "should load"

# Test WebKit (Desktop Safari)
npm run test:e2e:pages -- --project=webkit --grep "should load"
```

## Expected Results

After successful installation:
- ✅ Mobile Safari tests should run (95 tests)
- ✅ WebKit (Desktop Safari) tests should run (95 tests)
- ✅ All tests should pass (assuming code is correct)

## Troubleshooting

If tests still fail after installation:
1. Verify the package is installed: `dpkg -l | grep libavif16`
2. Restart your terminal/IDE
3. Reinstall Playwright browsers: `npx playwright install`
4. Check system logs: `journalctl -xe` (for system-level issues)

