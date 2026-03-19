# What Happens When the Embed Script Is Used

## Does the script work when embedded?

Yes. The backend is configured so that **widget endpoints** (`/api/chat/**` and `/js/**`) allow **any origin**. So when a customer embeds the snippet on their own site (e.g. `https://church-example.com`), the browser’s cross-origin requests to your backend are allowed and the chat works. Your dashboard, auth, and subscription APIs still use the strict CORS list (prayer-chat.com, localhost, etc.).

---

## Step-by-step: what happens when someone embeds your script

### 1. Customer pastes the snippet on their site

They add the embed code (from Dashboard or Account) just before `</body>` on a page, e.g.:

```html
<div id="prayer-chat-chatbot-123" data-chatbot-id="123"></div>
<script>
  (function() {
    var script = document.createElement('script');
    script.src = 'https://chatbot-java-spring-ai.onrender.com/js/chatbot-widget.js';
    script.async = true;
    script.onload = function() {
      PrayerChat.init({ chatbotId: 123, apiUrl: 'https://chatbot-java-spring-ai.onrender.com/api', theme: 'default' });
    };
    document.head.appendChild(script);
  })();
</script>
```

- The page is on the **customer’s domain** (e.g. `https://church-example.com`).
- The **browser** loads `chatbot-widget.js` from **your backend** (Render). So the script file is served by you; the code then runs in the context of the customer’s page.

### 2. When the page loads

1. The script tag runs; the browser fetches **`/js/chatbot-widget.js`** from your server (no CORS issue — it’s a normal script load).
2. After load, `PrayerChat.init({ chatbotId: 123, apiUrl: '.../api' })` runs.
3. The widget creates the UI (floating button, chat panel) on the customer’s page.
4. It generates a **session ID** (e.g. `session_1738...`) in the browser for that visitor.
5. It calls **your backend**:  
   **`GET https://your-backend.onrender.com/api/chat/embed/123`**  
   to get the chatbot’s name and branding.  
   - This is a **cross-origin** request (origin = customer’s domain, e.g. `https://church-example.com`).  
   - The browser sends an `Origin` header. Your server must respond with `Access-Control-Allow-Origin` for that origin (or `*` for widget) or the browser will block the response and the widget can’t show the name/branding (or may fail to proceed).

### 3. When a visitor types a message and sends

1. The widget shows the message in the chat and a “typing” state.
2. It sends a **POST** to your backend:  
   **`POST https://your-backend.onrender.com/api/chat/123`**  
   Body: `{ "message": "what are your opening hours?", "sessionId": "session_...", "language": "en" }`  
   Again, origin is the **customer’s domain** (e.g. `https://church-example.com`).
3. **On your end (backend):**
   - The request hits your Spring app (e.g. on Render).
   - **Rate limiting** applies (e.g. per IP or per client) for `/api/chat/*`.
   - You resolve the chatbot by ID, check it’s **active**, and (if it has an owner) check the **owner’s message limit** (subscription/plan).
   - You call your **AI service** (e.g. RAG + LLM) to generate a reply for that chatbot and session.
   - You return `{ "message": "...", "sessionId": "...", "timestamp": ..., "chatbotId": 123 }`.
4. The widget receives the JSON and appends the reply to the chat. No auth is required for this request; it’s the public “widget chat” API.

So **on your end** when a user uses the embedded script you see:

- **GET /api/chat/embed/{chatbotId}** — once per widget load (config + name).
- **POST /api/chat/{chatbotId}** — one request per message sent.  
All from the **visitor’s IP**, with **Origin** = the site where the script is embedded (the customer’s domain). You don’t see the customer’s domain in the path, only in the `Origin` header. You apply rate limits and subscription limits as you already do.

---

## Summary

| Step | Where it runs | Request to your backend |
|------|----------------|--------------------------|
| Load widget script | Customer’s site | GET your-server/js/chatbot-widget.js |
| Init + load config | Customer’s site | GET your-server/api/chat/embed/{id} |
| Visitor sends message | Customer’s site | POST your-server/api/chat/{id} with message + sessionId |

So **yes, the script works when embedded** on any site. Widget traffic is allowed from any origin; the rest of the app still uses the strict CORS list.

---

## Host page compatibility (theme toggles, dark mode, links)

The widget is designed so it **does not interfere** with your site’s own behavior:

- **Theme / dark mode toggles** — The overlay behind the chat panel uses `pointer-events: none`, so clicks and touches pass through to your page. Buttons like a dark-mode toggle, nav links, or other controls keep working even while the chat is open. This applies to any site (static HTML, React, etc.).
- **No `body` or `html` changes** — The widget never sets `document.body.style` or classes on `body`/`html`, so your existing theme or layout logic is unchanged.
- **Scoped styles** — All widget CSS is scoped under `#prayer-chat-chatbot-widget`, so it does not affect your global styles.

If you previously saw a theme toggle (or similar control) stop working after adding the embed, ensure you are using the latest widget script; the overlay was updated so host controls remain clickable.

---

## Mobile / responsive behavior

On viewports **≤ 768px**, the chat panel opens as a **bottom sheet** (about 72% of viewport height from the bottom), so it does not cover the whole screen and stays within the window; width is limited to the viewport so it never sticks out on the right. The floating chat button uses **safe-area insets** so it isn’t hidden by notches or home indicators; the header and input area also respect safe areas. Desktop keeps the original 350×500-style panel.

---

## Troubleshooting: Widget not visible on my site

If you pasted the embed code but the chat button does not appear:

1. **Open the browser console** (F12 → Console). Look for:
   - **Blocked script** – Your site’s **Content-Security-Policy (CSP)** may be blocking the widget. See below for what to add.
   - **404** – The script URL may be wrong, or the backend may be waking up. On Render free tier, the first request after spin-down can return 404 (`x-render-routing: no-server`) until the instance is ready; wait a minute and reload, or open `https://your-backend.onrender.com/actuator/health` to wake the service, then reload your page. The backend serves the widget at both `/js/chatbot-widget.js` and `/chatbot-widget.js`.
   - **CORS or network errors** – The backend allows any origin for `/api/chat/**` and `/js/**`; if you use a proxy or custom domain, ensure it forwards requests correctly.

### Why add CSP for the embed?

If your site sends a **Content-Security-Policy** header, the browser enforces where scripts and network requests can come from. The embed does two things that CSP can block:

| CSP directive | What it controls | Why the widget needs it |
|---------------|-------------------|--------------------------|
| **script-src** | Where JavaScript files may be loaded from | The embed loads the widget from your backend, e.g. `https://chatbot-java-spring-ai.onrender.com/js/chatbot-widget.js`. If that origin is not in `script-src`, the browser blocks the script and the chat never runs. |
| **connect-src** | Where the page can send `fetch()` / XHR requests | After the script loads, the widget calls your API (e.g. `GET /api/chat/embed/3`, `POST /api/chat/3`). Those requests go to the same backend origin. If that origin is not in `connect-src`, the browser blocks the requests and the chat can’t load config or send messages. |

**What to add:** Include your backend origin (no path) in both directives, for example:

- `script-src ... https://chatbot-java-spring-ai.onrender.com`
- `connect-src ... https://chatbot-java-spring-ai.onrender.com`

Use your real backend URL if it’s different. After redeploying your site with the updated CSP, the widget should load and work.

2. **Place the snippet** just before `</body>` so the placeholder `<div>` is in the DOM when the script runs.

3. **HTTPS** – Use `https://` in the script and API URLs when your site is on HTTPS.
