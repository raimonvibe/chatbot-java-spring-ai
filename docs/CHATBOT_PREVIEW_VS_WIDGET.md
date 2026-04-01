# Why Chatbot Preview and Integrated Widget Can Show Different Text

Both the **dashboard preview** (e.g. `/chatbot/123`) and the **embedded widget** use the same backend: `POST /api/chat/{chatbotId}` and `AiChatbotService.processMessage()`. Responses can still differ for these reasons:

## 1. **Language**

- **Widget** sends `language: navigator.language.split('-')[0] || 'en'` (browser language).
- **Preview** calls `sendMessage(chatbotId, message, sessionId)` and does **not** pass `language`, so the API uses the default **`'en'`**.

The backend adds language-specific instructions when `userLanguage` is not English (see `AiChatbotService.buildSystemPrompt()`):

```java
if (userLanguage != null && !userLanguage.equals("en")) {
    prompt.append("\nRespond in ").append(getLanguageName(userLanguage)).append(".\n");
}
```

So on a non-English browser the **widget** may get “Respond in Dutch” (etc.) and the **preview** always gets English. That alone can make the same question produce different wording or language.

**Fix:** Have the preview pass the same language as the widget (e.g. from `navigator.language`) so behavior matches.

## 2. **Conversation history (session)**

- **Preview** uses a `sessionId` from React state: initially `''`, then the value returned by the backend. All messages in that preview tab share one conversation in the DB.
- **Widget** generates its own `sessionId` on init (`'session_' + Date.now() + '_' + randomString`) and keeps it for the page session.

So preview and widget use **different conversations**. The model receives different conversation history (e.g. different prior messages), which changes context and can change replies, especially for follow-up questions.

This is expected: preview is one “user” session, embed is another.

## 3. **Model non-determinism**

The same prompt and history can still produce different answers (temperature > 0, sampling). So small differences in context or no difference at all can still yield different text.

---

## Summary

| Factor              | Preview                      | Widget                          |
|---------------------|-----------------------------|----------------------------------|
| API endpoint        | Same: `/api/chat/{id}`      | Same                             |
| Language            | Default `'en'`              | Browser `navigator.language`     |
| Session/conversation| Own session per tab         | Own session per embed load       |
| Prompt/history      | Can differ (language + history) | Can differ                  |

To make preview and widget behavior as close as possible, the preview should send the same `language` as the widget (e.g. derived from `navigator.language`).

---

## Visual parity status (updated)

The dashboard preview is now aligned with the embedded widget layout rules:

- **Desktop/Tablet:** floating widget panel at bottom-right (not a centered full-page chat app)
- **Mobile:** bottom-sheet style panel with near-full width and reduced height
- **Theme:** selected `primaryColor`/`secondaryColor` and `borderRadius` are applied to key widget UI elements
- **Avatar:** selected avatar is shown in the assistant chat UI

### Website background mode

Preview now includes an optional **Website background** mode that tries to render the chatbot's website URL behind the widget using an iframe.

- If the website allows embedding, users can see context with the widget overlay.
- If blocked by `X-Frame-Options` or CSP `frame-ancestors`, preview shows a fallback notice and still renders widget placement/theme accurately.

### Remaining known differences

- Real embed runs on the customer page and can be affected by that page's own CSS/CSP/load behavior.
- Preview is still a simulator and cannot bypass third-party framing restrictions.
