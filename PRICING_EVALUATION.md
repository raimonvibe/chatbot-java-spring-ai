# Pricing & Plan Limits – Cost Evaluation

## Source of truth

**Backend:** `backend/src/main/java/com/prayer_chat/chatbot/config/PlanLimits.java`

- Standard page tiers: `FREE_MAX_PAGES` (50), `BASIC_MAX_PAGES` (500), `PRO_MAX_PAGES` (2,000), `ENTERPRISE_MAX_PAGES` (10,000).
- All limits (max pages, cost cap, scans/month, messages/day) are defined there.
- **Public API:** `GET /api/plans/limits` returns these limits (no auth). Frontend can use this to stay in sync.

## Cost basis (doable?)

Cost model is in `CostTrackingService`:

- Embedding: **$0.10 per 1M tokens** (≈ 2,000 tokens per page → 50 pages ≈ 100k tokens ≈ **$0.01**).
- Scan: **$0.0001 per page** (50 pages ≈ **$0.005**).

| Plan       | Max pages/scan | Est. cost (1 full scan) | Monthly cost cap | Verdict   |
|-----------|----------------|--------------------------|------------------|-----------|
| FREE      | 50             | ~\$0.02                  | \$5              | Very safe |
| BASIC     | 500            | ~\$0.15                  | \$15             | Safe      |
| PRO       | 2,000          | ~\$0.60                  | \$50             | Safe      |
| ENTERPRISE| 10,000         | ~\$3.00                  | \$200            | Safe      |

One full scan at plan max is well under the monthly cap. Plans are **cost-sustainable**. Chat (LLM) costs are separate and not part of this cap.

## Automatic reference to plan

- **Page count → plan:** `PlanLimits.minimumPlanForPages(estimatedPages)` returns the minimum plan needed for a given page count. Used when a user hits the limit (API returns `suggestedPlan` and `suggestedMaxPages`).
- **Enforcement:** Before any website scan or chatbot creation, the backend checks `estimatedPages <= PlanLimits.maxPagesPerScan(userPlan)` and rejects with 403/402 and the suggested upgrade plan.

## Frontend

- Pricing page can call `GET /api/plans/limits` and render plans from the response so it never drifts from the backend.
- If the pricing page keeps hardcoded numbers, they **must** match `PlanLimits` (see table above).
