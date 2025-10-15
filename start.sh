#!/bin/bash

# Noupe AI Chatbot System Startup Script

echo "🤖 Starting Noupe AI Chatbot System..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17 or higher."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "☕ Java version: $JAVA_VERSION"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven 3.6 or higher."
    exit 1
fi

# Check environment variables
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  Warning: OPENAI_API_KEY is not set. AI functionality will be limited."
    echo "   Set your OpenAI API key: export OPENAI_API_KEY=your-key-here"
fi

# Create logs directory
mkdir -p logs

# Build the application
echo "🔨 Building application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check the errors above."
    exit 1
fi

# Start the application
echo "🚀 Starting application..."
echo "📊 Dashboard: http://localhost:8080"
echo "🔧 H2 Console: http://localhost:8080/h2-console"
echo "📝 Logs: ./logs/chatbot.log"
echo ""
echo "Press Ctrl+C to stop the application"

java -jar target/ai-chatbot-*.jar
