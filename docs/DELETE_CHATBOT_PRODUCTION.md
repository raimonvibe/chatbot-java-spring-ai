# Delete Chatbot Button (Production Safety)

## Why it exists
The dashboard has a **Delete chatbot** button for testing. Deleting a chatbot can also remove stored conversations/messages that belong to that chatbot. Because your usage limits are computed from stored messages and scans, allowing deletes in a live environment lets a user “reset” their usage by deleting content.

## What happens in production
When Stripe/billing is enabled (`app.billing.enabled=true` on the backend):
- The backend rejects:
  - `DELETE /api/chatbots/{id}` (single delete)
  - `DELETE /api/chatbots` (delete all)
- The dashboard UI hides the delete button(s) as well.

So: **the delete controls are effectively gone in production**, even if someone manually calls the API.

## What happens in testing / preview mode
When billing is disabled (`app.billing.enabled=false`), deletion is allowed for your testing workflows.

### Preview message quota (common expectation)
When billing is enabled and a user is on the **FREE** plan (preview):
- **Preview max messages/day = 10**

This comes from `PlanLimits.messagesPerDay(FREE)`.

### Note about the current free-product deployment
In the current “free product” mode (billing disabled), the effective caps are:
- **Messages/day = 30**
- **Scans/month = 3**

This is configured in `BillingModeService` for cost control during free rollout.

## Implementation (so future you remembers)
- Backend guardrail: `ChatbotController` now returns `403 FORBIDDEN` for delete endpoints when billing is enabled.
- Frontend guardrail: dashboard/header show delete UI only when billing is disabled.

