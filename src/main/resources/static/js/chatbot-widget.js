/**
 * Noupe AI Chatbot Widget
 * Embeddable chatbot widget for websites
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
     * Initialize the chatbot widget
     */
    function init(options) {
        config = Object.assign(config, options);
        
        if (!config.chatbotId) {
            console.error('Noupe Chatbot: chatbotId is required');
            return;
        }
        
        // Generate session ID
        sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        
        // Create widget
        createWidget();
        
        // Load chatbot configuration
        loadChatbotConfig();
        
        console.log('Noupe Chatbot initialized:', config);
    }
    
    /**
     * Create the widget HTML structure
     */
    function createWidget() {
        // Create main container
        widgetContainer = document.createElement('div');
        widgetContainer.id = 'noupe-chatbot-widget';
        widgetContainer.style.cssText = `
            position: fixed;
            ${config.position.includes('right') ? 'right: 20px;' : 'left: 20px;'}
            ${config.position.includes('bottom') ? 'bottom: 20px;' : 'top: 20px;'}
            z-index: 9999;
            font-family: ${config.fontFamily};
        `;
        
        // Create chat container
        chatContainer = document.createElement('div');
        chatContainer.id = 'noupe-chat-container';
        chatContainer.style.cssText = `
            display: none;
            width: 350px;
            height: 500px;
            background: white;
            border-radius: ${config.borderRadius};
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
            border: 1px solid #e0e0e0;
            flex-direction: column;
            position: relative;
        `;
        
        // Create header
        const header = document.createElement('div');
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
                <h6 style="margin: 0; font-weight: 600;">
                    <i class="fas fa-robot" style="margin-right: 8px;"></i>
                    AI Assistant
                </h6>
            </div>
            <button id="noupe-close-btn" style="background: none; border: none; color: white; font-size: 18px; cursor: pointer;">
                <i class="fas fa-times"></i>
            </button>
        `;
        
        // Create messages container
        messageContainer = document.createElement('div');
        messageContainer.id = 'noupe-messages';
        messageContainer.style.cssText = `
            flex: 1;
            padding: 15px;
            overflow-y: auto;
            background: #f8f9fa;
        `;
        
        // Create input area
        const inputArea = document.createElement('div');
        inputArea.style.cssText = `
            padding: 15px;
            background: white;
            border-top: 1px solid #e0e0e0;
            border-radius: 0 0 ${config.borderRadius} ${config.borderRadius};
        `;
        inputArea.innerHTML = `
            <div style="display: flex; gap: 10px;">
                <input type="text" id="noupe-message-input" placeholder="Type your message..." 
                       style="flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 20px; outline: none;">
                <button id="noupe-send-btn" style="background: ${config.primaryColor}; color: white; border: none; border-radius: 50%; width: 40px; height: 40px; cursor: pointer;">
                    <i class="fas fa-paper-plane"></i>
                </button>
            </div>
        `;
        
        // Create toggle button
        toggleButton = document.createElement('button');
        toggleButton.id = 'noupe-toggle-btn';
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
        toggleButton.innerHTML = '<i class="fas fa-comments"></i>';
        
        // Assemble widget
        chatContainer.appendChild(header);
        chatContainer.appendChild(messageContainer);
        chatContainer.appendChild(inputArea);
        
        widgetContainer.appendChild(chatContainer);
        widgetContainer.appendChild(toggleButton);
        
        // Add to page
        document.body.appendChild(widgetContainer);
        
        // Get references to interactive elements
        inputField = document.getElementById('noupe-message-input');
        sendButton = document.getElementById('noupe-send-btn');
        
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
        document.getElementById('noupe-close-btn').addEventListener('click', closeChat);
        
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
            padding: 10px 15px;
            border-radius: 18px;
            word-wrap: break-word;
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
        typingDiv.id = 'noupe-typing';
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
        const typingDiv = document.getElementById('noupe-typing');
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
        // Update colors
        const elements = [
            { selector: '#noupe-close-btn', style: 'color' },
            { selector: '#noupe-send-btn', style: 'background' },
            { selector: '#noupe-toggle-btn', style: 'background' }
        ];
        
        elements.forEach(({ selector, style }) => {
            const element = document.querySelector(selector);
            if (element) {
                element.style[style] = config.primaryColor;
            }
        });
    }
    
    // Add CSS animations
    const style = document.createElement('style');
    style.textContent = `
        @keyframes pulse {
            0%, 100% { opacity: 0.3; }
            50% { opacity: 1; }
        }
    `;
    document.head.appendChild(style);
    
    // Expose global API
    window.NoupeChatbot = {
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
