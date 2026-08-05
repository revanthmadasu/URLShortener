package com.example.urlshortener.link;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * Decides whether a destination host resolves into a range we refuse to shorten: loopback,
 * private/site-local, link-local (including the cloud metadata address 169.254.169.254),
 * wildcard, multicast, or IPv6 unique-local. This limits the shortener from being used to reach
 * internal infrastructure (SSRF/open-redirect hardening — risk R3).
 *
 * <p><b>Honest scope.</b> The service 302-redirects the caller's client; it does not fetch the
 * URL itself, so this is defense-in-depth rather than a complete SSRF control. It is also
 * evaluated at creation time, so it cannot prevent <i>DNS rebinding</i> (a name that resolves
 * public now and private at click time). Both limitations are documented in the risk register.
 *
 * <p>The DNS resolver is injected so the range logic is unit-testable without real lookups.
 */
@Component
public class PrivateNetworkGuard {

  /** Seam for tests: resolve a host to addresses (literals resolve without DNS). */
  @FunctionalInterface
  public interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final HostResolver resolver;

  public PrivateNetworkGuard() {
    this(InetAddress::getAllByName);
  }

  public PrivateNetworkGuard(HostResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * @return true if the host is unsafe to shorten. Fails <b>closed</b>: an unresolvable host is
   *     treated as disallowed because its safety cannot be verified.
   */
  public boolean isDisallowed(String host) {
    final InetAddress[] addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException e) {
      return true; // cannot verify → refuse
    }
    for (InetAddress address : addresses) {
      if (isPrivate(address)) {
        return true;
      }
    }
    return false;
  }

  static boolean isPrivate(InetAddress address) {
    return address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isUniqueLocalIpv6(address);
  }

  /** IPv6 unique-local addresses (fc00::/7) are not covered by {@code isSiteLocalAddress}. */
  private static boolean isUniqueLocalIpv6(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
  }
}
