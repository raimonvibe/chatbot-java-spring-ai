package com.prayer_chat.chatbot.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the embeddable chatbot widget script so it is always available
 * even when static resource handling or the hosting proxy (e.g. Render) behaves
 * differently. Ensures GET /chatbot-widget.js and GET /js/chatbot-widget.js
 * return the same script with cache headers.
 */
@RestController
public class WidgetScriptController {

    private static final String WIDGET_SCRIPT_PATH = "static/js/chatbot-widget.js";

    @GetMapping(value = "/chatbot-widget.js", produces = "application/javascript")
    public ResponseEntity<Resource> widgetScriptRoot() {
        return serveWidgetScript();
    }

    @GetMapping(value = "/js/chatbot-widget.js", produces = "application/javascript")
    public ResponseEntity<Resource> widgetScriptUnderJs() {
        return serveWidgetScript();
    }

    private ResponseEntity<Resource> serveWidgetScript() {
        Resource resource = new ClassPathResource(WIDGET_SCRIPT_PATH);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript; charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
