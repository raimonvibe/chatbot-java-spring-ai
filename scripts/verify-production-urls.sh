#!/bin/bash

# Production URL Verification Script
# This script verifies that production URLs are correctly configured

set -e

echo "🔍 Production URL Verification Script"
echo "======================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
BACKEND_URL="https://chatbot-backend-4mp4.onrender.com"
FRONTEND_URL="https://prayer-chat.com"
EXPECTED_CORS_ORIGINS=("https://prayer-chat.com" "https://www.prayer-chat.com")

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test result
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}❌ FAIL${NC}: $2"
        ((TESTS_FAILED++))
    fi
}

# Test 1: Backend Health Check
echo "1. Testing Backend Health Check..."
HEALTH_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "${BACKEND_URL}/api/health" || echo "000")
if [ "$HEALTH_RESPONSE" = "200" ]; then
    print_result 0 "Backend health check endpoint responds"
else
    print_result 1 "Backend health check endpoint failed (HTTP $HEALTH_RESPONSE)"
fi
echo ""

# Test 2: Root API Endpoint
echo "2. Testing Root API Endpoint..."
ROOT_RESPONSE=$(curl -s "${BACKEND_URL}/" || echo "")
if echo "$ROOT_RESPONSE" | grep -q "frontend_url"; then
    print_result 0 "Root endpoint returns API information"
    echo "   Response preview: $(echo "$ROOT_RESPONSE" | head -c 100)..."
else
    print_result 1 "Root endpoint does not return expected format"
fi
echo ""

# Test 3: CORS Configuration
echo "3. Testing CORS Configuration..."
for ORIGIN in "${EXPECTED_CORS_ORIGINS[@]}"; do
    CORS_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X OPTIONS \
        -H "Origin: ${ORIGIN}" \
        -H "Access-Control-Request-Method: GET" \
        "${BACKEND_URL}/api/auth/me" || echo "000")
    
    if [ "$CORS_RESPONSE" = "200" ]; then
        print_result 0 "CORS allows origin: ${ORIGIN}"
    else
        print_result 1 "CORS does not allow origin: ${ORIGIN} (HTTP $CORS_RESPONSE)"
    fi
done
echo ""

# Test 4: Frontend URL Detection (requires manual verification)
echo "4. Frontend URL Detection (Manual Check Required)"
echo -e "${YELLOW}⚠️  MANUAL${NC}: Check Vercel environment variables:"
echo "   - NEXT_PUBLIC_API_URL should be set to: ${BACKEND_URL}"
echo "   - Verify in Vercel Dashboard → Project Settings → Environment Variables"
echo ""

# Test 5: Integration Script URL (requires authentication)
echo "5. Integration Script URL (Manual Check Required)"
echo -e "${YELLOW}⚠️  MANUAL${NC}: After logging in to production:"
echo "   - Create or select a chatbot"
echo "   - Click 'Get Embed Code'"
echo "   - Verify embed code contains: ${BACKEND_URL}"
echo "   - Verify no localhost URLs are present"
echo ""

# Summary
echo "======================================"
echo "📊 Test Summary"
echo "======================================"
echo -e "${GREEN}Passed:${NC} $TESTS_PASSED"
echo -e "${RED}Failed:${NC} $TESTS_FAILED"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ All automated tests passed!${NC}"
    echo ""
    echo "⚠️  Remember to:"
    echo "   1. Set NEXT_PUBLIC_API_URL in Vercel"
    echo "   2. Set CORS_ALLOWED_ORIGINS in Render"
    echo "   3. Set APP_BASE_URL in Render"
    echo "   4. Test integration script generation manually"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please review the output above.${NC}"
    exit 1
fi

