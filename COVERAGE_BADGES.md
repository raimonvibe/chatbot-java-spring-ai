# Coverage Badges Configuration

> Instructions for adding test coverage badges to your README

---

## Option 1: Using Codecov (Recommended)

### Setup

1. Sign up at [codecov.io](https://codecov.io) with your GitHub account
2. Add your repository
3. Add Codecov token to GitHub Secrets (`CODECOV_TOKEN`)

### Update GitHub Actions Workflow

Add to `.github/workflows/ci-cd.yml`:

```yaml
- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v4
  with:
    files: ./backend/target/site/jacoco/jacoco.xml,./frontend/coverage/lcov.info
    flags: unittests
    name: codecov-umbrella
    fail_ci_if_error: false
```

### Add Badge to README

```markdown
[![codecov](https://codecov.io/gh/YOUR_USERNAME/YOUR_REPO/branch/main/graph/badge.svg)](https://codecov.io/gh/YOUR_USERNAME/YOUR_REPO)
```

---

## Option 2: Using Coveralls

### Setup

1. Sign up at [coveralls.io](https://coveralls.io)
2. Add repository
3. Add `COVERALLS_REPO_TOKEN` to GitHub Secrets

### Update GitHub Actions Workflow

```yaml
- name: Coveralls
  uses: coverallsapp/github-action@v2
  with:
    github-token: ${{ secrets.GITHUB_TOKEN }}
    path-to-lcov: ./frontend/coverage/lcov.info
```

### Add Badge to README

```markdown
[![Coverage Status](https://coveralls.io/repos/github/YOUR_USERNAME/YOUR_REPO/badge.svg?branch=main)](https://coveralls.io/github/YOUR_USERNAME/YOUR_REPO?branch=main)
```

---

## Option 3: Using Shields.io (Manual)

### Generate Badge URLs

#### Backend Coverage

```markdown
![Backend Coverage](https://img.shields.io/badge/backend%20coverage-70%25-green)
```

#### Frontend Coverage

```markdown
![Frontend Coverage](https://img.shields.io/badge/frontend%20coverage-80%25-green)
```

**Note**: Update percentages manually after each release.

---

## Option 4: Using GitHub Actions Artifacts

### Add to Workflow

```yaml
- name: Generate coverage badge
  uses: cicirello/jacoco-badge-generator@v2
  with:
    jacoco-csv-file: backend/target/site/jacoco/jacoco.csv
    badges-directory: .github/badges
    generate-branches-badge: true
    generate-summary: true

- name: Commit badge
  run: |
    git config --local user.email "action@github.com"
    git config --local user.name "GitHub Action"
    git add .github/badges/*.svg
    git commit -m "Update coverage badge" || exit 0
    git push
```

### Add Badge to README

```markdown
![Coverage](.github/badges/jacoco.svg)
```

---

## Recommended README Badges Section

```markdown
## Status

![Build Status](https://github.com/YOUR_USERNAME/YOUR_REPO/workflows/CI%2FCD%20Pipeline/badge.svg)
[![codecov](https://codecov.io/gh/YOUR_USERNAME/YOUR_REPO/branch/main/graph/badge.svg)](https://codecov.io/gh/YOUR_USERNAME/YOUR_REPO)
![Backend Coverage](https://img.shields.io/badge/backend%20coverage-70%25-green)
![Frontend Coverage](https://img.shields.io/badge/frontend%20coverage-80%25-green)
![Java Version](https://img.shields.io/badge/java-21-blue)
![Node Version](https://img.shields.io/badge/node-20-green)
![License](https://img.shields.io/badge/license-MIT-blue)
```

---

## Testing Status in README

Add this section to your README:

```markdown
## Testing

- **Backend Coverage**: 70%+ (JaCoCo)
- **Frontend Coverage**: 80%+ (Jest/Istanbul)
- **E2E Tests**: Playwright (120+ scenarios)
- **Total Tests**: ~250 tests
- **CI/CD**: Automated testing on all PRs

### Running Tests

```bash
# Backend
cd backend && mvn test

# Frontend
cd frontend && npm test

# E2E
cd frontend && npx playwright test
```

See [TESTING_GUIDE.md](./TESTING_GUIDE.md) for detailed instructions.
```

---

## Coverage Report Links

If you host coverage reports online:

```markdown
- [Backend Coverage Report](https://your-site.com/backend/coverage)
- [Frontend Coverage Report](https://your-site.com/frontend/coverage)
- [E2E Test Results](https://your-site.com/e2e/report)
```

---

## Auto-Update Coverage Badges

### Using GitHub Actions

Create `.github/workflows/coverage-badge.yml`:

```yaml
name: Update Coverage Badge

on:
  push:
    branches: [main]
  workflow_run:
    workflows: ["CI/CD Pipeline"]
    types:
      - completed

jobs:
  update-badge:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6

      - name: Download coverage artifacts
        uses: actions/download-artifact@v4
        with:
          name: backend-coverage-report

      - name: Parse coverage percentage
        id: coverage
        run: |
          COVERAGE=$(grep -oP '(?<=Total.*?)\d+(?=%)' target/site/jacoco/index.html || echo "0")
          echo "coverage=$COVERAGE" >> $GITHUB_OUTPUT

      - name: Create badge
        uses: schneegans/dynamic-badges-action@v1.7.0
        with:
          auth: ${{ secrets.GIST_SECRET }}
          gistID: YOUR_GIST_ID
          filename: coverage-badge.json
          label: coverage
          message: ${{ steps.coverage.outputs.coverage }}%
          color: green
```

---

## Best Practices

1. ✅ **Update badges automatically** - Don't manually update percentages
2. ✅ **Use Codecov or Coveralls** - Professional solution for coverage tracking
3. ✅ **Link badges to reports** - Make badges clickable
4. ✅ **Show trend graphs** - Codecov shows coverage trends over time
5. ✅ **Set coverage thresholds** - Fail CI if coverage drops below threshold

---

**Last Updated:** 2025-12-18
