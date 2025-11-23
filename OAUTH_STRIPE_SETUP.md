# Google OAuth 2.0 & Stripe Setup Guide

This guide will walk you through setting up Google OAuth 2.0 authentication and Stripe subscription payments for the TjanaBot AI Chatbot application.

## Table of Contents
1. [Google OAuth 2.0 Setup](#google-oauth-20-setup)
2. [Stripe Setup](#stripe-setup)
3. [Environment Configuration](#environment-configuration)
4. [Testing](#testing)

---

## Google OAuth 2.0 Setup

### Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" → "New Project"
3. Enter project name: `TjanaBot Chatbot` (or your preferred name)
4. Click "Create"

### Step 2: Enable Google+ API

1. In your project, go to "APIs & Services" → "Library"
2. Search for "Google+ API"
3. Click on it and press "Enable"

### Step 3: Configure OAuth Consent Screen

1. Go to "APIs & Services" → "OAuth consent screen"
2. Select "External" (unless you have Google Workspace)
3. Click "Create"
4. Fill in the required information:
   - **App name**: TjanaBot AI Chatbot
   - **User support email**: Your email
   - **Developer contact information**: Your email
5. Click "Save and Continue"
6. **Scopes**: Click "Add or Remove Scopes"
   - Add: `userinfo.email`
   - Add: `userinfo.profile`
7. Click "Save and Continue"
8. **Test users** (for development): Add your Google email
9. Click "Save and Continue"

### Step 4: Create OAuth 2.0 Credentials

1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth 2.0 Client ID"
3. Select "Web application"
4. **Name**: TjanaBot Web Client
5. **Authorized JavaScript origins**:
   - Development: `http://localhost:8080`
   - Production: `https://yourdomain.com`
6. **Authorized redirect URIs**:
   - Development: `http://localhost:8080/login/oauth2/code/google`
   - Production: `https://yourdomain.com/login/oauth2/code/google`
7. Click "Create"
8. **Copy the Client ID and Client Secret** - you'll need these for your `.env` file

---

## Stripe Setup

### Step 1: Create Stripe Account

1. Go to [Stripe](https://stripe.com/)
2. Sign up for an account
3. Complete the account verification process

### Step 2: Get API Keys

1. Go to [Stripe Dashboard](https://dashboard.stripe.com/)
2. Click "Developers" → "API keys"
3. You'll see two keys:
   - **Publishable key** (starts with `pk_test_`) - for frontend
   - **Secret key** (starts with `sk_test_`) - for backend
4. Click "Reveal test key" and copy the **Secret key**
5. Save this for your `.env` file

### Step 3: Create a Product and Price

1. In Stripe Dashboard, go to "Products" → "Add product"
2. Fill in product details:
   - **Name**: TjanaBot Pro Subscription
   - **Description**: Access to TjanaBot AI Chatbot platform
3. Under "Pricing":
   - **Pricing model**: Recurring
   - **Price**: (e.g., $19.99)
   - **Billing period**: Monthly (or your preferred interval)
   - **Currency**: USD (or your preferred currency)
4. Click "Save product"
5. **Copy the Price ID** (starts with `price_`) - you'll need this for your `.env` file

### Step 4: Set Up Webhook

1. Go to "Developers" → "Webhooks"
2. Click "Add endpoint"
3. **Endpoint URL**:
   - Development: `http://localhost:8080/stripe/webhook` (use ngrok or similar for local testing)
   - Production: `https://yourdomain.com/stripe/webhook`
4. **Events to listen to**:
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `invoice.payment_succeeded`
   - `invoice.payment_failed`
5. Click "Add endpoint"
6. **Copy the Signing secret** (starts with `whsec_`) - you'll need this for your `.env` file

### Step 5: Configure Webhook for Local Development (Optional)

For local development, use the Stripe CLI to forward webhook events:

```bash
# Install Stripe CLI
brew install stripe/stripe-cli/stripe

# Login to Stripe
stripe login

# Forward webhooks to local server
stripe listen --forward-to localhost:8080/stripe/webhook
```

The CLI will display a webhook signing secret. Use this for `STRIPE_WEBHOOK_SECRET` in development.

---

## Environment Configuration

### Step 1: Copy Environment File

```bash
cp .env.example .env
```

### Step 2: Configure `.env`

Fill in the following variables in your `.env` file:

```bash
# Google OAuth 2.0
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret

# Stripe
STRIPE_SECRET_KEY=sk_test_your_secret_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
STRIPE_PRICE_ID=price_your_price_id

# Stripe URLs (update the domain for production)
STRIPE_SUCCESS_URL=http://localhost:3000/dashboard?session_id={CHECKOUT_SESSION_ID}
STRIPE_CANCEL_URL=http://localhost:3000/pricing
```

---

## Testing

### Test Google OAuth Login

1. Start your backend server:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. Navigate to: `http://localhost:8080/oauth2/authorization/google`

3. You should be redirected to Google's login page

4. After successful login, you'll be redirected back to your application

### Test Stripe Subscription

1. Ensure your backend server is running

2. Use Stripe's test card numbers:
   - **Success**: `4242 4242 4242 4242`
   - **Requires authentication**: `4000 0025 0000 3155`
   - **Declined**: `4000 0000 0000 9995`

3. Test the subscription flow:
   ```bash
   # Create checkout session
   curl -X POST http://localhost:8080/api/subscription/create-checkout-session \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"

   # Check subscription status
   curl http://localhost:8080/api/subscription/status \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"

   # Cancel subscription
   curl -X POST http://localhost:8080/api/subscription/cancel \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```

4. Monitor webhook events:
   - If using Stripe CLI: Check the terminal running `stripe listen`
   - Otherwise: Check Stripe Dashboard → Developers → Webhooks → Your endpoint

### Verify Subscription Access Control

1. Try accessing chatbot endpoints without a subscription:
   ```bash
   curl http://localhost:8080/api/chatbots \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```
   Expected: `403 Forbidden`

2. Create a subscription and try again
   Expected: `200 OK` with chatbot list

---

## Production Deployment

### Before Going Live

1. **Switch to Live Keys**:
   - In Stripe Dashboard, toggle to "Live mode"
   - Copy live API keys (start with `sk_live_` and `pk_live_`)
   - Update `.env` with live keys

2. **Update OAuth Redirect URIs**:
   - Add production domain to Google OAuth credentials
   - Example: `https://yourdomain.com/login/oauth2/code/google`

3. **Update Stripe Webhook**:
   - Add production webhook endpoint
   - Example: `https://yourdomain.com/stripe/webhook`

4. **Update CORS Settings**:
   ```bash
   CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
   ```

5. **Verify All Environment Variables**:
   - Check `.env` file has all production values
   - Ensure no test keys are being used

---

## Troubleshooting

### Google OAuth Issues

**Problem**: "redirect_uri_mismatch" error
- **Solution**: Ensure the redirect URI in Google Console exactly matches your application URL

**Problem**: "Access blocked: This app's request is invalid"
- **Solution**: Make sure you've added your email to test users in OAuth consent screen

### Stripe Issues

**Problem**: Webhook signature verification failed
- **Solution**: Ensure `STRIPE_WEBHOOK_SECRET` matches the signing secret from Stripe Dashboard

**Problem**: "No such price" error
- **Solution**: Verify the `STRIPE_PRICE_ID` is correct and from the right environment (test/live)

**Problem**: Subscription not updating in database
- **Solution**: Check webhook endpoint is accessible and events are being received

---

## Security Best Practices

1. **Never commit `.env` file** - it's already in `.gitignore`
2. **Use strong JWT secrets** - at least 32 characters, random
3. **Keep Stripe keys secure** - never expose in frontend code
4. **Use HTTPS in production** - required for OAuth and Stripe
5. **Rotate keys regularly** - especially if compromised
6. **Monitor Stripe logs** - check for suspicious activity

---

## Support

- **Google OAuth**: [Google OAuth Documentation](https://developers.google.com/identity/protocols/oauth2)
- **Stripe**: [Stripe Documentation](https://stripe.com/docs)
- **Issues**: [GitHub Issues](https://github.com/raimonvibe/chatbot-java-spring-ai/issues)

---

## Architecture Overview

```
┌─────────────┐
│   Frontend  │
│  (React)    │
└─────┬───────┘
      │
      │ OAuth Login
      ▼
┌─────────────────────────────┐
│   Spring Boot Backend       │
│ ┌─────────────────────────┐ │
│ │ SecurityConfig          │ │
│ │ - OAuth2Login          │ │
│ │ - JWT Auth             │ │
│ └───────┬─────────────────┘ │
│         │                    │
│         ▼                    │
│ ┌─────────────────────────┐ │
│ │ CustomOAuth2UserService │ │
│ │ - Create/Update User    │ │
│ │ - Link Google Account   │ │
│ └───────┬─────────────────┘ │
│         │                    │
│         ▼                    │
│ ┌─────────────────────────┐ │
│ │ StripeService           │ │
│ │ - Create Checkout       │ │
│ │ - Handle Webhooks       │ │
│ │ - Manage Subscriptions  │ │
│ └───────┬─────────────────┘ │
│         │                    │
│         ▼                    │
│ ┌─────────────────────────┐ │
│ │ ChatbotController       │ │
│ │ - Check Subscription    │ │
│ │ - Verify Ownership      │ │
│ │ - Manage Chatbots       │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
      │           │
      │           │
      ▼           ▼
┌──────────┐  ┌────────┐
│  Google  │  │ Stripe │
│  OAuth   │  │   API  │
└──────────┘  └────────┘
```

---

*Last updated: 2025*
