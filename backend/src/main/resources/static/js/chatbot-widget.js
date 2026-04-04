/**
 * Prayer-Chat embeddable widget — bring a gentle, Christ-centered chat to your website.
 * Place the script on your site; visitors see a chat button and can ask questions.
 *
 * Integration safety (does not interfere with client code):
 * - Only adds window.PrayerChat; all other state is in closure.
 * - We never modify document.body or document.documentElement (preserves host dark mode, theme toggles, etc.).
 * - Scroll lock uses a transparent backdrop; backdrop has pointer-events: none so host controls (theme toggles, links, etc.) still work.
 * - Injected CSS is scoped to #prayer-chat-chatbot-widget so host styles are unaffected.
 * - DOM queries are scoped to our container; we never modify host elements.
 * - Document click listener does not call preventDefault/stopPropagation so host events work as usual.
 * - We only append our widget to the placeholder or body; we do not remove or replace host content.
 */
(function() {
    'use strict';
    
    // Configuration
    let config = {
        // SECURITY: widget uses opaque embedCode (not numeric ids) to prevent embed ID swapping.
        embedCode: null,
        // Deprecated: kept only so older widget init calls don't crash before showing an error.
        chatbotId: null,
        apiUrl: 'http://localhost:8080/api',
        position: 'bottom-right',
        /* Match app default; host site CSS often sets `button { background: blue }` — we override with !important in updateWidgetStyling */
        primaryColor: '#8B5E34',
        secondaryColor: '#E8DCC4',
        fontFamily: 'Arial, sans-serif',
        borderRadius: '8px',
        // Optional Cloudflare Turnstile bot protection (server can require token for /chat/embed POST)
        turnstileEnabled: false,
        turnstileSiteKey: null
    };
    
    // Widget state
    let isOpen = false;
    let sessionId = null;
    let messageHistory = [];
    /** True while waiting for the bot response; input stays enabled, send is disabled. */
    let waitingForBotResponse = false;
    
    // DOM elements
    let widgetContainer = null;
    let chatContainer = null;
    let messageContainer = null;
    let inputField = null;
    let sendButton = null;
    let toggleButton = null;
    let backdropElement = null;
    let resizeHandleEl = null;
    /** @type {{ startY: number, startH: number, pointerId: number } | null} */
    var resizeDragState = null;

    /**
     * Synchronous guard: duplicate init must be blocked BEFORE createWidget finishes appending to the DOM.
     * Otherwise two inits (e.g. script onload twice) both pass a querySelector check, create two widgets,
     * and shared closure vars point at only the last one — the first stays unstyled (often host "blue" button CSS).
     */
    var prayerChatEmbedInitTaken = Object.create(null);
    
    /**
     * Ensure Font Awesome is loaded so icons display on any host site.
     * Uses a named link id so we never add a duplicate; skips if host already has FA.
     */
    function ensureFontAwesome() {
        if (document.getElementById('prayer-chat-fa')) return;
        if (document.querySelector('link[href*="fontawesome"]') || document.querySelector('link[href*="font-awesome"]')) return;
        var link = document.createElement('link');
        link.id = 'prayer-chat-fa';
        link.rel = 'stylesheet';
        link.href = 'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css';
        link.crossOrigin = 'anonymous';
        document.head.appendChild(link);
    }
    
    /**
     * Show a visible error in the placeholder or on the page (so user knows embed failed).
     * Security: uses textContent only (no innerHTML) so message is never interpreted as HTML.
     * Callers must pass a safe string; only hardcoded messages are used in this script.
     */
    function showEmbedError(message) {
        try {
            var embedCode = config && config.embedCode;
            var placeholderId = 'prayer-chat-chatbot-' + embedCode;
            var el =
                (placeholderId && document.getElementById(placeholderId)) ||
                (embedCode ? document.querySelector('[data-embed-code="' + embedCode + '"]') : null);
            if (!el) el = document.body;
            var p = document.createElement('p');
            p.style.cssText = 'padding:12px;background:#f8d7da;border:1px solid #f5c6cb;border-radius:8px;font-family:sans-serif;font-size:14px;margin:8px;';
            p.textContent = (typeof message === 'string' ? message : null) || 'Chat failed to load. Open console (F12) for details.';
            el.appendChild(p);
        } catch (e) {
            console.error('PrayerChat embed error:', e);
        }
    }

    /**
     * Initialize the chatbot widget
     */
    function init(options) {
        try {
            config = Object.assign(config, options || {});
        } catch (e) {
            console.error('PrayerChat Chatbot: invalid options', e);
            showEmbedError('Chat config error. Check console (F12).');
            return;
        }
        if (!config.embedCode) {
            if (config.chatbotId) {
                // Old embed snippet passed chatbotId; we no longer accept it for security.
                console.error('PrayerChat Chatbot: embedCode is required (old snippet detected)');
                showEmbedError('Chat embed is outdated. Please regenerate your embed code.');
            } else {
                console.error('PrayerChat Chatbot: embedCode is required');
                showEmbedError('Chat: embedCode is required.');
            }
            return;
        }
        var ec = config.embedCode;
        if (prayerChatEmbedInitTaken[ec]) {
            console.warn('PrayerChat: duplicate init skipped (embed already mounted or initializing)');
            return;
        }
        if (document.querySelector('[data-prayer-chat-widget-for="' + ec + '"]')) {
            console.warn('PrayerChat: widget node already present for this embed — skipping duplicate init');
            prayerChatEmbedInitTaken[ec] = true;
            return;
        }
        prayerChatEmbedInitTaken[ec] = true;
        try {
            ensureFontAwesome();
            var randomValues = new Uint8Array(16);
            crypto.getRandomValues(randomValues);
            var randomString = Array.from(randomValues, function(b) { return b.toString(16).padStart(2, '0'); }).join('');
            sessionId = 'session_' + Date.now() + '_' + randomString;
            createWidget();
            loadChatbotConfig();
            console.log('PrayerChat Chatbot initialized:', config);
        } catch (e) {
            delete prayerChatEmbedInitTaken[ec];
            console.error('PrayerChat Chatbot init error:', e);
            showEmbedError('Chat failed to start. Open console (F12) for details.');
        }
    }
    
    /**
     * Create the widget HTML structure
     */
    function createWidget() {
        // Create main container (mobile overrides in injected <style> use 100dvw so it fits phone viewport)
        widgetContainer = document.createElement('div');
        widgetContainer.id = 'prayer-chat-chatbot-widget';
        widgetContainer.setAttribute('data-prayer-chat-widget-for', config.embedCode);
        widgetContainer.style.cssText = `
            position: fixed !important;
            ${config.position.includes('right') ? 'right: 20px;' : 'left: 20px;'}
            ${config.position.includes('bottom') ? 'bottom: 20px;' : 'top: 20px;'}
            z-index: 2147483647;
            font-family: ${config.fontFamily};
            max-width: calc(100vw - 24px);
            max-height: calc(100dvh - 24px);
            box-sizing: border-box;
        `;
        
        // Create chat container (sized for desktop; overridden to bottom sheet on mobile via CSS)
        chatContainer = document.createElement('div');
        chatContainer.id = 'prayer-chat-chat-container';
        chatContainer.style.cssText = `
            display: none;
            width: 350px;
            max-width: calc(100vw - 24px);
            height: 500px;
            max-height: calc(100dvh - 24px);
            background: white;
            border-radius: ${config.borderRadius};
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
            border: 1px solid #e0e0e0;
            flex-direction: column;
            position: relative;
            box-sizing: border-box;
            min-width: 0;
        `;
        
        // Top resize grip (vertical resize; panel anchored to bottom of widget container)
        resizeHandleEl = document.createElement('div');
        resizeHandleEl.className = 'prayer-chat-resize-handle';
        resizeHandleEl.setAttribute('role', 'slider');
        resizeHandleEl.setAttribute('aria-orientation', 'vertical');
        resizeHandleEl.setAttribute('aria-label', 'Chat height. Drag, or use arrow keys to resize.');
        resizeHandleEl.setAttribute('title', 'Drag to resize height');
        resizeHandleEl.setAttribute('tabindex', '0');
        resizeHandleEl.style.cssText = `
            flex-shrink: 0;
            height: 11px;
            min-height: 11px;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: ns-resize;
            touch-action: none;
            user-select: none;
            -webkit-user-select: none;
            background: linear-gradient(to bottom, rgba(0,0,0,0.06), rgba(0,0,0,0.02));
            border-bottom: 1px solid rgba(0,0,0,0.08);
            border-radius: ${config.borderRadius} ${config.borderRadius} 0 0;
        `;
        resizeHandleEl.innerHTML = '<i class="fas fa-grip-lines prayer-chat-resize-grip-icon" aria-hidden="true" style="opacity:0.5;font-size:13px;line-height:1;color:#444;"></i>';
        
        // Create header
        const header = document.createElement('div');
        header.className = 'prayer-chat-widget-header';
        header.style.cssText = `
            background: ${config.primaryColor};
            color: white;
            padding: 15px;
            border-radius: 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
        `;
        header.innerHTML = `
            <div>
                <h6 id="prayer-chat-widget-title" style="margin: 0; font-weight: 600;">
                    <i class="fas fa-robot" style="margin-right: 8px;"></i>
                    AI Assistant
                </h6>
            </div>
            <button type="button" id="prayer-chat-close-btn" name="prayer-chat-close" aria-label="Close chat" style="background: none; border: none; color: white; font-size: 18px; cursor: pointer;">
                <i class="fas fa-times"></i>
            </button>
        `;
        
        // Create messages container
        messageContainer = document.createElement('div');
        messageContainer.id = 'prayer-chat-messages';
        messageContainer.style.cssText = `
            flex: 1;
            min-height: 0;
            min-width: 0;
            padding: 15px;
            overflow-y: auto;
            overflow-x: hidden;
            -webkit-overflow-scrolling: touch;
            background: #f8f9fa;
        `;
        
        // Create input area
        const inputArea = document.createElement('div');
        inputArea.className = 'prayer-chat-input-area';
        inputArea.style.cssText = `
            padding: 15px;
            background: white;
            border-top: 1px solid #e0e0e0;
            border-radius: 0 0 ${config.borderRadius} ${config.borderRadius};
        `;
        inputArea.innerHTML = `
            <div class="prayer-chat-input-row" style="display: flex; gap: 10px; align-items: center;">
                <input type="text" id="prayer-chat-message-input" name="prayer-chat-message" placeholder="Type your message..." 
                       aria-label="Chat message" style="flex: 1; min-width: 0; padding: 10px; border: 1px solid #ddd; border-radius: 20px; outline: none; font-size: 16px;">
                <button type="button" id="prayer-chat-send-btn" name="prayer-chat-send" aria-label="Send message" style="background: ${config.primaryColor}; color: white; border: none; border-radius: 50%; width: 40px; height: 40px; min-width: 40px; min-height: 40px; flex-shrink: 0; cursor: pointer; display: flex; align-items: center; justify-content: center;">
                    <i class="fas fa-paper-plane"></i>
                </button>
            </div>
        `;
        
        // Create toggle button
        toggleButton = document.createElement('button');
        toggleButton.type = 'button';
        toggleButton.id = 'prayer-chat-toggle-btn';
        toggleButton.name = 'prayer-chat-toggle';
        toggleButton.style.cssText = `
            width: 60px;
            height: 60px;
            border-radius: 50%;
            background: ${config.primaryColor};
            color: white;
            border: none;
            cursor: pointer;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            transition: all 0.3s ease;
        `;
        toggleButton.innerHTML = '<i class="fas fa-comments" aria-hidden="true"></i><span class="prayer-chat-btn-label" style="display:none;">Chat</span>';
        toggleButton.setAttribute('aria-label', 'Open chat');
        
        // Assemble widget
        chatContainer.appendChild(resizeHandleEl);
        chatContainer.appendChild(header);
        chatContainer.appendChild(messageContainer);
        chatContainer.appendChild(inputArea);
        
        widgetContainer.appendChild(chatContainer);
        widgetContainer.appendChild(toggleButton);
        
        // Mount in placeholder div if present (so widget appears where the user placed the embed), else body
        const placeholderId = 'prayer-chat-chatbot-' + config.embedCode;
        const placeholderById = document.getElementById(placeholderId);
        const placeholderByData = document.querySelector('[data-embed-code="' + config.embedCode + '"]');
        const mountPoint = placeholderById || placeholderByData || document.body;
        mountPoint.appendChild(widgetContainer);
        
        // Get references to interactive elements (scoped to our widget so we never touch host DOM)
        inputField = widgetContainer.querySelector('#prayer-chat-message-input');
        sendButton = widgetContainer.querySelector('#prayer-chat-send-btn');
        
        // Add event listeners
        setupEventListeners();
        
        // Add welcome message
        addMessage('Hello! How can I help you today?', 'bot');

        updateWidgetStyling();
        applySavedPanelHeight();
        setupResizeHandle();
    }

    function getPanelHeightLimits() {
        var minH = 220;
        var maxH = Math.max(minH, Math.min(typeof window !== 'undefined' ? window.innerHeight - 32 : 800, 900));
        return { minH: minH, maxH: maxH };
    }

    function clampPanelHeight(px) {
        var lim = getPanelHeightLimits();
        return Math.round(Math.max(lim.minH, Math.min(lim.maxH, px)));
    }

    function updateResizeHandleAria(h) {
        if (!resizeHandleEl) return;
        var lim = getPanelHeightLimits();
        try {
            resizeHandleEl.setAttribute('aria-valuemin', String(lim.minH));
            resizeHandleEl.setAttribute('aria-valuemax', String(lim.maxH));
            resizeHandleEl.setAttribute('aria-valuenow', String(h));
        } catch (e) { /* ignore */ }
    }

    function applyPanelHeightPx(px) {
        if (!chatContainer) return;
        var h = clampPanelHeight(px);
        chatContainer.style.setProperty('height', h + 'px', 'important');
        chatContainer.style.setProperty('max-height', 'calc(100dvh - 24px)', 'important');
        updateResizeHandleAria(h);
    }

    function applySavedPanelHeight() {
        if (!config.embedCode || !chatContainer) return;
        try {
            var k = 'prayer-chat-panel-h-' + config.embedCode;
            var raw = localStorage.getItem(k);
            if (raw == null || raw === '') return;
            var n = parseInt(raw, 10);
            if (!isFinite(n)) return;
            applyPanelHeightPx(n);
        } catch (e) { /* private mode / quota */ }
    }

    function persistPanelHeight(px) {
        if (!config.embedCode) return;
        try {
            localStorage.setItem('prayer-chat-panel-h-' + config.embedCode, String(clampPanelHeight(px)));
        } catch (e) { /* ignore */ }
    }

    function detachResizeGlobalListeners() {
        document.body.style.cursor = '';
        window.removeEventListener('pointermove', onResizePointerMove);
        window.removeEventListener('pointerup', onResizePointerUp);
        window.removeEventListener('pointercancel', onResizePointerUp);
    }

    function releaseResizePointerCapture() {
        if (!resizeDragState || !resizeHandleEl) return;
        try {
            if (resizeHandleEl.hasPointerCapture(resizeDragState.pointerId)) {
                resizeHandleEl.releasePointerCapture(resizeDragState.pointerId);
            }
        } catch (err) { /* ignore */ }
    }

    /** End in-progress resize without persisting (e.g. panel closed mid-drag). */
    function cancelResizeInteraction() {
        if (!resizeDragState) return;
        releaseResizePointerCapture();
        detachResizeGlobalListeners();
        resizeDragState = null;
    }

    function setupResizeHandle() {
        if (!resizeHandleEl) return;
        resizeHandleEl.addEventListener('pointerdown', onResizePointerDown);
        resizeHandleEl.addEventListener('keydown', onResizeHandleKeydown);
    }

    function onResizeHandleKeydown(e) {
        if (!isOpen || !chatContainer) return;
        var key = e.key;
        if (key !== 'ArrowUp' && key !== 'ArrowDown' && key !== 'Home' && key !== 'End') return;
        e.preventDefault();
        var lim = getPanelHeightLimits();
        var current = clampPanelHeight(chatContainer.getBoundingClientRect().height);
        var next = current;
        if (key === 'ArrowUp') next = current + 24;
        else if (key === 'ArrowDown') next = current - 24;
        else if (key === 'Home') next = lim.maxH;
        else if (key === 'End') next = lim.minH;
        applyPanelHeightPx(next);
        persistPanelHeight(clampPanelHeight(next));
    }

    function onResizePointerDown(e) {
        if (!isOpen || !chatContainer) return;
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        try {
            resizeHandleEl.setPointerCapture(e.pointerId);
        } catch (err) { /* older browsers */ }
        var rect = chatContainer.getBoundingClientRect();
        resizeDragState = {
            startY: e.clientY,
            startH: rect.height,
            pointerId: e.pointerId
        };
        document.body.style.cursor = 'ns-resize';
        window.addEventListener('pointermove', onResizePointerMove);
        window.addEventListener('pointerup', onResizePointerUp);
        window.addEventListener('pointercancel', onResizePointerUp);
    }

    function onResizePointerMove(e) {
        if (!resizeDragState || !chatContainer) return;
        if (e.pointerId !== resizeDragState.pointerId) return;
        var dy = e.clientY - resizeDragState.startY;
        var next = resizeDragState.startH - dy;
        applyPanelHeightPx(next);
    }

    function onResizePointerUp(e) {
        if (!resizeDragState) return;
        if (e && e.pointerId !== undefined && e.pointerId !== resizeDragState.pointerId) return;
        releaseResizePointerCapture();
        detachResizeGlobalListeners();
        if (chatContainer) {
            persistPanelHeight(chatContainer.getBoundingClientRect().height);
        }
        resizeDragState = null;
    }
    
    /**
     * Setup event listeners
     */
    function setupEventListeners() {
        // Toggle button
        toggleButton.addEventListener('click', toggleChat);
        
        // Close button (scoped to our widget)
        var closeBtnEl = widgetContainer && widgetContainer.querySelector('#prayer-chat-close-btn');
        if (closeBtnEl) closeBtnEl.addEventListener('click', closeChat);
        
        // Send button
        sendButton.addEventListener('click', sendMessage);
        
        // Input field — Enter sends only when not waiting (user can type the next message while bot thinks)
        inputField.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (!waitingForBotResponse) sendMessage();
            }
        });
        
        // Click outside to close — we do not preventDefault or stopPropagation so host page behavior is unchanged.
        // Backdrop click also closes (backdrop is not inside widgetContainer).
        document.addEventListener('click', function(e) {
            if (isOpen && widgetContainer && !widgetContainer.contains(e.target)) {
                closeChat();
            }
        });
    }
    
    /**
     * Create a full-screen backdrop so we never modify document.body (preserves host dark mode, theme, etc.).
     * Backdrop uses pointer-events: none so clicks and touches pass through to the host page — theme toggles,
     * links, and other controls keep working on any site. Click-outside-to-close still works via document listener.
     */
    function createBackdrop() {
        if (backdropElement && backdropElement.parentNode) return;
        var backdrop = document.createElement('div');
        backdrop.id = 'prayer-chat-widget-backdrop';
        backdrop.setAttribute('aria-hidden', 'true');
        backdrop.style.cssText = 'position:fixed;inset:0;z-index:2147483646;background:transparent;pointer-events:none;';
        var parent = widgetContainer && widgetContainer.parentNode;
        if (parent) {
            parent.insertBefore(backdrop, widgetContainer);
            backdropElement = backdrop;
        }
    }
    
    function removeBackdrop() {
        if (backdropElement && backdropElement.parentNode) {
            backdropElement.parentNode.removeChild(backdropElement);
        }
        backdropElement = null;
    }
    
    /**
     * Toggle chat visibility
     */
    function toggleChat() {
        if (isOpen) {
            closeChat();
        } else {
            openChat();
        }
    }
    
    /**
     * Open chat. We never modify document.body so host page (dark mode, theme toggle, etc.) is unaffected.
     * A transparent backdrop blocks background scroll via touch-action and pointer-events only.
     */
    function openChat() {
        chatContainer.style.display = 'flex';
        /* !important beats mobile injected rules (#prayer-chat-toggle-btn { position:fixed }) so the FAB cannot sit on top of the send button */
        if (widgetContainer) widgetContainer.classList.add('prayer-chat-panel-open');
        if (toggleButton) {
            toggleButton.style.setProperty('display', 'none', 'important');
            toggleButton.setAttribute('aria-hidden', 'true');
        }
        isOpen = true;
        createBackdrop();
        if (inputField) {
            setTimeout(function() { inputField.focus(); }, 100);
        }
        requestAnimationFrame(function() {
            if (chatContainer && resizeHandleEl) {
                var h = clampPanelHeight(chatContainer.getBoundingClientRect().height);
                updateResizeHandleAria(h);
            }
        });
    }
    
    /**
     * Close chat. Remove only our backdrop; leave document.body untouched.
     */
    function closeChat() {
        cancelResizeInteraction();
        chatContainer.style.display = 'none';
        if (widgetContainer) widgetContainer.classList.remove('prayer-chat-panel-open');
        if (toggleButton) {
            toggleButton.style.setProperty('display', 'flex', 'important');
            toggleButton.removeAttribute('aria-hidden');
        }
        isOpen = false;
        removeBackdrop();
    }
    
    /**
     * Disable only the send control while the bot is responding (input stays editable).
     */
    function setSendWaiting(waiting) {
        waitingForBotResponse = !!waiting;
        if (sendButton) {
            sendButton.disabled = waitingForBotResponse;
            sendButton.style.opacity = waitingForBotResponse ? '0.55' : '1';
            sendButton.style.cursor = waitingForBotResponse ? 'not-allowed' : 'pointer';
            sendButton.setAttribute('aria-busy', waitingForBotResponse ? 'true' : 'false');
        }
    }

    /**
     * Send message
     */
    function sendMessage() {
        const message = inputField.value.trim();
        if (!message || waitingForBotResponse) return;
        
        // Add user message
        addMessage(message, 'user');
        inputField.value = '';
        
        setSendWaiting(true);
        showTypingIndicator();
        
        // Send to API (optionally include Turnstile token when enabled)
        Promise.resolve()
        .then(function() {
            if (!config.turnstileEnabled || !config.turnstileSiteKey) return null;
            return getTurnstileToken();
        })
        .then(function(turnstileToken) {
            return fetch(`${config.apiUrl}/chat/embed/${encodeURIComponent(config.embedCode)}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    message: message,
                    sessionId: sessionId,
                    language: navigator.language.split('-')[0] || 'en',
                    turnstileToken: turnstileToken || null
                })
            });
        })
        .then(function(response) {
            return response.json().then(function(data) {
                return { ok: response.ok, status: response.status, data: data || {} };
            });
        })
        .then(function(result) {
            var data = result.data;
            var errMsg = (typeof data.error === 'string' && data.error.trim()) ? data.error.trim() : '';
            if (errMsg.length > 500) {
                errMsg = errMsg.substring(0, 497) + '...';
            }
            if (errMsg) {
                addMessage(errMsg, 'bot');
            } else if (!result.ok) {
                if (result.status === 429) {
                    addMessage('Too many messages right now. Please try again in a little while.', 'bot');
                } else {
                    addMessage('Sorry, I encountered an error. Please try again.', 'bot');
                }
            } else {
                addMessage(data.message || 'Sorry, I encountered an error. Please try again.', 'bot');
            }
        })
        .catch(function(error) {
            addMessage('Sorry, I\'m having trouble connecting. Please try again later.', 'bot');
            console.error('Chatbot error:', error);
        })
        .finally(function() {
            hideTypingIndicator();
            setSendWaiting(false);
        });
    }
    
    /**
     * Add message to chat
     */
    function addMessage(content, type) {
        const messageDiv = document.createElement('div');
        messageDiv.style.cssText = `
            margin-bottom: 15px;
            display: flex;
            ${type === 'user' ? 'justify-content: flex-end;' : 'justify-content: flex-start;'}
        `;
        
        const bubble = document.createElement('div');
        bubble.style.cssText = `
            max-width: 80%;
            min-width: 0;
            padding: 10px 15px;
            border-radius: 18px;
            word-wrap: break-word;
            overflow-wrap: break-word;
            ${type === 'user' 
                ? `background: ${config.primaryColor}; color: white;` 
                : 'background: white; color: #333; border: 1px solid #e0e0e0;'
            }
        `;
        bubble.textContent = content;
        
        messageDiv.appendChild(bubble);
        messageContainer.appendChild(messageDiv);
        
        // Scroll to bottom
        messageContainer.scrollTop = messageContainer.scrollHeight;
        
        // Store in history
        messageHistory.push({ content, type, timestamp: Date.now() });
    }
    
    /**
     * Show typing indicator
     */
    function showTypingIndicator() {
        const typingDiv = document.createElement('div');
        typingDiv.id = 'prayer-chat-typing';
        typingDiv.style.cssText = `
            margin-bottom: 15px;
            display: flex;
            justify-content: flex-start;
            color: #666;
            font-style: italic;
        `;
        typingDiv.innerHTML = `
            <div style="background: white; padding: 10px 15px; border-radius: 18px; border: 1px solid #e0e0e0;">
                <i class="fas fa-circle fa-xs" style="animation: prayer-chat-pulse 1s infinite;"></i>
                <i class="fas fa-circle fa-xs" style="animation: prayer-chat-pulse 1s infinite 0.2s;"></i>
                <i class="fas fa-circle fa-xs" style="animation: prayer-chat-pulse 1s infinite 0.4s;"></i>
                Prayer-Chat is typing...
            </div>
        `;
        messageContainer.appendChild(typingDiv);
        messageContainer.scrollTop = messageContainer.scrollHeight;
    }
    
    /**
     * Hide typing indicator
     */
    function hideTypingIndicator() {
        const typingDiv = messageContainer ? messageContainer.querySelector('#prayer-chat-typing') : null;
        if (typingDiv) {
            typingDiv.remove();
        }
    }
    
    /**
     * Load chatbot configuration
     */
    function loadChatbotConfig() {
        fetch(`${config.apiUrl}/chat/embed/${encodeURIComponent(config.embedCode)}`)
            .then(response => response.json())
            .then(data => {
                if (data.error) {
                    console.error('Failed to load chatbot config:', data.error);
                    return;
                }
                if (data.name && widgetContainer) {
                    var titleEl = widgetContainer.querySelector('#prayer-chat-widget-title');
                    if (titleEl) titleEl.textContent = data.name;
                }
                // Turnstile (optional)
                if (typeof data.turnstileEnabled === 'boolean') {
                    config.turnstileEnabled = data.turnstileEnabled;
                }
                if (data.turnstileSiteKey) {
                    config.turnstileSiteKey = data.turnstileSiteKey;
                }
                // Avatar: only allow ids 1-6 (server already validates; defense in depth)
                var allowedAvatarIds = ['1', '2', '3', '4', '5', '6'];
                var avatarId = data.avatar && allowedAvatarIds.indexOf(String(data.avatar)) !== -1 ? String(data.avatar) : null;
                if (avatarId && widgetContainer) {
                    var headerEl = widgetContainer.querySelector('.prayer-chat-widget-header');
                    var headerFirstDiv = headerEl && headerEl.querySelector('div');
                    if (headerFirstDiv) {
                        headerFirstDiv.style.display = 'flex';
                        headerFirstDiv.style.alignItems = 'center';
                        headerFirstDiv.style.minWidth = '0';
                        var baseUrl = (config.apiUrl || '').replace(/\/api\/?$/, '');
                        var avatarSrc = baseUrl + '/images/avatars/' + avatarId + '.png';
                        var img = document.createElement('img');
                        img.src = avatarSrc;
                        img.alt = '';
                        img.setAttribute('role', 'presentation');
                        img.style.cssText = 'width:40px;height:40px;border-radius:50%;object-fit:cover;margin-right:10px;flex-shrink:0;border:2px solid rgba(255,255,255,0.5);';
                        headerFirstDiv.insertBefore(img, headerFirstDiv.firstChild);
                    }
                }
                // Apply branding if available (API may return string or already-parsed object)
                if (data.brandingConfig) {
                    try {
                        var branding = typeof data.brandingConfig === 'string'
                            ? JSON.parse(data.brandingConfig)
                            : data.brandingConfig;
                        if (branding && typeof branding === 'object') {
                            if (branding.primaryColor) config.primaryColor = branding.primaryColor;
                            if (branding.secondaryColor) config.secondaryColor = branding.secondaryColor;
                            if (branding.fontFamily) config.fontFamily = branding.fontFamily;
                            if (branding.borderRadius) config.borderRadius = branding.borderRadius;
                        }
                    } catch (e) {
                        console.warn('Invalid branding config:', e);
                    }
                }
                /* Always re-apply (host CSS cannot override; works when branding is missing or empty) */
                updateWidgetStyling();
            })
            .catch(error => {
                console.error('Error loading chatbot config:', error);
            });
    }

    /**
     * Ensure Turnstile JS is loaded and return a token (invisible).
     * If Turnstile fails, return null (server may then reject with 403).
     */
    function getTurnstileToken() {
        return new Promise(function(resolve) {
            try {
                if (!config.turnstileSiteKey) return resolve(null);
                ensureTurnstileScript(function(ok) {
                    if (!ok || !window.turnstile || !window.turnstile.execute) return resolve(null);
                    try {
                        // Use an implicit widget: execute returns a promise-like in some builds; callback is most compatible.
                        window.turnstile.execute(config.turnstileSiteKey, { action: 'chat' })
                            .then(function(token) { resolve(token || null); })
                            .catch(function() { resolve(null); });
                    } catch (e) {
                        resolve(null);
                    }
                });
            } catch (e) {
                resolve(null);
            }
        });
    }

    function ensureTurnstileScript(cb) {
        try {
            if (window.turnstile) return cb(true);
            if (document.getElementById('prayer-chat-turnstile')) {
                // Script is loading; wait briefly.
                var tries = 0;
                var t = setInterval(function() {
                    tries++;
                    if (window.turnstile) { clearInterval(t); cb(true); }
                    else if (tries > 30) { clearInterval(t); cb(false); }
                }, 100);
                return;
            }
            var s = document.createElement('script');
            s.id = 'prayer-chat-turnstile';
            s.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
            s.async = true;
            s.defer = true;
            s.onload = function() { cb(!!window.turnstile); };
            s.onerror = function() { cb(false); };
            document.head.appendChild(s);
        } catch (e) {
            cb(false);
        }
    }
    
    /**
     * Update widget styling based on branding
     */
    function updateWidgetStyling() {
        // Apply branding after config loads. All queries scoped to our widget so we never touch host DOM.
        // Security: we only set styles from the sanitized brandingConfig we already validated server-side.
        if (!widgetContainer) return;
        var primary = config.primaryColor || '#8B5E34';
        widgetContainer.style.setProperty('--prayer-chat-primary', primary);

        const header = widgetContainer.querySelector('.prayer-chat-widget-header');
        if (header) {
            header.style.setProperty('background', primary, 'important');
            header.style.setProperty('background-color', primary, 'important');
        }

        const chatContainerEl = widgetContainer.querySelector('#prayer-chat-chat-container');
        if (chatContainerEl && config.borderRadius) {
            chatContainerEl.style.borderRadius = config.borderRadius;
        }

        var resizeHandle = widgetContainer.querySelector('.prayer-chat-resize-handle');
        if (resizeHandle && config.borderRadius) {
            resizeHandle.style.borderRadius = config.borderRadius + ' ' + config.borderRadius + ' 0 0';
        }

        const inputAreaEl = widgetContainer.querySelector('.prayer-chat-input-area');
        if (inputAreaEl && config.borderRadius) {
            inputAreaEl.style.borderRadius = `0 0 ${config.borderRadius} ${config.borderRadius}`;
        }

        const sendBtn = widgetContainer.querySelector('#prayer-chat-send-btn');
        if (sendBtn) {
            sendBtn.style.setProperty('background', primary, 'important');
            sendBtn.style.setProperty('background-color', primary, 'important');
            sendBtn.style.setProperty('color', '#ffffff', 'important');
        }

        const toggleBtn = widgetContainer.querySelector('#prayer-chat-toggle-btn');
        if (toggleBtn) {
            toggleBtn.style.setProperty('background', primary, 'important');
            toggleBtn.style.setProperty('background-color', primary, 'important');
            toggleBtn.style.setProperty('color', '#ffffff', 'important');
        }

        const closeBtn = widgetContainer.querySelector('#prayer-chat-close-btn');
        if (closeBtn) {
            closeBtn.style.setProperty('background', 'transparent', 'important');
            closeBtn.style.setProperty('background-color', 'transparent', 'important');
            closeBtn.style.setProperty('color', '#ffffff', 'important');
        }

        if (config.fontFamily) {
            widgetContainer.style.fontFamily = config.fontFamily;
        }
    }
    
    // Add CSS animations and mobile-responsive overrides
    const style = document.createElement('style');
    style.id = 'prayer-chat-widget-styles';
    style.textContent = `
        @keyframes prayer-chat-pulse {
            0%, 100% { opacity: 0.3; }
            50% { opacity: 1; }
        }
        /* All rules scoped under our widget root so host page is never styled */
        /* When the panel is open, never show the floating launcher (avoids double circles with the send button). */
        #prayer-chat-chatbot-widget.prayer-chat-panel-open #prayer-chat-toggle-btn {
            display: none !important;
            visibility: hidden !important;
            pointer-events: none !important;
        }
        /* Host pages often style all buttons (e.g. Bootstrap) — keep our launcher/send on-brand */
        #prayer-chat-chatbot-widget button#prayer-chat-toggle-btn,
        #prayer-chat-chatbot-widget button#prayer-chat-send-btn {
            background-color: var(--prayer-chat-primary, #8B5E34) !important;
            color: #fff !important;
            border: none !important;
            appearance: none !important;
            -webkit-appearance: none !important;
        }
        #prayer-chat-chatbot-widget button#prayer-chat-close-btn {
            background: transparent !important;
            background-color: transparent !important;
            box-shadow: none !important;
        }
        /*
         * Grip stays tiny only on very wide viewports (fine pointer, mouse-first).
         * Up to 1920px: tablets, laptops, small desktops — larger hit area (touch + trackpad).
         */
        @media (pointer: coarse), (max-width: 1920px) {
            #prayer-chat-chatbot-widget .prayer-chat-resize-handle {
                min-height: 64px !important;
                height: auto !important;
                padding: 18px 0 !important;
                box-sizing: border-box !important;
                position: relative !important;
                z-index: 2 !important;
                -webkit-tap-highlight-color: rgba(0, 0, 0, 0.08);
            }
            #prayer-chat-chatbot-widget .prayer-chat-resize-handle .prayer-chat-resize-grip-icon {
                font-size: 26px !important;
                line-height: 1 !important;
                opacity: 0.55 !important;
            }
        }
        /* Mobile: bottom sheet 50% height, 95% width, centered — does not push viewport */
        @media (max-width: 768px) {
            #prayer-chat-chatbot-widget {
                left: 0 !important;
                right: 0 !important;
                bottom: 0 !important;
                top: auto !important;
                width: 100% !important;
                max-width: 100% !important;
                max-height: none !important;
                padding: 0 !important;
                margin: 0 !important;
                box-sizing: border-box !important;
                overflow: visible !important;
                pointer-events: none !important;
            }
            #prayer-chat-chatbot-widget #prayer-chat-chat-container {
                pointer-events: auto !important;
                position: fixed !important;
                bottom: max(12px, env(safe-area-inset-bottom)) !important;
                left: 2.5% !important;
                right: 2.5% !important;
                top: auto !important;
                width: 95% !important;
                max-width: 95vw !important;
                height: 50dvh !important;
                max-height: 50dvh !important;
                min-height: 200px !important;
                border-radius: 16px 16px 16px 16px !important;
                box-sizing: border-box !important;
                overflow-x: hidden !important;
                overflow-y: hidden !important;
                margin: 0 auto !important;
                box-shadow: 0 -4px 24px rgba(0,0,0,0.15) !important;
            }
            #prayer-chat-chatbot-widget #prayer-chat-chat-container #prayer-chat-messages {
                overflow-y: auto !important;
                overflow-x: hidden !important;
                min-width: 0 !important;
            }
            #prayer-chat-chatbot-widget #prayer-chat-toggle-btn {
                pointer-events: auto !important;
                position: fixed !important;
                right: max(12px, env(safe-area-inset-right)) !important;
                bottom: max(12px, env(safe-area-inset-bottom)) !important;
                left: auto !important;
            }
            #prayer-chat-chatbot-widget .prayer-chat-resize-handle {
                flex-shrink: 0 !important;
                touch-action: none !important;
            }
            #prayer-chat-chatbot-widget .prayer-chat-widget-header {
                padding-top: max(15px, env(safe-area-inset-top)) !important;
            }
            #prayer-chat-chatbot-widget .prayer-chat-input-area {
                padding-bottom: max(15px, env(safe-area-inset-bottom)) !important;
                box-sizing: border-box !important;
            }
            /* 16px input prevents iOS Safari auto-zoom on focus (Apple a11y threshold); avoids viewport "pop" */
            #prayer-chat-chatbot-widget #prayer-chat-message-input {
                font-size: max(16px, 1em) !important;
            }
            #prayer-chat-chatbot-widget #prayer-chat-chat-container {
                touch-action: manipulation;
            }
            /* Send button: responsive, never shrinks on mobile (min 44px touch target) */
            #prayer-chat-chatbot-widget .prayer-chat-input-row {
                flex-wrap: nowrap;
                min-width: 0;
            }
            #prayer-chat-chatbot-widget #prayer-chat-send-btn {
                flex-shrink: 0 !important;
                min-width: 44px !important;
                min-height: 44px !important;
                width: 44px !important;
                height: 44px !important;
            }
        }
    `;
    document.head.appendChild(style);
    
    // Expose global API
    window.PrayerChat = {
        init: init,
        open: openChat,
        close: closeChat,
        toggle: toggleChat,
        sendMessage: function(message) {
            if (message) {
                inputField.value = message;
                sendMessage();
            }
        }
    };
    
})();
