# Environment Variable Loading Fix

## Problem
The backend was throwing "ChatModel not properly configured! Ensure ANTHROPIC_API_KEY environment variable is set" errors when trying to use the chat functionality.

## Root Cause
Spring AI autoconfiguration requires the `ANTHROPIC_API_KEY` environment variable to be available when Spring Boot starts. The issue was that:
1. The `.env` file was a symlink (`backend/.env -> ../.env`)
2. spring-dotenv 4.0.0 auto-loads `.env` files, but needs the file to be accessible
3. Environment variables needed to be consistently loaded

## Solution
1. **Copied .env file**: Replaced the symlink with an actual copy of the `.env` file in `backend/.env`
2. **spring-dotenv auto-loading**: spring-dotenv 4.0.0 automatically loads `.env` files from the working directory when Spring Boot starts
3. **Explicit export**: When starting the backend, we also export environment variables from the `.env` file to ensure they're available

## How It Works
- **spring-dotenv 4.0.0**: Automatically loads `.env` files from the working directory (where `mvn spring-boot:run` is executed)
- **Spring Boot property resolution**: Spring Boot resolves `${ANTHROPIC_API_KEY}` in `application.yml` from:
  1. Environment variables (highest priority)
  2. System properties
  3. Application properties
- **Spring AI autoconfiguration**: Spring AI checks for `spring.ai.anthropic.api-key` property and auto-configures `AnthropicChatModel` if present

## Verification
The error changed from:
- ❌ "ChatModel not properly configured! Ensure ANTHROPIC_API_KEY environment variable is set"
- ✅ "Chatbot not found" (this means the API key is working, but the chatbot with ID 1 doesn't exist in the database)

## Current Status
- ✅ Environment variables are loaded correctly
- ✅ Spring AI autoconfiguration picks up the API key
- ⚠️ Need to ensure chatbots exist in the database for testing

## Files Changed
- `backend/.env`: Changed from symlink to actual file copy
- `backend/src/main/java/com/tjanabot/chatbot/AiChatbotApplication.java`: Removed incorrect `@EnableDotEnv` annotation (spring-dotenv 4.0.0 auto-configures)

## Starting the Backend
```bash
cd backend
export $(grep -v '^#' .env | xargs)
export PORT=8081
mvn spring-boot:run -Dmaven.test.skip=true
```

The `.env` file will be automatically loaded by spring-dotenv, and the exported environment variables ensure they're available to Spring Boot.
