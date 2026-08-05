package com.example.urlshortener.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies per-link management tokens (a capability, not user identity).
 *
 * <p>The plaintext token is returned to the creator once; only its SHA-256 hash is persisted.
 * Verification uses a constant-time comparison to avoid leaking information via timing. SHA-256
 * (rather than a slow password hash like bcrypt) is appropriate here because the token is a
 * high-entropy 256-bit random value, not a low-entropy human password — it is not brute-forceable.
 */
@Component
public class ManagementTokenService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  /** A freshly generated token together with its hash (for storage). */
  public record IssuedToken(String token, String hash) {}

  public IssuedToken issue() {
    byte[] raw = new byte[32]; // 256 bits of entropy
    RANDOM.nextBytes(raw);
    String token = URL_ENCODER.encodeToString(raw);
    return new IssuedToken(token, hash(token));
  }

  public String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] out = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(out);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Constant-time check that {@code presentedToken} hashes to {@code expectedHash}. */
  public boolean matches(String presentedToken, String expectedHash) {
    if (presentedToken == null || expectedHash == null) {
      return false;
    }
    byte[] a = hash(presentedToken).getBytes(StandardCharsets.UTF_8);
    byte[] b = expectedHash.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(a, b);
  }
}
