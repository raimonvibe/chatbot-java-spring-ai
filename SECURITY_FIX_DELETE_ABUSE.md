# Security Fix: Prevent Abuse via Chatbot Deletion

## Problem
Hackers could bypass scan frequency limits (1 scan/day for preview mode) by:
1. Creating a chatbot
2. Scanning a website (1 scan counted)
3. Deleting the chatbot (scan history deleted via cascade delete)
4. Creating a new chatbot
5. Scanning again (limit check sees no scans because history was deleted!)

## Root Cause
The `Chatbot` entity had `cascade = CascadeType.ALL` on `WebsiteContent`, meaning when a chatbot was deleted, all scan history was also deleted. This allowed users to reset their scan count by deleting and recreating chatbots.

## Solution
Created a separate `WebsiteScanAudit` entity that:
- **Tracks scans independently of chatbots** - not cascade-deleted
- **Persists scan history** even when chatbots are deleted
- **Prevents abuse** by maintaining an immutable audit trail

## Implementation

### 1. New Entity: `WebsiteScanAudit`
- Stores: user, website URL, scan date, estimated pages, estimated cost, chatbot ID (optional reference)
- **No foreign key constraint** on chatbot_id - allows chatbot deletion without cascade
- Indexed for efficient queries on user + date

### 2. Updated Scan Frequency Check
Changed from:
```java
websiteContentRepository.countScansByUserAndDateAfter(user.getId(), oneDayAgo)
```

To:
```java
websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(user.getId(), oneDayAgo)
```

### 3. Audit Entry Creation
An audit entry is created **BEFORE** starting the scan, ensuring:
- Scan is logged even if scan fails
- History persists even if chatbot is deleted
- Cost tracking is accurate

## Security Benefits

1. **Scan frequency limits cannot be bypassed** - deleting chatbots doesn't reset scan count
2. **Cost tracking is accurate** - audit entries persist for monthly cost calculations
3. **Audit trail** - complete history of all scans for monitoring and abuse detection
4. **Max pages limit still enforced** - 50 pages max for preview mode (checked before scan)

## Protection Layers

The system now has multiple layers of protection:

1. **Max pages limit** (50 for preview mode) - prevents scanning large websites
2. **Scan frequency limit** (1 scan/day for preview mode) - **NOW SECURE** via audit table
3. **Cost limit** ($5/month for preview mode) - tracked via audit entries
4. **Chatbot limit** (3 for preview mode temporarily for testing, will be 1 in production) - prevents creating too many chatbots

## Database Migration

The new `website_scan_audits` table will be created automatically by Hibernate on next startup. No manual migration needed.

## Testing

To test the fix:
1. Create a chatbot in preview mode
2. Scan a website (should succeed)
3. Try to scan again immediately (should fail with "Daily scan limit reached")
4. Delete the chatbot
5. Create a new chatbot
6. Try to scan again (should STILL fail - limit persists!)

## Notes

- The `WebsiteContent` table still exists for storing actual website content
- The `WebsiteScanAudit` table is separate and only for tracking scans
- Audit entries are never deleted (except manually by admins for cleanup)
- The chatbot_id in audit is optional - allows tracking which chatbot was used, but doesn't prevent deletion

