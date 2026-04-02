package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.config.ProxyHeaderProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves the real client IP in a safe way:
 * - Only trusts forwarded headers when the immediate sender is a trusted proxy.
 * - Otherwise falls back to request.getRemoteAddr().
 */
@Component
public class ClientIpResolver {

    private final ProxyHeaderProperties proxyHeaderProperties;

    public ClientIpResolver(ProxyHeaderProperties proxyHeaderProperties) {
        this.proxyHeaderProperties = proxyHeaderProperties;
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;

        String remoteAddr = safeTrim(request.getRemoteAddr());
        if (!shouldTrustForwardedHeaders(remoteAddr)) {
            return remoteAddr;
        }

        // Trust boundary confirmed: now read forwarded headers.
        String xForwardedFor = safeTrim(request.getHeader("X-Forwarded-For"));
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // First IP is the original client (per convention); later entries are proxies.
            String first = xForwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) return first;
        }

        String xRealIp = safeTrim(request.getHeader("X-Real-IP"));
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return remoteAddr;
    }

    private boolean shouldTrustForwardedHeaders(String remoteAddr) {
        if (!proxyHeaderProperties.isTrustForwardedHeaders()) return false;
        if (remoteAddr == null || remoteAddr.isBlank()) return false;
        List<String> trusted = proxyHeaderProperties.getTrustedProxies();
        if (trusted == null || trusted.isEmpty()) return false;
        return isIpInAnyCidr(remoteAddr, trusted);
    }

    private static String safeTrim(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Minimal CIDR matcher. Supports IPv4 CIDRs and exact matches; IPv6 entries are treated as exact.
     */
    static boolean isIpInAnyCidr(String ip, List<String> cidrsOrIps) {
        for (String entry : cidrsOrIps) {
            if (entry == null) continue;
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (matchesCidrOrIp(ip, e)) return true;
        }
        return false;
    }

    private static boolean matchesCidrOrIp(String ip, String cidrOrIp) {
        if (cidrOrIp.contains("/")) {
            return matchesIpv4Cidr(ip, cidrOrIp);
        }
        return cidrOrIp.equals(ip);
    }

    private static boolean matchesIpv4Cidr(String ip, String cidr) {
        // Only handle IPv4 CIDR blocks.
        if (ip == null || !ip.contains(".")) return false;
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) return false;
        String network = parts[0].trim();
        String prefixStr = parts[1].trim();
        if (network.isEmpty() || prefixStr.isEmpty()) return false;

        int prefix;
        try {
            prefix = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefix < 0 || prefix > 32) return false;

        try {
            int ipInt = ipv4ToInt(InetAddress.getByName(ip));
            int netInt = ipv4ToInt(InetAddress.getByName(network));
            int mask = prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
            return (ipInt & mask) == (netInt & mask);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static int ipv4ToInt(InetAddress address) {
        byte[] b = address.getAddress();
        // Only IPv4.
        if (b.length != 4) throw new IllegalArgumentException("Not an IPv4 address");
        return ((b[0] & 0xFF) << 24) |
               ((b[1] & 0xFF) << 16) |
               ((b[2] & 0xFF) << 8)  |
               (b[3] & 0xFF);
    }
}

