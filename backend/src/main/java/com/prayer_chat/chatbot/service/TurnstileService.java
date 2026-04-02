package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.TurnstileProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class TurnstileService {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestTemplate restTemplate;
    private final TurnstileProperties turnstileProperties;

    public TurnstileService(RestTemplate restTemplate, TurnstileProperties turnstileProperties) {
        this.restTemplate = restTemplate;
        this.turnstileProperties = turnstileProperties;
    }

    public boolean isEnabled() {
        return turnstileProperties.isEnabled();
    }

    public String siteKey() {
        return turnstileProperties.getSiteKey();
    }

    public VerifyResult verify(String token, String remoteIp) {
        if (!turnstileProperties.isEnabled()) {
            return VerifyResult.pass();
        }
        if (token == null || token.isBlank()) {
            return VerifyResult.fail("missing_token");
        }
        String secret = turnstileProperties.getSecretKey();
        if (secret == null || secret.isBlank()) {
            // Misconfiguration: fail closed when enabled.
            return VerifyResult.fail("missing_secret_key");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(VERIFY_URL, req, Map.class);
            boolean success = body != null && Boolean.TRUE.equals(body.get("success"));
            if (success) return VerifyResult.pass();

            Object codes = body != null ? body.get("error-codes") : null;
            String code = "verification_failed";
            if (codes instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                code = String.valueOf(list.get(0));
            }
            return VerifyResult.fail(code);
        } catch (Exception e) {
            return VerifyResult.fail("verification_error");
        }
    }

    public record VerifyResult(boolean allowed, String reason) {
        static VerifyResult pass() { return new VerifyResult(true, null); }
        static VerifyResult fail(String reason) { return new VerifyResult(false, reason); }
    }
}

