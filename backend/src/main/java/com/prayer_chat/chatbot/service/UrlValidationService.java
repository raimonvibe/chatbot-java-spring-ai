package com.prayer_chat.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * URL Validation Service
 * Prevents SSRF (Server-Side Request Forgery) attacks
 */
@Service
public class UrlValidationService {

    private static final Logger logger = LoggerFactory.getLogger(UrlValidationService.class);

    // Blocked hostnames
    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "169.254.169.254",          // AWS metadata
        "metadata.google.internal",  // GCP metadata
        "metadata",
        "169.254.169.254",          // Azure metadata
        "fd00:ec2::254"             // AWS metadata IPv6
    );

    // Private IP ranges (RFC 1918)
    private static final List<String> PRIVATE_IP_PREFIXES = Arrays.asList(
        "10.",                      // Class A private
        "192.168.",                 // Class C private
        "172.16.", "172.17.", "172.18.", "172.19.",  // Class B private
        "172.20.", "172.21.", "172.22.", "172.23.",
        "172.24.", "172.25.", "172.26.", "172.27.",
        "172.28.", "172.29.", "172.30.", "172.31."
    );

    // Link-local addresses
    private static final List<String> LINK_LOCAL_PREFIXES = Arrays.asList(
        "169.254.",                 // IPv4 link-local
        "fe80:"                     // IPv6 link-local
    );

    // Loopback addresses
    private static final List<String> LOOPBACK_PREFIXES = Arrays.asList(
        "127.",                     // IPv4 loopback
        "::1",                      // IPv6 loopback
        "0:0:0:0:0:0:0:1"          // IPv6 loopback expanded
    );

    // Pattern for valid public URLs
    private static final Pattern VALID_URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}(:[0-9]+)?(/.*)?$"
    );

    /**
     * Validate that URL is safe and doesn't point to internal resources
     *
     * @param url The URL to validate
     * @return true if URL is safe, false otherwise
     */
    public boolean isValidAndSafe(String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.warn("URL validation failed: URL is null or empty");
            return false;
        }

        try {
            // Parse URI
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            // Validate scheme
            if (!isValidScheme(scheme)) {
                logger.warn("URL validation failed: Invalid scheme '{}' for URL: {}", scheme, url);
                return false;
            }

            // Validate host format
            if (host == null || host.isEmpty()) {
                logger.warn("URL validation failed: Missing host in URL: {}", url);
                return false;
            }

            // Check against blocked hosts
            if (isBlockedHost(host)) {
                logger.warn("URL validation failed: Blocked host '{}' in URL: {}", host, url);
                return false;
            }

            // Resolve and validate IP address
            if (!isValidIpAddress(host)) {
                logger.warn("URL validation failed: Invalid or private IP for host '{}' in URL: {}", host, url);
                return false;
            }

            // Validate port (if specified)
            int port = uri.getPort();
            if (port != -1 && !isValidPort(port)) {
                logger.warn("URL validation failed: Invalid port '{}' in URL: {}", port, url);
                return false;
            }

            // Validate URL pattern
            if (!VALID_URL_PATTERN.matcher(url).matches()) {
                logger.warn("URL validation failed: URL doesn't match valid pattern: {}", url);
                return false;
            }

            logger.debug("URL validation successful: {}", url);
            return true;

        } catch (Exception e) {
            logger.error("URL validation failed with exception for URL: {}", url, e);
            return false;
        }
    }

    /**
     * Check if scheme is valid (only HTTP and HTTPS)
     */
    private boolean isValidScheme(String scheme) {
        return scheme != null && (scheme.equals("http") || scheme.equals("https"));
    }

    /**
     * Check if host is in blocked list
     */
    private boolean isBlockedHost(String host) {
        String lowerHost = host.toLowerCase();
        return BLOCKED_HOSTS.stream()
                .anyMatch(blocked -> lowerHost.equals(blocked) || lowerHost.endsWith("." + blocked));
    }

    /**
     * Validate IP address is not private/internal
     */
    private boolean isValidIpAddress(String host) {
        try {
            // Resolve hostname to IP
            InetAddress address = InetAddress.getByName(host);
            String ip = address.getHostAddress();

            // Check if it's a loopback address
            if (address.isLoopbackAddress()) {
                logger.warn("Blocked loopback address: {}", ip);
                return false;
            }

            // Check if it's a site-local (private) address
            if (address.isSiteLocalAddress()) {
                logger.warn("Blocked site-local (private) address: {}", ip);
                return false;
            }

            // Check if it's a link-local address
            if (address.isLinkLocalAddress()) {
                logger.warn("Blocked link-local address: {}", ip);
                return false;
            }

            // Additional checks for IP string patterns
            if (matchesBlockedIpPattern(ip)) {
                logger.warn("Blocked IP pattern: {}", ip);
                return false;
            }

            return true;

        } catch (UnknownHostException e) {
            logger.warn("Failed to resolve host: {}", host, e);
            return false;
        }
    }

    /**
     * Check if IP matches blocked patterns
     */
    private boolean matchesBlockedIpPattern(String ip) {
        // Check private IP prefixes
        for (String prefix : PRIVATE_IP_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        // Check link-local prefixes
        for (String prefix : LINK_LOCAL_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        // Check loopback prefixes
        for (String prefix : LOOPBACK_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        // Check for 0.0.0.0
        if (ip.equals("0.0.0.0") || ip.equals("::")) {
            return true;
        }

        return false;
    }

    /**
     * Validate port number
     */
    private boolean isValidPort(int port) {
        // Allow standard HTTP/HTTPS ports and common web ports
        // Block potentially dangerous ports
        if (port < 1 || port > 65535) {
            return false;
        }

        // Explicitly blocked dangerous ports
        int[] blockedPorts = {22, 23, 25, 110, 143, 3306, 3389, 5432, 6379, 9200, 27017};
        for (int blockedPort : blockedPorts) {
            if (port == blockedPort) {
                return false;
            }
        }

        // Allowed ports: 80, 443, 8080, 8443, 3000-3999 (common dev ports, excluding blocked ones)
        return port == 80 || port == 443 || port == 8080 || port == 8443 ||
               (port >= 3000 && port <= 3999);
    }

    /**
     * Alias for isValidAndSafe() for convenience
     *
     * @param url The URL to validate
     * @return true if URL is safe, false otherwise
     */
    public boolean isValid(String url) {
        return isValidAndSafe(url);
    }

    /**
     * Extract domain from URL for logging (without sensitive params)
     */
    public String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host : "invalid-url";
        } catch (Exception e) {
            return "invalid-url";
        }
    }
}
