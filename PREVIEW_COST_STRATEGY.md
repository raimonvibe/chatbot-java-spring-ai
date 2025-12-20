# 💰 Preview Mode Cost Strategy

## Problem Statement

**Challenge:**
- Preview mode requires website to be scanned (costs money)
- But we want to allow preview without subscription
- If user enters large website (e.g., AWS with 15,000 pages), we make $5 cost before we can check

**Question:** Can we estimate website size BEFORE scanning to prevent costs?

---

## 🎯 Solution: Pre-Scan Size Estimation

### Strategy: Estimate Before Full Scan

**Two-Phase Approach:**

1. **Phase 1: Quick Size Estimation (No Cost)**
   - Estimate website size using lightweight methods
   - Check if size exceeds limits
   - Block if too large, proceed if acceptable

2. **Phase 2: Full Scan (Only if Size OK)**
   - Only scan if estimated size is within limits
   - Generate embeddings (costs money)
   - Create chatbot for preview

---

## 📊 Pre-Scan Size Estimation Methods

### Method 1: Sitemap.xml Check (Best Option)

**How it works:**
- Most websites have `/sitemap.xml`
- Lists all pages on website
- Can count URLs without downloading content

**Implementation:**
```java
public int estimateWebsiteSize(String websiteUrl) {
    try {
        String sitemapUrl = websiteUrl.endsWith("/") 
            ? websiteUrl + "sitemap.xml" 
            : websiteUrl + "/sitemap.xml";
        
        Document sitemap = Jsoup.connect(sitemapUrl)
            .timeout(5000)
            .get();
        
        // Count <url> or <loc> tags
        Elements urls = sitemap.select("url, loc");
        return urls.size();
    } catch (Exception e) {
        // Sitemap not available, use other methods
        return estimateFromRobotsTxt(websiteUrl);
    }
}
```

**Advantages:**
- ✅ Very fast (single HTTP request)
- ✅ Accurate count
- ✅ No content download needed
- ✅ Zero cost

**Limitations:**
- ❌ Not all websites have sitemap.xml
- ❌ Some sitemaps are split into multiple files

---

### Method 2: Robots.txt Check

**How it works:**
- Check `/robots.txt` for sitemap reference
- Follow sitemap links if found
- Fallback if no sitemap

**Implementation:**
```java
public int estimateFromRobotsTxt(String websiteUrl) {
    try {
        String robotsUrl = websiteUrl.endsWith("/") 
            ? websiteUrl + "robots.txt" 
            : websiteUrl + "/robots.txt";
        
        String robotsContent = Jsoup.connect(robotsUrl)
            .timeout(5000)
            .get()
            .text();
        
        // Extract sitemap URLs
        Pattern sitemapPattern = Pattern.compile("Sitemap:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = sitemapPattern.matcher(robotsContent);
        
        if (matcher.find()) {
            String sitemapUrl = matcher.group(1).trim();
            return estimateFromSitemap(sitemapUrl);
        }
    } catch (Exception e) {
        // Fallback to sampling method
    }
    return estimateBySampling(websiteUrl);
}
```

---

### Method 3: Sampling Method (Fallback)

**How it works:**
- Download homepage only
- Count internal links
- Estimate total pages based on link density
- Conservative estimate (assume 2-3x multiplier)

**Implementation:**
```java
public int estimateBySampling(String websiteUrl) {
    try {
        Document homepage = Jsoup.connect(websiteUrl)
            .timeout(10000)
            .get();
        
        // Count internal links
        Elements links = homepage.select("a[href]");
        String domain = extractDomain(websiteUrl);
        
        long internalLinks = links.stream()
            .map(link -> link.attr("abs:href"))
            .filter(href -> href.startsWith(websiteUrl) || href.contains(domain))
            .count();
        
        // Conservative estimate: assume 3x multiplier
        // (homepage has 100 links → estimate 300 pages)
        return (int) (internalLinks * 3);
    } catch (Exception e) {
        // If we can't estimate, use default conservative limit
        return 100; // Assume small website
    }
}
```

**Advantages:**
- ✅ Works for any website
- ✅ Only downloads homepage (minimal cost)
- ✅ Fast estimation

**Limitations:**
- ❌ Less accurate than sitemap
- ❌ May overestimate or underestimate

---

## 🛡️ Size-Based Limits

### Preview Mode Limits (No Subscription)

**Free Preview Limits:**
- **Max Pages:** 50 pages
- **Max Tokens:** 100,000 tokens (~$0.01 cost)
- **Estimated Cost:** ~$0.01 per preview

**If Website Exceeds Limits:**
- Block scanning before it starts
- Show message: "Website too large for preview. Subscribe to scan large websites."
- No cost incurred

---

## 💻 Implementation Flow

### Step 1: User Creates Chatbot (Preview Mode)

```java
@PostMapping("/api/chatbots")
public ResponseEntity<?> createChatbot(@RequestBody ChatbotRequest request,
                                      @AuthenticationPrincipal CustomOAuth2User user) {
    // Step 1: Estimate website size (NO COST)
    int estimatedPages = websiteSizeEstimator.estimateSize(request.getWebsiteUrl());
    
    // Step 2: Check if user has subscription
    Subscription sub = subscriptionRepository.findByUserId(user.getId());
    boolean isPaid = sub != null && sub.isPaid();
    
    // Step 3: Apply limits
    int maxPages = isPaid ? Integer.MAX_VALUE : 50; // Preview limit: 50 pages
    
    if (estimatedPages > maxPages) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(Map.of(
                "error", "Website too large for preview",
                "estimatedPages", estimatedPages,
                "maxPages", maxPages,
                "message", "Subscribe to scan websites with more than " + maxPages + " pages"
            ));
    }
    
    // Step 4: Create chatbot (will scan later)
    Chatbot chatbot = chatbotService.createChatbot(request, user);
    
    // Step 5: Start scanning (async, costs money)
    websiteAnalysisService.analyzeWebsite(chatbot);
    
    return ResponseEntity.ok(chatbot);
}
```

### Step 2: Size Estimation Service

```java
@Service
public class WebsiteSizeEstimator {
    
    public int estimateSize(String websiteUrl) {
        // Try sitemap first (most accurate)
        try {
            return estimateFromSitemap(websiteUrl);
        } catch (Exception e) {
            logger.debug("Sitemap not available, trying robots.txt");
        }
        
        // Try robots.txt
        try {
            return estimateFromRobotsTxt(websiteUrl);
        } catch (Exception e) {
            logger.debug("Robots.txt not available, using sampling");
        }
        
        // Fallback to sampling
        return estimateBySampling(websiteUrl);
    }
    
    private int estimateFromSitemap(String websiteUrl) {
        // Implementation from Method 1
    }
    
    private int estimateFromRobotsTxt(String websiteUrl) {
        // Implementation from Method 2
    }
    
    private int estimateBySampling(String websiteUrl) {
        // Implementation from Method 3
    }
}
```

---

## 💰 Cost Analysis

### Preview Mode Costs

**Scenario 1: Small Website (10 pages)**
- Size estimation: ~$0 (HTTP requests only)
- Full scan: 10 pages × 3,000 tokens = 30,000 tokens
- Embedding cost: 30K tokens × $0.10/1M = **$0.003**
- **Total: ~$0.003 per preview**

**Scenario 2: Medium Website (50 pages - at limit)**
- Size estimation: ~$0
- Full scan: 50 pages × 3,000 tokens = 150,000 tokens
- Embedding cost: 150K tokens × $0.10/1M = **$0.015**
- **Total: ~$0.015 per preview**

**Scenario 3: Large Website (15,000 pages - AWS)**
- Size estimation: ~$0 (detected via sitemap)
- **BLOCKED before scan** - No cost incurred
- User sees: "Website too large. Subscribe to continue."
- **Total: $0.00** ✅

---

## 🎯 Business Model Update

### New Model: Preview-Only (No Free Tier)

**Preview Mode (No Subscription):**
- ✅ Create 1 chatbot
- ✅ Rate-limited preview (10 messages/day)
- ✅ Website size limit: 50 pages max
- ✅ Cost per preview: ~$0.01-0.02
- ❌ No integration script
- ❌ No deployment

**Paid Subscription:**
- ✅ All preview features
- ✅ Unlimited website size
- ✅ Integration script access
- ✅ Full deployment
- ✅ Unlimited chat messages

---

## 📊 Cost Protection Summary

| Scenario | Size Estimation | Full Scan | Total Cost | Action |
|----------|----------------|-----------|------------|--------|
| Small site (10 pages) | $0 | $0.003 | **$0.003** | ✅ Allow preview |
| Medium site (50 pages) | $0 | $0.015 | **$0.015** | ✅ Allow preview |
| Large site (500 pages) | $0 | - | **$0.00** | ❌ Block before scan |
| AWS (15,000 pages) | $0 | - | **$0.00** | ❌ Block before scan |

**Key Insight:** Pre-scan estimation prevents costs for large websites while allowing preview for small/medium sites.

---

## ✅ Implementation Checklist

- [ ] Create `WebsiteSizeEstimator` service
- [ ] Implement sitemap.xml parsing
- [ ] Implement robots.txt parsing
- [ ] Implement sampling method (fallback)
- [ ] Add size check before chatbot creation
- [ ] Update error messages for size limits
- [ ] Update frontend to show size limits
- [ ] Test with various website sizes
- [ ] Monitor estimation accuracy

---

**Last Updated:** 2025-12-20  
**Status:** Planning Phase - To be implemented in Task 4

