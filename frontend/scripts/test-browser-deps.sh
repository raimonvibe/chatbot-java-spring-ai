#!/bin/bash

# Script to test Mobile Safari and WebKit after installing dependencies
# Usage: ./scripts/test-browser-deps.sh

echo "=== Testing Browser Dependencies ==="
echo ""

# Check if libavif16 is installed
echo "1. Checking if libavif16 is installed..."
if dpkg -l | grep -q libavif16; then
    echo "   ✅ libavif16 is installed"
else
    echo "   ❌ libavif16 is NOT installed"
    echo "   Run: sudo apt-get install libavif16"
    exit 1
fi

echo ""
echo "2. Testing Mobile Safari..."
npm run test:e2e:pages -- --project="Mobile Safari" --grep "should load" 2>&1 | grep -E "(passed|failed|Error)" | head -3

echo ""
echo "3. Testing WebKit (Desktop Safari)..."
npm run test:e2e:pages -- --project=webkit --grep "should load" 2>&1 | grep -E "(passed|failed|Error)" | head -3

echo ""
echo "=== Test Complete ==="

