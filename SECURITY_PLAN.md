# 🔒 Security Improvement Plan - ChatWeave AI Chatbot

## Executive Summary

**Current Security Status: CRITICAL (2/10)**

This application has critical security vulnerabilities that make it **UNSAFE FOR PRODUCTION**. This plan outlines a phased approach to address all identified vulnerabilities and achieve a production-ready security posture, including Google OAuth and Stripe payment integration security.

**Target Security Status: 9.5/10** (after implementation including OAuth & Payments)

---

## 🚨 PHASE 1: CRITICAL FIXES (Week 1) - MUST DO IMMEDIATELY

These vulnerabilities allow unauthorized access to all data and functionality.

### 1.1 Implement Authentication & Authorization

**Priority: CRITICAL**
**Effort: 2-3 days**

#### Create Security Configuration

Create `src/main/java/com/chatweave/chatbot/config/SecurityConfig.java`:

```java
package com.chatweave.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/chat/**") // Only for public chat endpoints
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/chat/**").permitAll()
                .requestMatchers("/chatbot-widget.js").permitAll()
                .requestMatchers("/api/health").permitAll()

                // Admin only endpoints
                .requestMatchers("/api/chatbots/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/h2-console/**").hasRole("ADMIN")

                // Authenticated endpoints
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // H2 Console specific settings (development only)
        http.headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "https://yourdomain.com" // Replace with actual domain
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

#### Implement JWT Authentication

Create `src/main/java/com/chatweave/chatbot/security/JwtAuthenticationFilter.java`:

```java
package com.chatweave.chatbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebSecurityConfigurerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = getJwtFromRequest(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

Create `src/main/java/com/chatweave/chatbot/security/JwtTokenProvider.java`:

```java
package com.chatweave.chatbot.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 hours default
    private long jwtExpiration;

    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key)
            .compact();
    }

    public String getUsernameFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

#### Add JWT Dependency

Add to `pom.xml`:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

### 1.2 Secure Credentials & Secrets

**Priority: CRITICAL**
**Effort: 1 day**

#### Update application.yml

Remove hardcoded credentials:

```yaml
# BEFORE (INSECURE):
security:
  user:
    name: admin
    password: admin123

# AFTER (SECURE):
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

# Remove default API keys
ai:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}  # No default!
  cohere:
    api-key: ${COHERE_API_KEY}  # No default!
```

#### Disable H2 Console in Production

```yaml
spring:
  h2:
    console:
      enabled: ${H2_CONSOLE_ENABLED:false}  # Disabled by default
```

#### Create User Management

Create `src/main/java/com/chatweave/chatbot/model/User.java`:

```java
package com.chatweave.chatbot.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Data
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles")
    private Set<String> roles;

    private boolean enabled = true;
    private boolean accountNonExpired = true;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;

    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
```

### 1.3 Fix XSS Vulnerability

**Priority: CRITICAL**
**Effort: 2 hours**

#### Fix chatbot-test.html

Location: `src/main/resources/templates/chatbot-test.html` line 305

```javascript
// BEFORE (VULNERABLE):
if (type === 'bot') {
    bubble.innerHTML = `<i class="fas fa-robot me-2"></i>${content}`;
}

// AFTER (SECURE):
if (type === 'bot') {
    const icon = document.createElement('i');
    icon.className = 'fas fa-robot me-2';
    bubble.appendChild(icon);

    const text = document.createTextNode(content);
    bubble.appendChild(text);
}

// OR use DOMPurify library:
if (type === 'bot') {
    bubble.innerHTML = DOMPurify.sanitize(`<i class="fas fa-robot me-2"></i>${content}`);
}
```

#### Add DOMPurify (Recommended)

In HTML head:

```html
<script src="https://cdn.jsdelivr.net/npm/dompurify@3.0.6/dist/purify.min.js"></script>
```

### 1.4 Implement Rate Limiting

**Priority: CRITICAL**
**Effort: 1 day**

#### Add Bucket4j Dependency

```xml
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

#### Create Rate Limiting Filter

Create `src/main/java/com/chatweave/chatbot/security/RateLimitingFilter.java`:

```java
package com.chatweave.chatbot.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = getClientIdentifier(request);
        Bucket bucket = resolveBucket(key);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
            response.setContentType("application/json");
        }
    }

    private Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createNewBucket());
    }

    private Bucket createNewBucket() {
        // Allow 20 requests per minute
        Bandwidth limit = Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1)));
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Use IP address or API key as identifier
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            return apiKey;
        }
        return getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null) {
            return xForwardedFor.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
```

### 1.5 Fix CORS Configuration

**Priority: CRITICAL**
**Effort: 1 hour**

#### Update All Controllers

Replace `@CrossOrigin(origins = "*")` with environment-based configuration:

```java
// Remove @CrossOrigin annotations from all controllers
// CORS is now handled in SecurityConfig.java
```

#### Update application.yml

```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: "*"
  allow-credentials: true
```

---

## ⚠️ PHASE 2: HIGH PRIORITY FIXES (Week 2)

### 2.1 Input Validation & Sanitization

**Priority: HIGH**
**Effort: 2 days**

#### Enhanced Validation on Chat Messages

Update `ChatController.java`:

```java
@PostMapping("/{chatbotId}")
public ResponseEntity<?> chat(@PathVariable Long chatbotId,
                               @RequestBody @Valid ChatRequest request,
                               HttpServletRequest httpRequest) {
    // Validation handled by @Valid
    // Additional business logic validation here
}

// Create ChatRequest DTO:
@Data
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\s]*$", message = "Invalid characters in message")
    private String message;

    @Size(max = 100)
    private String sessionId;

    @Size(max = 10)
    @Pattern(regexp = "^[a-z]{2}$", message = "Invalid language code")
    private String language;
}
```

#### URL Validation for Chatbots

Update `Chatbot.java`:

```java
@NotBlank(message = "Website URL is required")
@URL(message = "Invalid URL format")
@Pattern(regexp = "^https?://.*", message = "URL must use HTTP or HTTPS protocol")
@Column(nullable = false)
private String websiteUrl;
```

#### Webhook URL Validation

Create validation service `src/main/java/com/chatweave/chatbot/service/UrlValidationService.java`:

```java
package com.chatweave.chatbot.service;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@Service
public class UrlValidationService {

    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "169.254.169.254", // AWS metadata
        "metadata.google.internal" // GCP metadata
    );

    private static final List<String> PRIVATE_IP_PREFIXES = Arrays.asList(
        "10.",
        "192.168.",
        "172.16.", "172.17.", "172.18.", "172.19.",
        "172.20.", "172.21.", "172.22.", "172.23.",
        "172.24.", "172.25.", "172.26.", "172.27.",
        "172.28.", "172.29.", "172.30.", "172.31."
    );

    public boolean isValidAndSafe(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            // Only allow HTTP/HTTPS
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }

            // Block known dangerous hosts
            if (BLOCKED_HOSTS.contains(host.toLowerCase())) {
                return false;
            }

            // Resolve and check IP
            InetAddress address = InetAddress.getByName(host);
            String ip = address.getHostAddress();

            // Block private IPs
            for (String prefix : PRIVATE_IP_PREFIXES) {
                if (ip.startsWith(prefix)) {
                    return false;
                }
            }

            // Block localhost IPs
            if (ip.startsWith("127.") || ip.equals("::1")) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2.2 SSRF Protection

**Priority: HIGH**
**Effort: 1 day**

#### Update WebsiteAnalysisService

```java
public void analyzeWebsite(Long chatbotId) {
    Chatbot chatbot = chatbotRepository.findById(chatbotId)
        .orElseThrow(() -> new RuntimeException("Chatbot not found"));

    // Validate URL before crawling
    if (!urlValidationService.isValidAndSafe(chatbot.getWebsiteUrl())) {
        throw new SecurityException("Invalid or unsafe URL");
    }

    // Continue with analysis...
}
```

#### Update WebhookService

```java
private void sendWebhookRequest(Chatbot chatbot, Map<String, Object> payload) {
    String webhookUrl = chatbot.getWebhookUrl();

    // Validate webhook URL
    if (webhookUrl == null || !urlValidationService.isValidAndSafe(webhookUrl)) {
        logger.warn("Invalid webhook URL for chatbot {}", chatbot.getId());
        return;
    }

    // Continue with webhook...
}
```

### 2.3 Authorization Checks

**Priority: HIGH**
**Effort: 1 day**

#### Add Owner Relationship

Update `Chatbot.java`:

```java
@ManyToOne
@JoinColumn(name = "owner_id", nullable = false)
private User owner;
```

#### Add Authorization Checks

Update `ChatbotController.java`:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/{id}")
public ResponseEntity<Chatbot> getChatbot(@PathVariable Long id) {
    Chatbot chatbot = chatbotRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Chatbot not found"));

    // Check ownership
    User currentUser = getCurrentUser();
    if (!chatbot.getOwner().getId().equals(currentUser.getId())) {
        throw new AccessDeniedException("You don't have access to this chatbot");
    }

    return ResponseEntity.ok(chatbot);
}

private User getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (User) auth.getPrincipal();
}
```

### 2.4 Secure Logging

**Priority: HIGH**
**Effort: 1 day**

#### Update application.yml

```yaml
spring:
  jpa:
    show-sql: false  # Disable SQL logging
    properties:
      hibernate:
        format_sql: false

logging:
  level:
    com.chatweave: INFO  # Change from DEBUG
    org.springframework.ai: WARN  # Change from DEBUG
    org.springframework.security: INFO
```

#### Sanitize Logs

Create `src/main/java/com/chatweave/chatbot/util/LogSanitizer.java`:

```java
package com.chatweave.chatbot.util;

import java.util.regex.Pattern;

public class LogSanitizer {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("(api[_-]?key[\"']?\\s*[:=]\\s*[\"']?)([\\w-]+)");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(password[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s]+)");

    public static String sanitize(String message) {
        if (message == null) return null;

        String sanitized = API_KEY_PATTERN.matcher(message).replaceAll("$1***REDACTED***");
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1***REDACTED***");

        return sanitized;
    }
}
```

---

## 📋 PHASE 3: MEDIUM PRIORITY FIXES (Week 3)

### 3.1 Security Headers

**Priority: MEDIUM**
**Effort: 4 hours**

#### Add Security Headers Configuration

Update `SecurityConfig.java`:

```java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' cdn.jsdelivr.net; img-src 'self' data: https:;")
    )
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)
    )
    .frameOptions(frame -> frame.deny())
    .xssProtection(xss -> xss.block(true))
    .contentTypeOptions(cto -> cto.disable())
);
```

### 3.2 Session Management

**Priority: MEDIUM**
**Effort: 4 hours**

#### Add Session Timeout

Create `src/main/java/com/chatweave/chatbot/service/SessionManagementService.java`:

```java
@Scheduled(fixedDelay = 3600000) // Run every hour
public void cleanupExpiredSessions() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
    List<Conversation> expiredSessions = conversationRepository
        .findByLastActivityBefore(cutoff);

    for (Conversation conversation : expiredSessions) {
        if (conversation.getIsActive()) {
            conversation.setIsActive(false);
            conversation.setEndedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        }
    }
}
```

### 3.3 GDPR Compliance

**Priority: MEDIUM**
**Effort: 2 days**

#### Add Privacy Consent

Create `src/main/java/com/chatweave/chatbot/model/PrivacyConsent.java`:

```java
@Entity
@Table(name = "privacy_consents")
@Data
public class PrivacyConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;
    private String ipAddress;
    private boolean consentGiven;
    private LocalDateTime consentDate;
    private String consentVersion;
}
```

#### Data Deletion Endpoint

```java
@DeleteMapping("/api/conversations/{sessionId}/data")
@PreAuthorize("permitAll()")
public ResponseEntity<?> deleteUserData(@PathVariable String sessionId) {
    conversationRepository.deleteBySessionId(sessionId);
    return ResponseEntity.ok().body(Map.of("message", "Data deleted successfully"));
}
```

### 3.4 API Key Authentication for Chat

**Priority: MEDIUM**
**Effort: 1 day**

#### Create API Key Model

```java
@Entity
@Table(name = "api_keys")
@Data
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Chatbot chatbot;

    @Column(unique = true)
    private String keyValue;

    private String name;
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
```

#### Validate API Key in Chat

```java
@PostMapping("/{chatbotId}")
public ResponseEntity<?> chat(@PathVariable Long chatbotId,
                               @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                               @RequestBody @Valid ChatRequest request) {

    // Validate API key if chatbot requires it
    Chatbot chatbot = chatbotRepository.findById(chatbotId)
        .orElseThrow(() -> new RuntimeException("Chatbot not found"));

    if (chatbot.getRequireApiKey() && !isValidApiKey(apiKey, chatbot)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Invalid or missing API key"));
    }

    // Continue...
}
```

---

## 🔍 PHASE 4: TESTING & VERIFICATION (Week 4)

### 4.1 Security Testing

**Tools to Use:**
- OWASP ZAP - Automated security scanner
- Burp Suite - Manual testing
- SonarQube - Code quality and security analysis
- Dependency-Check - Vulnerability scanning

#### Run OWASP ZAP

```bash
docker run -t owasp/zap2docker-stable zap-baseline.py \
    -t http://localhost:8080 \
    -r zap-report.html
```

#### Run Dependency Check

Add to `pom.xml`:

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.7</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 4.2 Penetration Testing Checklist

- [ ] Test authentication bypass
- [ ] Test authorization bypass (IDOR)
- [ ] Test SQL injection
- [ ] Test XSS vulnerabilities
- [ ] Test CSRF attacks
- [ ] Test SSRF vulnerabilities
- [ ] Test rate limiting
- [ ] Test session management
- [ ] Test password policies
- [ ] Test API key validation
- [ ] Test input validation
- [ ] Test error handling
- [ ] Test CORS configuration
- [ ] Test security headers

### 4.3 Code Review Checklist

- [ ] No hardcoded credentials
- [ ] No API keys in code
- [ ] Proper input validation
- [ ] Proper output encoding
- [ ] Secure session management
- [ ] Proper error handling
- [ ] Secure cryptography
- [ ] Proper authorization checks
- [ ] Secure dependencies
- [ ] No information leakage in logs

---

## 📦 PHASE 5: INFRASTRUCTURE SECURITY (Week 5)

### 5.1 HTTPS/TLS Configuration

**Priority: HIGH**

#### For Production (Render/Vercel)

Both platforms provide automatic HTTPS. Ensure:

```yaml
server:
  ssl:
    enabled: ${SSL_ENABLED:false}
  # Render/Vercel handle SSL termination
```

#### Force HTTPS

```java
// In SecurityConfig
http.requiresChannel(channel -> channel
    .anyRequest().requiresSecure()
);
```

### 5.2 Database Security

**Priority: HIGH**

#### Enable SSL for PostgreSQL

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require
```

#### Database Encryption

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          use_get_generated_keys: true
        connection:
          encryption: true
```

### 5.3 Secret Management

**Priority: HIGH**

#### Use Environment Variables (Current)

```bash
export ANTHROPIC_API_KEY="your-key"
export COHERE_API_KEY="your-key"
export JWT_SECRET="your-very-long-secret-key-min-256-bits"
```

#### Or Use AWS Secrets Manager (Production)

Add dependency:

```xml
<dependency>
    <groupId>com.amazonaws.secretsmanager</groupId>
    <artifactId>aws-secretsmanager-jdbc</artifactId>
    <version>2.0.0</version>
</dependency>
```

---

## 🔐 PHASE 6: OAuth & Payment Security (Week 6)

This phase covers security for Google OAuth integration and Stripe payment processing.

### 6.1 Google OAuth Security

**Priority: CRITICAL**
**Effort: 2-3 days**

#### Secure OAuth2 Configuration

**application.yml:**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}  # Never hardcode
            client-secret: ${GOOGLE_CLIENT_SECRET}  # Never commit to git
            scope:
              - profile
              - email
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
            authorization-grant-type: authorization_code

# Production: Add allowed redirect URIs to Google Console
# - https://yourdomain.com/login/oauth2/code/google
# NEVER allow wildcards or http:// in production
```

#### Validate OAuth State Parameter

Prevent CSRF attacks during OAuth flow:

```java
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // Validate state parameter (Spring Security handles this)
        // Additional validation
        String email = oAuth2User.getAttribute("email");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        // CRITICAL: Only accept verified emails
        if (emailVerified == null || !emailVerified) {
            throw new OAuth2AuthenticationException("Email not verified");
        }

        // Validate email domain (optional - for B2B)
        if (email != null && email.endsWith("@competitor.com")) {
            throw new OAuth2AuthenticationException("Domain not allowed");
        }

        return oAuth2User;
    }
}
```

#### Secure User Creation from OAuth

```java
@Service
public class OAuth2UserService {

    public User createOrUpdateUser(OAuth2User oAuth2User) {
        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        // Input validation
        if (googleId == null || email == null) {
            throw new IllegalArgumentException("Invalid OAuth2 user data");
        }

        // Sanitize inputs
        email = sanitizeEmail(email);
        name = sanitizeString(name, 100);

        // Check if user exists by Google ID (primary)
        Optional<User> existingUser = userRepository.findByGoogleId(googleId);

        if (existingUser.isPresent()) {
            // Update existing user
            User user = existingUser.get();
            user.setEmail(email);
            user.setProfilePictureUrl(picture);
            user.setLastLogin(LocalDateTime.now());
            return userRepository.save(user);
        }

        // Check if email already exists with different auth provider
        Optional<User> emailUser = userRepository.findByEmail(email);
        if (emailUser.isPresent()) {
            User user = emailUser.get();
            if (user.getAuthProvider() == AuthProvider.LOCAL) {
                // SECURITY: Don't auto-link accounts
                // User must manually link or use password login
                throw new OAuth2AuthenticationException(
                    "Account exists with password login. Please sign in with password."
                );
            }
        }

        // Create new user
        User newUser = new User();
        newUser.setGoogleId(googleId);
        newUser.setEmail(email);
        newUser.setUsername(email); // Use email as username
        newUser.setAuthProvider(AuthProvider.GOOGLE);
        newUser.setProfilePictureUrl(picture);
        newUser.setEnabled(true);
        newUser.getRoles().add("USER");
        newUser.setPassword(null); // OAuth users don't need password

        return userRepository.save(newUser);
    }

    private String sanitizeEmail(String email) {
        if (email == null) return null;
        email = email.toLowerCase().trim();
        // Validate email format
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return email;
    }

    private String sanitizeString(String input, int maxLength) {
        if (input == null) return null;
        input = input.trim();
        if (input.length() > maxLength) {
            input = input.substring(0, maxLength);
        }
        // Remove potentially harmful characters
        return input.replaceAll("[<>\"']", "");
    }
}
```

#### Rate Limit OAuth Endpoints

```java
@Configuration
public class OAuth2RateLimitConfig {

    @Bean
    public RateLimitingFilter oauth2RateLimiter() {
        return new RateLimitingFilter(
            "/login/oauth2/**",
            10,  // 10 attempts
            Duration.ofMinutes(15)  // per 15 minutes
        );
    }
}
```

### 6.2 Stripe Payment Security

**Priority: CRITICAL**
**Effort: 3-4 days**

#### Secure Stripe Configuration

**application.yml:**
```yaml
stripe:
  api-key: ${STRIPE_SECRET_KEY}  # sk_live_... for production
  publishable-key: ${STRIPE_PUBLISHABLE_KEY}  # pk_live_... (safe for frontend)
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}  # whsec_...
  price-id: ${STRIPE_PRICE_ID}  # price_...

# CRITICAL SECURITY RULES:
# 1. NEVER expose STRIPE_SECRET_KEY to frontend
# 2. NEVER commit keys to git
# 3. Use test keys (sk_test_...) in development
# 4. Rotate keys if compromised
# 5. Use restricted API keys with minimal permissions
```

#### Verify Webhook Signatures

**CRITICAL: Always verify webhooks to prevent spoofing**

```java
@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            // CRITICAL: Verify signature before processing
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // SECURITY: Log but don't reveal details
            logger.error("Webhook signature verification failed");
            return ResponseEntity.status(400).body("Invalid signature");
        }

        // Process verified event
        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutCompleted(event);
                    break;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;
                case "invoice.payment_failed":
                    handlePaymentFailed(event);
                    break;
                default:
                    logger.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error processing webhook", e);
            return ResponseEntity.status(500).body("Processing error");
        }

        return ResponseEntity.ok("Success");
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject()
            .orElseThrow();

        // Validate metadata
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("chatbot_id")) {
            throw new IllegalStateException("Missing chatbot_id in metadata");
        }

        Long chatbotId = Long.parseLong(metadata.get("chatbot_id"));

        // Idempotency: Check if already processed
        if (subscriptionService.isAlreadyActivated(chatbotId)) {
            logger.warn("Duplicate webhook: chatbot {} already activated", chatbotId);
            return;
        }

        // Activate subscription
        subscriptionService.activateSubscription(
            chatbotId,
            session.getCustomer(),
            session.getSubscription()
        );

        logger.info("Activated subscription for chatbot {}", chatbotId);
    }
}
```

#### Validate Subscription Status Before Access

```java
@Service
public class SubscriptionValidationService {

    public void validateAccess(Long chatbotId) {
        Chatbot chatbot = chatbotRepository.findById(chatbotId)
            .orElseThrow(() -> new ResourceNotFoundException("Chatbot not found"));

        Subscription subscription = chatbot.getSubscription();

        if (subscription == null) {
            throw new PaymentRequiredException("No subscription found");
        }

        // Check subscription status
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new PaymentRequiredException(
                "Subscription not active: " + subscription.getStatus()
            );
        }

        // Check subscription not expired
        if (subscription.getCurrentPeriodEnd().isBefore(LocalDateTime.now())) {
            // Mark as expired
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(subscription);
            throw new PaymentRequiredException("Subscription expired");
        }

        // Check message limits
        if (chatbot.getMessageCount() >= chatbot.getMessageLimit()) {
            throw new QuotaExceededException("Monthly message limit reached");
        }
    }
}
```

#### Secure Checkout Session Creation

```java
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @PostMapping("/create-checkout-session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            @RequestBody @Valid CreateCheckoutRequest request,
            @AuthenticationPrincipal User currentUser) {

        Long chatbotId = request.getChatbotId();

        // Authorization: User must own chatbot
        Chatbot chatbot = chatbotRepository.findById(chatbotId)
            .orElseThrow(() -> new ResourceNotFoundException("Chatbot not found"));

        if (!chatbot.getOwner().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Not authorized");
        }

        // Prevent duplicate subscriptions
        if (chatbot.getSubscription().getStatus() == SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Chatbot already has active subscription");
        }

        // Create Stripe customer if needed
        String customerId = stripeService.getOrCreateCustomer(currentUser);

        // Create checkout session with metadata
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(stripePriceId)
                    .setQuantity(1L)
                    .build()
            )
            .setSuccessUrl(appUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(appUrl + "/payment/cancel")
            .putMetadata("chatbot_id", chatbotId.toString())
            .putMetadata("user_id", currentUser.getId().toString())
            .build();

        Session session = stripeService.createSession(params);

        return ResponseEntity.ok(Map.of("url", session.getUrl()));
    }
}
```

#### PCI Compliance Notes

**✅ ChatWeave is PCI-DSS compliant by design:**

1. **No Card Data Stored**: Stripe handles all card processing
2. **No Card Data Touched**: Payment forms hosted by Stripe
3. **HTTPS Only**: All communication encrypted
4. **Stripe Checkout**: SAQ A compliance (simplest)

**Your Responsibilities:**
- ✅ Use HTTPS everywhere
- ✅ Never log card data (we don't have access)
- ✅ Verify webhook signatures
- ✅ Use latest Stripe SDK
- ✅ Rotate API keys if compromised

### 6.3 Subscription Fraud Prevention

#### Detect Suspicious Activity

```java
@Service
public class FraudDetectionService {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    public void detectAnomalies(User user) {
        // Check multiple failed payments
        long failedCount = subscriptionRepository
            .countByUserAndStatusAndUpdatedAtAfter(
                user,
                SubscriptionStatus.PAST_DUE,
                LocalDateTime.now().minusDays(7)
            );

        if (failedCount > 3) {
            logger.warn("Multiple payment failures for user {}", user.getId());
            // Consider blocking or manual review
        }

        // Check rapid subscription creation/cancellation
        long recentSubs = subscriptionRepository
            .countByUserAndCreatedAtAfter(
                user,
                LocalDateTime.now().minusHours(24)
            );

        if (recentSubs > 5) {
            logger.warn("Suspicious subscription activity for user {}", user.getId());
            // Rate limit or require manual approval
        }

        // Check disposable email domains
        if (isDisposableEmail(user.getEmail())) {
            logger.warn("Disposable email detected: {}", user.getEmail());
            // Require additional verification
        }
    }

    private boolean isDisposableEmail(String email) {
        String[] disposableDomains = {
            "tempmail.com", "guerrillamail.com", "10minutemail.com"
        };
        String domain = email.substring(email.indexOf("@") + 1);
        return Arrays.asList(disposableDomains).contains(domain.toLowerCase());
    }
}
```

#### Prevent Subscription Sharing

```java
@Service
public class SubscriptionSharingDetection {

    public void checkForSharing(Chatbot chatbot, HttpServletRequest request) {
        String embedCode = chatbot.getEmbedCode();

        // Track domains using embed code
        String referer = request.getHeader("Referer");
        if (referer != null) {
            String domain = extractDomain(referer);

            // Check if domain matches chatbot's website
            String authorizedDomain = extractDomain(chatbot.getWebsiteUrl());

            if (!domain.equals(authorizedDomain)) {
                logger.warn("Embed code {} used on unauthorized domain: {}",
                    embedCode, domain);

                // Options:
                // 1. Block request
                // 2. Watermark response
                // 3. Alert owner
                // 4. Charge extra
            }
        }
    }

    private String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
```

### 6.4 Secure Embed Code Generation

```java
@Service
public class EmbedCodeService {

    public String generateSecureEmbedCode(Chatbot chatbot) {
        // SECURITY: Only show embed code if subscription is active
        if (chatbot.getSubscription().getStatus() != SubscriptionStatus.ACTIVE) {
            throw new PaymentRequiredException("Activate subscription to get embed code");
        }

        String embedCode = chatbot.getEmbedCode();

        // Generate HTML with Content Security Policy hints
        return String.format("""
            <!-- ChatWeave AI Chatbot -->
            <script>
              (function() {
                var script = document.createElement('script');
                script.src = 'https://yourdomain.com/chatbot-widget.js';
                script.dataset.chatbotId = '%s';
                script.dataset.embedCode = '%s';
                script.async = true;
                script.integrity = 'sha384-...';  // Add SRI hash
                script.crossOrigin = 'anonymous';
                document.body.appendChild(script);
              })();
            </script>
            """,
            chatbot.getId(),
            embedCode
        );
    }
}
```

### 6.5 Audit Logging for Payments

```java
@Aspect
@Component
public class PaymentAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("PAYMENT_AUDIT");

    @AfterReturning(
        pointcut = "execution(* com.chatweave.chatbot.service.SubscriptionService.activateSubscription(..))",
        returning = "subscription"
    )
    public void logSubscriptionActivation(JoinPoint joinPoint, Subscription subscription) {
        auditLog.info("SUBSCRIPTION_ACTIVATED - User: {}, Chatbot: {}, Stripe: {}",
            subscription.getUser().getId(),
            subscription.getChatbot().getId(),
            subscription.getStripeSubscriptionId()
        );
    }

    @AfterReturning(
        pointcut = "execution(* com.chatweave.chatbot.service.SubscriptionService.cancelSubscription(..))",
        returning = "subscription"
    )
    public void logSubscriptionCancellation(JoinPoint joinPoint, Subscription subscription) {
        auditLog.info("SUBSCRIPTION_CANCELED - User: {}, Chatbot: {}, Reason: manual",
            subscription.getUser().getId(),
            subscription.getChatbot().getId()
        );
    }

    @AfterThrowing(
        pointcut = "execution(* com.chatweave.chatbot.controller.StripeWebhookController.*(..))",
        throwing = "exception"
    )
    public void logWebhookFailure(JoinPoint joinPoint, Exception exception) {
        auditLog.error("WEBHOOK_FAILED - Error: {}", exception.getMessage());
    }
}
```

### 6.6 Environment Variable Security Checklist

**Production Environment Variables:**

```bash
# CRITICAL: Never commit these to git
# Use secrets management (AWS Secrets Manager, Vault, etc.)

# OAuth
GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxx

# Stripe
STRIPE_SECRET_KEY=sk_live_xxx  # NEVER sk_test in production
STRIPE_PUBLISHABLE_KEY=pk_live_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx
STRIPE_PRICE_ID=price_xxx

# Database
DATABASE_URL=postgresql://...
DB_PASSWORD=xxx  # Strong password (20+ chars)

# JWT
JWT_SECRET=xxx  # 256-bit minimum

# AI APIs
ANTHROPIC_API_KEY=sk-ant-xxx
COHERE_API_KEY=xxx

# App
APP_URL=https://yourdomain.com  # HTTPS only
```

**Security Requirements:**
- ✅ All secrets in environment variables
- ✅ Different keys for dev/staging/production
- ✅ Rotate keys quarterly
- ✅ Use secrets manager in production
- ✅ Never log secrets
- ✅ Restrict access to production secrets

---

## 📊 Security Monitoring & Incident Response

### Implement Logging & Monitoring

#### Add Security Event Logging

Create `src/main/java/com/chatweave/chatbot/audit/SecurityEventLogger.java`:

```java
@Component
public class SecurityEventLogger {

    private static final Logger logger = LoggerFactory.getLogger("SECURITY");

    public void logAuthenticationSuccess(String username, String ip) {
        logger.info("AUTH_SUCCESS: user={} ip={}", username, ip);
    }

    public void logAuthenticationFailure(String username, String ip) {
        logger.warn("AUTH_FAILURE: user={} ip={}", username, ip);
    }

    public void logRateLimitExceeded(String identifier, String endpoint) {
        logger.warn("RATE_LIMIT: identifier={} endpoint={}", identifier, endpoint);
    }

    public void logSuspiciousActivity(String description, String ip) {
        logger.error("SUSPICIOUS: {} ip={}", description, ip);
    }
}
```

### Set Up Alerts

#### Monitor for:
- Failed authentication attempts (>5 in 5 minutes)
- Rate limit violations
- SSRF attempts
- Invalid tokens
- Suspicious patterns
- Database errors
- API quota exceeded

---

## ✅ Implementation Checklist

### Week 1 (Critical)
- [ ] Implement SecurityConfig with authentication
- [ ] Create JWT authentication system
- [ ] Create User entity and UserDetailsService
- [ ] Remove hardcoded credentials
- [ ] Disable H2 console (or secure it)
- [ ] Fix XSS vulnerability
- [ ] Implement rate limiting
- [ ] Fix CORS configuration
- [ ] Add environment variable validation

### Week 2 (High Priority)
- [ ] Add input validation DTOs
- [ ] Implement URL validation service
- [ ] Add SSRF protection
- [ ] Add authorization checks to all endpoints
- [ ] Add owner relationship to Chatbot
- [ ] Disable debug logging
- [ ] Implement log sanitization
- [ ] Test all changes

### Week 3 (Medium Priority)
- [ ] Add security headers
- [ ] Implement session timeout
- [ ] Add privacy consent system
- [ ] Create data deletion endpoint
- [ ] Implement API key authentication
- [ ] Add password policies
- [ ] Create admin dashboard

### Week 4 (Testing)
- [ ] Run OWASP ZAP scan
- [ ] Run dependency check
- [ ] Manual penetration testing
- [ ] Code security review
- [ ] Fix identified issues
- [ ] Document security measures

### Week 5 (Infrastructure)
- [ ] Enable HTTPS/TLS
- [ ] Configure database SSL
- [ ] Set up secret management
- [ ] Configure monitoring
- [ ] Set up security alerts
- [ ] Create incident response plan
- [ ] Final security audit

### Week 6 (OAuth & Payments)
- [ ] Configure Google OAuth2 securely
- [ ] Validate OAuth email verification
- [ ] Implement secure user creation from OAuth
- [ ] Add OAuth rate limiting
- [ ] Configure Stripe with environment variables
- [ ] Implement webhook signature verification
- [ ] Add subscription validation before access
- [ ] Secure checkout session creation
- [ ] Implement fraud detection service
- [ ] Add subscription sharing detection
- [ ] Create payment audit logging
- [ ] Test PCI compliance requirements
- [ ] Document payment security measures

---

## 🎯 Success Criteria

After completing this plan, your application should:

1. ✅ Require authentication for all admin endpoints
2. ✅ Use JWT tokens for stateless authentication
3. ✅ Have no hardcoded credentials
4. ✅ Validate and sanitize all user input
5. ✅ Be protected against XSS attacks
6. ✅ Be protected against SSRF attacks
7. ✅ Be protected against SQL injection
8. ✅ Have rate limiting on all public endpoints
9. ✅ Use HTTPS in production
10. ✅ Have proper CORS configuration
11. ✅ Log security events
12. ✅ Pass OWASP ZAP security scan
13. ✅ Have no critical/high vulnerabilities in dependencies
14. ✅ Be GDPR compliant
15. ✅ Have proper authorization checks
16. ✅ Securely integrate Google OAuth with email verification
17. ✅ Verify all Stripe webhook signatures
18. ✅ Validate subscription status before granting access
19. ✅ Be PCI-DSS compliant (SAQ A level)
20. ✅ Detect and prevent payment fraud
21. ✅ Audit log all payment-related events
22. ✅ Never expose payment API keys to frontend

**Target Security Rating: 9.5/10**

---

## 📚 Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)
- [OWASP Cheat Sheets](https://cheatsheetseries.owasp.org/)
- [CWE Top 25](https://cwe.mitre.org/top25/)

---

## 🆘 Support & Questions

For security concerns or questions about this plan:

1. Review Spring Security documentation
2. Consult OWASP guidelines
3. Perform security testing regularly
4. Keep dependencies updated
5. Monitor security advisories

**Remember: Security is not a one-time task, but an ongoing process!**
