package com.example.urlshortener.analytics;

/**
 * Immutable snapshot of the request data needed to record a click, captured on the request thread
 * and handed to the async recorder. Kept separate from the servlet request so nothing servlet-
 * scoped leaks onto the async thread.
 *
 * @param shortCode the code that was resolved
 * @param clientIp best-effort client IP (may be null); hashed, never stored raw
 * @param userAgent the User-Agent header (may be null)
 * @param referer the Referer header (may be null)
 */
public record ClickContext(String shortCode, String clientIp, String userAgent, String referer) {}
