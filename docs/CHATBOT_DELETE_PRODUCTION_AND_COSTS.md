# Chatbot delete in production and cost considerations

## Is delete safe in production?

**Yes.** Keeping the chatbot delete feature in production does **not** interfere with:

- **Subscriptions** – Stripe subscription is tied to the **user** (customer), not to individual chatbots. Deleting a chatbot does not cancel or change the user’s plan.
- **Service behavior** – Deleting a chatbot only removes that chatbot’s data (DB rows and vector store documents). Other users and their chatbots are unaffected.
- **Plan limits** – `PlanLimits.maxChatbots(plan)` defines how many chatbots a user can have. Deleting one frees a slot so they can create a new one within their plan.

So the flow “delete chatbot → create new chatbot” is valid for production: users can replace their single chatbot (e.g. change website or start fresh) without losing their subscription.

---

## Why delete was added (testing vs production)

- **Testing** – Delete (and bulk delete for preview users) lets you stay under the “max chatbots” limit without creating new accounts, so you can test create/delete/analyze cycles.
- **Production** – The same behavior is useful for real users: they can delete their chatbot and create a new one (e.g. new site, wrong URL, or hitting the one-chatbot-per-account limit). So it’s appropriate to keep delete in production, not only for testing.

---

## Cost implications of delete + create

Each time a user **creates a new chatbot and runs “Analyze website”**, you incur:

- Crawl (and optional headless) work
- Embedding API usage (e.g. Cohere)
- Storage (DB + vector store)

So **repeated delete → create → analyze** cycles can increase cost if unchecked. Your existing limits already protect you:

| Control | Role |
|--------|------|
| **Daily scan limit** | `PlanLimits.dailyScanLimit(plan)` – e.g. FREE = 1/day, BASIC = 3/day. Caps how often they can run analysis in a day. |
| **Monthly scan quota** | `PlanLimits.monthlyScanQuota(plan)` – e.g. FREE = 1/month, BASIC = 5/month. Caps total analyses per month. |
| **Monthly cost cap** | `CostTrackingService` / `PlanLimits.monthlyCostCapUsd(plan)` – hard cap on estimated scan + embedding cost per user per month. |
| **WebsiteScanAudit** | Each “Analyze website” is audited and counted for limits; audit rows are **not** deleted when a chatbot is deleted, so abuse via delete/recreate cannot bypass scan limits. |

So even if users delete and recreate often, they still consume their **daily** and **monthly** scan quota and stay under the **monthly cost cap**. You don’t need to remove delete for cost reasons as long as these limits stay in place.

---

## Recommendations

1. **Keep delete in production** – Safe for subscriptions and service; useful for users who want to replace their chatbot.
2. **Keep bulk delete restricted** – Already limited to preview-mode users; paid users use single delete only. No change needed.
3. **Rely on existing cost controls** – Daily/monthly scan limits and cost cap already limit the cost of delete → create → analyze cycles.
4. **Optional UX** – In the UI, you can mention that “Analyze website” uses their monthly scan quota (and that deleting and re-analyzing counts as a new scan), so power users understand the trade-off.

No code changes are required for production safety; this doc is for clarity and future reference.
