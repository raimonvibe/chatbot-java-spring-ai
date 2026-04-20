#!/bin/bash

# Test script for Bible data loading and Christian Content Analysis
# Usage: ./scripts/test-bible-analysis.sh [backend-url]

BACKEND_URL="${1:-http://localhost:8081}"
API_BASE="${BACKEND_URL}/api"

echo "🧪 Testing Bible Data and Christian Content Analysis"
echo "Backend URL: $BACKEND_URL"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Test 1: Check Bible data status
echo "1️⃣  Checking Bible data status..."
STATUS_RESPONSE=$(curl -s "${API_BASE}/admin/bible/status")
if [ $? -eq 0 ]; then
    echo "$STATUS_RESPONSE" | jq '.' 2>/dev/null || echo "$STATUS_RESPONSE"
    
    DATA_LOADED=$(echo "$STATUS_RESPONSE" | jq -r '.dataLoaded' 2>/dev/null)
    TOTAL_VERSES=$(echo "$STATUS_RESPONSE" | jq -r '.totalVerses' 2>/dev/null)
    EMBEDDINGS_READY=$(echo "$STATUS_RESPONSE" | jq -r '.embeddingsReady' 2>/dev/null)
    
    if [ "$DATA_LOADED" = "true" ]; then
        print_status "Bible data is loaded: $TOTAL_VERSES verses"
    else
        print_warning "Bible data not loaded yet"
    fi
    
    if [ "$EMBEDDINGS_READY" = "true" ]; then
        print_status "Embeddings are ready"
    else
        print_warning "Embeddings not ready yet"
    fi
else
    print_error "Failed to check status"
fi

echo ""

# Test 2: Load Bible data (if not loaded)
if [ "$DATA_LOADED" != "true" ]; then
    echo "2️⃣  Loading Bible data..."
    LOAD_RESPONSE=$(curl -s -X POST "${API_BASE}/admin/bible/load-data")
    if [ $? -eq 0 ]; then
        echo "$LOAD_RESPONSE" | jq '.' 2>/dev/null || echo "$LOAD_RESPONSE"
        print_status "Bible data load initiated"
    else
        print_error "Failed to load Bible data"
    fi
    echo ""
fi

# Test 3: Check embedding progress
echo "3️⃣  Checking embedding generation progress..."
PROGRESS_RESPONSE=$(curl -s "${API_BASE}/admin/bible/embedding-progress")
if [ $? -eq 0 ]; then
    echo "$PROGRESS_RESPONSE" | jq '.' 2>/dev/null || echo "$PROGRESS_RESPONSE"
    
    PERCENTAGE=$(echo "$PROGRESS_RESPONSE" | jq -r '.percentage' 2>/dev/null)
    COMPLETED=$(echo "$PROGRESS_RESPONSE" | jq -r '.completed' 2>/dev/null)
    
    if [ "$COMPLETED" = "true" ]; then
        print_status "Embeddings generation completed: ${PERCENTAGE}%"
    else
        print_warning "Embeddings generation in progress: ${PERCENTAGE}%"
        echo ""
        echo "To generate embeddings, run:"
        echo "  curl -X POST ${API_BASE}/admin/bible/generate-embeddings"
        echo ""
        echo "⚠️  WARNING: This will take a long time and cost API credits!"
    fi
else
    print_error "Failed to check embedding progress"
fi

echo ""

# Test 4: Test Christian Content Analysis (if embeddings ready)
if [ "$EMBEDDINGS_READY" = "true" ]; then
    echo "4️⃣  Testing Christian Content Analysis..."
    echo "   (This requires a chatbot with analyzed website content)"
    echo ""
    echo "To test analysis, you need to:"
    echo "  1. Create a chatbot via /api/chatbots/onboarding"
    echo "  2. Wait for website analysis to complete"
    echo "  3. Call: POST /api/chatbots/{id}/analyze-christian-content"
    echo ""
    echo "Example:"
    echo "  curl -X POST \"${API_BASE}/chatbots/1/analyze-christian-content?maxVerses=10&similarityThreshold=0.5\" \\"
    echo "    -H \"Cookie: JSESSIONID=your-session-id\""
else
    print_warning "Cannot test analysis - embeddings not ready"
    echo ""
    echo "To generate embeddings, run:"
    echo "  curl -X POST ${API_BASE}/admin/bible/generate-embeddings"
    echo ""
    echo "⚠️  WARNING: This will take 30+ minutes and cost significant API credits!"
fi

echo ""
echo "✅ Test script completed"

