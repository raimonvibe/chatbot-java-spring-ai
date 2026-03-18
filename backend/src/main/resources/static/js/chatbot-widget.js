/**
 * Prayer-Chat embeddable widget — bring a gentle, Christ-centered chat to your website.
 * Place the script on your site; visitors see a chat button and can ask questions.
 */
(function() {
    'use strict';
    
    // Configuration
    let config = {
        chatbotId: null,
        apiUrl: 'http://localhost:8080/api',
        theme: 'default',
        position: 'bottom-right',
        primaryColor: '#007bff',
        secondaryColor: '#6c757d',
        fontFamily: 'Arial, sans-serif',
        borderRadius: '8px',
        buttonStyle: 'rounded'
    };
    
    // Widget state
    let isOpen = false;
    let sessionId = null;
    let messageHistory = [];
    
    // DOM elements
    let widgetContainer = null;
    let chatContainer = null;
    let messageContainer = null;
    let inputField = null;
    let sendButton = null;
    let toggleButton = null;
    
    /**
     * Ensure Font Awesome is loaded so icons display on any host site
     */
    function ensureFontAwesome() {
        if (document.querySelector('link[href*="fontawesome"]') || document.querySelector('link[href*="font-awesome"]')) return;
        var link = document.createElement('link');
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
            var placeholderId = 'prayer-chat-chatbot-' + (config && config.chatbotId);
            var el = (placeholderId && document.getElementById(placeholderId)) || document.querySelector('[data-chatbot-id="' + (config && config.chatbotId) + '"]');
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
        if (!config.chatbotId) {
            console.error('PrayerChat Chatbot: chatbotId is required');
            showEmbedError('Chat: chatbotId is required.');
            return;
        }
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
        
        // Create header
        const header = document.createElement('div');
        header.className = 'prayer-chat-widget-header';
        header.style.cssText = `
            background: ${config.primaryColor};
            color: white;
            padding: 15px;
            border-radius: ${config.borderRadius} ${config.borderRadius} 0 0;
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
            <div style="display: flex; gap: 10px;">
                <input type="text" id="prayer-chat-message-input" name="prayer-chat-message" placeholder="Type your message..." 
                       aria-label="Chat message" style="flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 20px; outline: none;">
                <button type="button" id="prayer-chat-send-btn" name="prayer-chat-send" aria-label="Send message" style="background: ${config.primaryColor}; color: white; border: none; border-radius: 50%; width: 40px; height: 40px; cursor: pointer;">
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
        chatContainer.appendChild(header);
        chatContainer.appendChild(messageContainer);
        chatContainer.appendChild(inputArea);
        
        widgetContainer.appendChild(chatContainer);
        widgetContainer.appendChild(toggleButton);
        
        // Mount in placeholder div if present (so widget appears where the user placed the embed), else body
        const placeholderId = 'prayer-chat-chatbot-' + config.chatbotId;
        const placeholderById = document.getElementById(placeholderId);
        const placeholderByData = document.querySelector('[data-chatbot-id="' + config.chatbotId + '"]');
        const mountPoint = placeholderById || placeholderByData || document.body;
        mountPoint.appendChild(widgetContainer);
        
        // Get references to interactive elements
        inputField = document.getElementById('prayer-chat-message-input');
        sendButton = document.getElementById('prayer-chat-send-btn');
        
        // Add event listeners
        setupEventListeners();
        
        // Add welcome message
        addMessage('Hello! How can I help you today?', 'bot');
    }
    
    /**
     * Setup event listeners
     */
    function setupEventListeners() {
        // Toggle button
        toggleButton.addEventListener('click', toggleChat);
        
        // Close button
        document.getElementById('prayer-chat-close-btn').addEventListener('click', closeChat);
        
        // Send button
        sendButton.addEventListener('click', sendMessage);
        
        // Input field
        inputField.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
        
        // Click outside to close
        document.addEventListener('click', function(e) {
            if (isOpen && !widgetContainer.contains(e.target)) {
                closeChat();
            }
        });
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
     * Open chat
     */
    function openChat() {
        chatContainer.style.display = 'flex';
        toggleButton.style.display = 'none';
        isOpen = true;
        inputField.focus();
    }
    
    /**
     * Close chat
     */
    function closeChat() {
        chatContainer.style.display = 'none';
        toggleButton.style.display = 'flex';
        isOpen = false;
    }
    
    /**
     * Send message
     */
    function sendMessage() {
        const message = inputField.value.trim();
        if (!message) return;
        
        // Add user message
        addMessage(message, 'user');
        inputField.value = '';
        
        // Show typing indicator
        showTypingIndicator();
        
        // Send to API
        fetch(`${config.apiUrl}/chat/${config.chatbotId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                message: message,
                sessionId: sessionId,
                language: navigator.language.split('-')[0] || 'en'
            })
        })
        .then(response => response.json())
        .then(data => {
            hideTypingIndicator();
            
            if (data.error) {
                addMessage('Sorry, I encountered an error. Please try again.', 'bot');
            } else {
                addMessage(data.message, 'bot');
            }
        })
        .catch(error => {
            hideTypingIndicator();
            addMessage('Sorry, I\'m having trouble connecting. Please try again later.', 'bot');
            console.error('Chatbot error:', error);
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
                <i class="fas fa-circle fa-xs" style="animation: pulse 1s infinite;"></i>
                <i class="fas fa-circle fa-xs" style="animation: pulse 1s infinite 0.2s;"></i>
                <i class="fas fa-circle fa-xs" style="animation: pulse 1s infinite 0.4s;"></i>
                AI is typing...
            </div>
        `;
        messageContainer.appendChild(typingDiv);
        messageContainer.scrollTop = messageContainer.scrollHeight;
    }
    
    /**
     * Hide typing indicator
     */
    function hideTypingIndicator() {
        const typingDiv = document.getElementById('prayer-chat-typing');
        if (typingDiv) {
            typingDiv.remove();
        }
    }
    
    /**
     * Load chatbot configuration
     */
    function loadChatbotConfig() {
        fetch(`${config.apiUrl}/chat/embed/${config.chatbotId}`)
            .then(response => response.json())
            .then(data => {
                if (data.error) {
                    console.error('Failed to load chatbot config:', data.error);
                    return;
                }
                if (data.name) {
                    var titleEl = document.getElementById('prayer-chat-widget-title');
                    if (titleEl) titleEl.textContent = data.name;
                }
                // Apply branding if available
                if (data.brandingConfig) {
                    try {
                        const branding = JSON.parse(data.brandingConfig);
                        if (branding.primaryColor) config.primaryColor = branding.primaryColor;
                        if (branding.secondaryColor) config.secondaryColor = branding.secondaryColor;
                        if (branding.fontFamily) config.fontFamily = branding.fontFamily;
                        if (branding.borderRadius) config.borderRadius = branding.borderRadius;
                        
                        // Update widget styling
                        updateWidgetStyling();
                    } catch (e) {
                        console.warn('Invalid branding config:', e);
                    }
                }
            })
            .catch(error => {
                console.error('Error loading chatbot config:', error);
            });
    }
    
    /**
     * Update widget styling based on branding
     */
    function updateWidgetStyling() {
        // Apply branding after config loads.
        // Security: we only set styles from the sanitized brandingConfig we already validated server-side.
        const header = document.querySelector('.prayer-chat-widget-header');
        if (header) header.style.background = config.primaryColor;

        const chatContainerEl = document.getElementById('prayer-chat-chat-container');
        if (chatContainerEl && config.borderRadius) {
            chatContainerEl.style.borderRadius = config.borderRadius;
        }

        const inputAreaEl = document.querySelector('.prayer-chat-input-area');
        if (inputAreaEl && config.borderRadius) {
            // Keep only the bottom corners rounded.
            inputAreaEl.style.borderRadius = `0 0 ${config.borderRadius} ${config.borderRadius}`;
        }

        // Update colors for interactive controls.
        const sendBtn = document.getElementById('prayer-chat-send-btn');
        if (sendBtn) sendBtn.style.background = config.primaryColor;

        const toggleBtn = document.getElementById('prayer-chat-toggle-btn');
        if (toggleBtn) toggleBtn.style.background = config.primaryColor;

        // Keep the close icon readable (header button uses no background).
        const closeBtn = document.getElementById('prayer-chat-close-btn');
        if (closeBtn) closeBtn.style.color = '#ffffff';

        if (widgetContainer && config.fontFamily) {
            widgetContainer.style.fontFamily = config.fontFamily;
        }
    }
    
    // Add CSS animations and mobile-responsive overrides
    const style = document.createElement('style');
    style.textContent = `
        @keyframes pulse {
            0%, 100% { opacity: 0.3; }
            50% { opacity: 1; }
        }
        /* Mobile: bottom sheet (bottom half of screen), fit viewport width — no horizontal overflow */
        @media (max-width: 768px) {
            #prayer-chat-chatbot-widget {
                left: 0 !important;
                right: 0 !important;
                bottom: 0 !important;
                top: auto !important;
                width: 100dvw !important;
                max-width: 100dvw !important;
                padding: 0 !important;
                margin: 0 !important;
                box-sizing: border-box !important;
                overflow-x: hidden !important;
            }
            #prayer-chat-chat-container {
                position: fixed !important;
                bottom: 0 !important;
                left: 0 !important;
                right: 0 !important;
                top: auto !important;
                width: 100dvw !important;
                max-width: 100dvw !important;
                height: 72dvh !important;
                max-height: 72dvh !important;
                border-radius: 16px 16px 0 0 !important;
                box-sizing: border-box !important;
                overflow-x: hidden !important;
                overflow-y: hidden !important;
                margin: 0 !important;
            }
            #prayer-chat-chat-container #prayer-chat-messages {
                overflow-y: auto !important;
                overflow-x: hidden !important;
                min-width: 0 !important;
            }
            #prayer-chat-chatbot-widget #prayer-chat-toggle-btn {
                position: fixed !important;
                right: max(12px, env(safe-area-inset-right)) !important;
                bottom: max(12px, env(safe-area-inset-bottom)) !important;
                left: auto !important;
            }
            .prayer-chat-widget-header {
                padding-top: max(15px, env(safe-area-inset-top)) !important;
            }
            .prayer-chat-input-area {
                padding-bottom: max(15px, env(safe-area-inset-bottom)) !important;
                box-sizing: border-box !important;
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
