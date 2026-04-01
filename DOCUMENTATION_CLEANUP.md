# Documentation Cleanup Plan

**Purpose:** Reduce doc sprawl while keeping useful documentation. Many files are one-off fix logs, completed plans, or redundant guides.

**Done:**  
- **Phase 1:** 41 files moved to `docs/archive/` (plans, security-reviews, fix-logs, testing, deployment, backend, frontend).  
- **Phase 2:** Merged OAuth/Stripe → **docs/OAUTH_AND_STRIPE_SETUP.md**; Session → **docs/SESSION_AND_AUTH.md**; Testing → single **TESTING.md**. Moved merged originals and optional docs (embeddings, Spring AI upgrade, cost, reverse proxy, etc.) to **docs/archive/oauth-stripe**, **docs/archive/session**, **docs/archive/testing**, **docs/archive/misc**. README updated with Documentation section.

---

## Summary

| Action | Count | Effect |
|--------|-------|--------|
| **Keep** (as-is or light edit) | ~15 | Core docs stay in root |
| **Merge** into fewer docs | ~25 → 5 | One OAuth/Stripe setup, one Testing, one Security |
| **Archive** (move to `docs/archive/`) | ~45 | Historical plans, phase reviews, fix logs |
| **Remove** (optional) | ~5 | Duplicate or fully superseded |

---

## 1. KEEP (essential)

Keep these in the project root:

| File | Why |
|------|-----|
| **README.md** | Main entry point; link to other docs here |
| **GETTING_STARTED.md** | First-run guide (or merge into README) |
| **LOCAL_DEVELOPMENT.md** | How to run locally |
| **DEPLOYMENT.md** | Main deployment guide (single source) |
| **APPLICATION_ARCHITECTURE_OVERVIEW.md** | Architecture reference |
| **SYSTEM_OVERVIEW.md** | High-level system (could merge into README) |
| **SECURE_CODING_GUIDELINES.md** | Coding standards |
| **SECURITY_PLAN.md** | Security approach (keep one) |
| **STRIPE_SAFE_SETUP.md** | Stripe + subscription setup |
| **BIBLE_DATA_SETUP.md** | Bible data if still used |
| **ABUSE_PROTECTION.md** | Abuse protection behavior |
| **RENDER_ENV_VARIABLES.md** | Render env reference |
| **VERCEL_ENV_SETUP.md** | Frontend env reference |
| **JWT_TOKEN_SECURITY.md** | Auth security |
| **INCIDENT_RESPONSE.md** | Incident process |
| **API_KEY_ROTATION.md** | Key rotation process |

---

## 2. MERGE (consolidate into fewer files)

### 2.1 Testing → one **TESTING.md**

- **TESTING.md** – keep as main; add “Current status” section from others or drop if outdated.
- **TESTING_GUIDE.md** – merge “how to run” into TESTING.md.
- **TESTING_STRATEGY.md** – keep strategy in TESTING.md or move to `docs/archive/` (long).
- **TESTING_BEST_PRACTICES.md** – merge “best practices” section into TESTING.md.
- **TESTING_IMPLEMENTATION_PLAN.md** → archive (plan, not reference).
- **TESTING_TODO.md** → archive or delete if tasks are done.

**Result:** One **TESTING.md** (how to run, structure, best practices, optional short strategy).

### 2.2 OAuth / Stripe setup → one **docs/OAUTH_AND_STRIPE_SETUP.md**

Merge these into a single setup doc:

- OAUTH_STRIPE_SETUP.md  
- OAUTH_RENDER_SETUP.md  
- OAUTH_CONSENT_SCREEN_FIX.md  
- OAUTH_DOMAIN_EXPLANATION.md  
- OAUTH_APP_NAME_UPDATE.md  
- OAUTH_APP_NAME_QUICK_FIX.md  
- OAUTH2_TROUBLESHOOTING.md  
- OAUTH2_TROUBLESHOOTING_LOCAL.md  
- HYBRID_OAUTH_SETUP.md  
- HYBRID_OAUTH_SECURITY.md  
- GOOGLE_OAUTH_LOGOUT_GUIDE.md  
- IMPLEMENTATION_PLAN_OAUTH_STRIPE.md (extract setup steps only; rest → archive)

**Result:** One **docs/OAUTH_AND_STRIPE_SETUP.md** (setup + troubleshooting).

### 2.3 Deployment status/verification

- **DEPLOYMENT_STATUS.md** – fold “current status” into DEPLOYMENT.md or drop if outdated.
- **DEPLOYMENT_VERIFICATION.md** – same; keep verification steps in DEPLOYMENT.md.

**Result:** Only **DEPLOYMENT.md** for deployment and verification.

### 2.4 Next steps

- **NEXT_STEPS.md** + **NEXT_STEPS_UPDATED.md** – merge into one “Next steps” section in README or a short **NEXT_STEPS.md**; archive the other.

### 2.5 Session / auth security

- **SESSION_TIMEOUT_EXPLANATION.md**  
- **SESSION_TIMEOUT_BEST_PRACTICES.md**  
- **SESSION_SECURITY_ANALYSIS.md**  

**Result:** One **docs/SESSION_AND_AUTH.md** (or a “Session & auth” section in SECURITY_PLAN.md).

---

## 3. ARCHIVE (move to `docs/archive/`)

Move these to **docs/archive/** so they’re out of the way but still available. They are historical plans, phase reviews, or one-off fix logs.

### Root – plans and status

- ACTION_PLAN.md  
- DEVELOPMENT_PLAN.md  
- POST_CHRISTMAS_PLAN.md  
- IMPLEMENTATION_STATUS.md  
- PLAN_JESUS_TEACHINGS_FEATURE.md  
- PRICING_PLAN.md  
- MEMORY_OPTIMIZATION_PLAN.md  
- LONG_TERM_SOLUTION.md  
- ALTERNATIVE_OPTIONS.md  
- MIGRATION_TO_CLAUDE.md  
- PREVIEW_COST_STRATEGY.md  

### Root – next steps (if not merged)

- NEXT_STEPS.md  
- NEXT_STEPS_UPDATED.md  

### Root – deployment status (after merging into DEPLOYMENT.md)

- DEPLOYMENT_STATUS.md  
- DEPLOYMENT_VERIFICATION.md  
- PRODUCTION_URL_VERIFICATION.md  

### Root – security reviews (historical)

- SECURITY_REVIEW_PHASE1.md  
- SECURITY_REVIEW_PHASE2.1.md  
- SECURITY_REVIEW_PHASE2.2.md  
- SECURITY_REVIEW_PRODUCTION_URLS.md  
- PHASE3_SECURITY_REVIEW.md  
- SECURITY_ANALYSIS.md  
- SECURITY_TESTING.md  
- SECURITY_FIX_DELETE_ABUSE.md  
- SECURITY_AUDIT_PLAN.md  
- TEST_SECURITY_ANALYSIS.md  
- WEBSITE_SCANNING_SECURITY_STATUS.md  

### Root – one-off fixes / troubleshooting (done)

- GIT_EMAIL_FIX.md  
- ENV_LOADING_FIX.md  
- OAUTH_APP_NAME_QUICK_FIX.md  
- OAUTH_APP_NAME_UPDATE.md  
- OAUTH_CONSENT_SCREEN_FIX.md  
- GITHUB_ACTIONS_FIX.md  
- WORKFLOW_TRIGGER_CHECK.md  

### Root – E2E / test analysis (historical)

- E2E_TEST_PROBLEM_ANALYSIS.md  
- E2E_TEST_PROBLEM_SUMMARY.md  
- E2E_AUTHENTICATION_ISSUES.md  
- TEST_ANALYSIS.md  
- TEST_FIX_PLAN.md  

### Root – other

- REVERSE_PROXY_SETUP.md → keep or merge into DEPLOYMENT.md; else archive.  
- QUICK_START_REVERSE_PROXY.md → merge into DEPLOYMENT or archive.  
- RENDER_POSTGRESQL_SESSION_SETUP.md → merge into DEPLOYMENT or archive.  
- COVERAGE_BADGES.md → keep if you use badges; else archive.  
- COST_COMPARISON.md, AWS_COST_ANALYSIS.md → archive (reference).  
- EMBEDDING_IMPORT_SETUP_TODAY.md, EMBEDDINGS_IMPORT_RENDER.md, EMBEDDINGS_FILE_LOCATION.md, UPLOAD_EMBEDDINGS_STAP_VOOR_STAP.md, QUICK_IMPORT_GUIDE.md, COLAB_EMBEDDINGS_GUIDE.md → one **docs/EMBEDDINGS_IMPORT.md**; rest archive.  
- SPRING_AI_UPGRADE.md, SPRING_AI_2_UPGRADE.md → archive (migration logs).  
- LOCAL_TESTING_COST_PROTECTION.md, LOCAL_TESTING_SCAN_LIMITS.md → merge into TESTING.md or LOCAL_DEVELOPMENT.md; else archive.  

### Backend

- REST_ASSURED_BUG_ANALYSIS.md  
- REST_ASSURED_GET_NPE_FIXES.md  
- REST_ASSURED_GET_NPE_RESEARCH.md  
- DEBUG_FINDINGS.md  
- AFFECTED_TESTS_ANALYSIS.md  
- BACKEND_TEST_FIXES.md  
- TEST_FIXES_SUMMARY.md  
- TEST_DOCUMENTATION.md (merge useful parts into root TESTING.md; then archive)  
- TEST_RESULTS.md  
- WEBTESTCLIENT_MIGRATION_PLAN.md  
- WEBTESTCLIENT_DISADVANTAGES.md  
- JACKSON_2X_DOWNGRADE.md  
- RATE_LIMIT_LOG_EXPLANATION.md  
- NEXT_STEPS.md  
- IMPLEMENTATION_PLAN.md  

### Frontend

- E2E_OAUTH_TEST_FIXES.md  
- TEST_RESULTS.md (or keep one “current status” in root TESTING.md)  
- INSTALL_BROWSER_DEPS.md → keep if needed for E2E; else archive.  
- TESTING.md → merge into root TESTING.md; then archive.  

---

## 4. REMOVE (optional)

Only after merge/archive; delete if content is fully superseded or duplicate:

- Duplicate of another doc (e.g. second NEXT_STEPS after merge).  
- Purely historical one-pager with no future reference (e.g. some fix logs).  

Recommendation: **prefer archive over delete** so nothing is lost.

---

## 5. Suggested structure after cleanup

```
/
├── README.md                    # Main entry; links to docs
├── GETTING_STARTED.md           # First run
├── LOCAL_DEVELOPMENT.md         # Local run
├── DEPLOYMENT.md                # Deploy + verification
├── TESTING.md                   # How to run tests + practices
├── APPLICATION_ARCHITECTURE_OVERVIEW.md
├── SYSTEM_OVERVIEW.md
├── SECURITY_PLAN.md
├── SECURE_CODING_GUIDELINES.md
├── STRIPE_SAFE_SETUP.md
├── BIBLE_DATA_SETUP.md
├── ABUSE_PROTECTION.md
├── JWT_TOKEN_SECURITY.md
├── INCIDENT_RESPONSE.md
├── API_KEY_ROTATION.md
├── RENDER_ENV_VARIABLES.md
├── VERCEL_ENV_SETUP.md
├── docs/
│   ├── OAUTH_AND_STRIPE_SETUP.md   # Merged OAuth + Stripe setup
│   ├── SESSION_AND_AUTH.md         # Session timeout + auth security
│   ├── EMBEDDINGS_IMPORT.md        # Single embeddings import guide
│   └── archive/                    # All archived .md files
│       ├── plans/
│       ├── security-reviews/
│       ├── fix-logs/
│       └── ...
├── backend/
│   └── (no .md except optional README)
└── frontend/
    └── README.md
```

---

## 6. How to apply

1. **Create archive:**  
   `mkdir -p docs/archive/plans docs/archive/security-reviews docs/archive/fix-logs docs/archive/testing docs/archive/backend docs/archive/frontend`

2. **Move files:**  
   Move each file listed in §3 to the appropriate `docs/archive/` subfolder (e.g. plans, security-reviews, fix-logs, backend, frontend).

3. **Merge:**  
   - Create **docs/OAUTH_AND_STRIPE_SETUP.md** and merge OAuth/Stripe setup docs; then archive originals.  
   - Merge testing docs into **TESTING.md**; archive the rest.  
   - Fold deployment status/verification into **DEPLOYMENT.md**; archive status/verification files.  
   - Optionally add **docs/SESSION_AND_AUTH.md** and **docs/EMBEDDINGS_IMPORT.md** as above.

4. **Update README:**  
   - Add a “Documentation” section with links to TESTING.md, DEPLOYMENT.md, OAUTH_AND_STRIPE_SETUP.md, SECURITY_PLAN.md, etc.  
   - Remove or fix links to archived docs (e.g. link to `docs/archive/` if you want to keep them reachable).

5. **Optional:**  
   - Add a short **docs/README.md** describing `docs/` and that `archive/` is historical.

If you want, the next step can be: (1) creating `docs/archive` and moving the archive list, and (2) adding a single merged **TESTING.md** and **docs/OAUTH_AND_STRIPE_SETUP.md** skeleton so you can paste in content.
