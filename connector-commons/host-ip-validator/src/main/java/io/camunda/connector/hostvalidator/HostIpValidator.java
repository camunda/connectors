/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.hostvalidator;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

/**
 * Resolves a hostname and classifies each resolved IP against allow/deny CIDR ranges. Models the
 * classification used by Stripe's smokescreen ({@code classifyAddr} / {@code safeResolve}) and is
 * intended for SSRF defence in outbound HTTP calls.
 *
 * <p>Unlike smokescreen — which only checks the first resolved IP — this validator inspects every
 * address returned by the resolver, to defend against DNS rebinding / multi-record SSRF.
 */
public final class HostIpValidator {

  /** Standard RFC 1918 (IPv4) and RFC 4193 (IPv6 ULA) private ranges. */
  private static final List<CidrRange> RFC_PRIVATE_RANGES =
      List.of(
          CidrRange.parse("10.0.0.0/8"),
          CidrRange.parse("172.16.0.0/12"),
          CidrRange.parse("192.168.0.0/16"),
          CidrRange.parse("fc00::/7"));

  /**
   * Reserved / special-use ranges that are never a legitimate outbound HTTP target. Includes IPv4
   * gaps not covered by Java's {@code InetAddress.is*} methods (the {@code 0.0.0.0/8} "this
   * network" range — Java only flags the single {@code 0.0.0.0}, while {@code 0.0.0.1} routes to
   * loopback on Linux — and the {@code 240.0.0.0/4} reserved block which contains the {@code
   * 255.255.255.255} limited broadcast). Also includes IPv6 transition mechanisms ({@code
   * 64:ff9b::/96} NAT64, {@code 2002::/16} 6to4, {@code 2001::/32} Teredo) that can encode
   * arbitrary IPv4 addresses — including RFC 1918 — and would otherwise bypass the IPv4 private
   * check entirely. Unlike {@link #RFC_PRIVATE_RANGES} these are <em>not</em> unlocked by the
   * {@code unsafeAllowPrivateRanges} escape hatch; an operator who legitimately needs one of these
   * (e.g. a test harness against TEST-NET) must add it to {@code allowRanges}.
   */
  private static final List<CidrRange> DEFAULT_RESERVED_RANGES =
      List.of(
          // IPv4 — gaps in Java's InetAddress.is* methods
          CidrRange.parse("0.0.0.0/8"), // RFC 1122 "this network" — 0.0.0.1 routes to loopback
          CidrRange.parse("100.64.0.0/10"), // RFC 6598 CGN / shared address space
          CidrRange.parse("192.0.0.0/24"), // RFC 6890 IETF protocol assignments
          CidrRange.parse("192.0.2.0/24"), // RFC 5737 TEST-NET-1
          CidrRange.parse("192.88.99.0/24"), // RFC 7526 deprecated 6to4 relay anycast
          CidrRange.parse("198.18.0.0/15"), // RFC 2544 benchmark
          CidrRange.parse("198.51.100.0/24"), // RFC 5737 TEST-NET-2
          CidrRange.parse("203.0.113.0/24"), // RFC 5737 TEST-NET-3
          CidrRange.parse("240.0.0.0/4"), // RFC 1112 reserved (includes 255.255.255.255 broadcast)
          // IPv6 — transition / discard / documentation prefixes
          CidrRange.parse("64:ff9b::/96"), // RFC 6052 NAT64 (embeds arbitrary IPv4)
          CidrRange.parse("64:ff9b:1::/48"), // RFC 8215 local-use NAT64
          CidrRange.parse("100::/64"), // RFC 6666 discard prefix
          CidrRange.parse("2001::/32"), // RFC 4380 Teredo (embeds IPv4)
          CidrRange.parse("2001:10::/28"), // RFC 4843 ORCHID (deprecated, still reserved)
          CidrRange.parse("2001:20::/28"), // RFC 7343 ORCHIDv2
          CidrRange.parse("2001:db8::/32"), // RFC 3849 documentation
          CidrRange.parse("2002::/16"), // RFC 3056 6to4 (embeds IPv4)
          CidrRange.parse("3fff::/20"), // RFC 9637 documentation
          CidrRange.parse("5f00::/16")); // RFC 9602 segment routing (SRv6)

  private HostIpValidator() {}

  public enum Classification {
    ALLOW_DEFAULT(true),
    ALLOW_USER_CONFIGURED(true),
    DENY_NOT_GLOBAL_UNICAST(false),
    DENY_PRIVATE_RANGE(false),
    DENY_RESERVED_RANGE(false),
    DENY_USER_CONFIGURED(false);

    private final boolean allowed;

    Classification(boolean allowed) {
      this.allowed = allowed;
    }

    public boolean isAllowed() {
      return allowed;
    }
  }

  /**
   * Resolves {@code host} and throws {@link HostDeniedException} if any resolved address is denied.
   *
   * @throws UnknownHostException when the hostname cannot be resolved
   * @throws HostDeniedException when any resolved IP is disallowed
   */
  public static void validate(
      String host,
      List<CidrRange> allowRanges,
      List<CidrRange> denyRanges,
      boolean unsafeAllowPrivateRanges,
      boolean unsafeAllowLoopback)
      throws UnknownHostException {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("host must not be null or blank");
    }
    InetAddress[] resolved = InetAddress.getAllByName(host);
    if (resolved == null || resolved.length == 0) {
      throw new UnknownHostException("No IPs resolved for '" + host + "'");
    }
    List<CidrRange> allow = allowRanges != null ? allowRanges : Collections.emptyList();
    List<CidrRange> deny = denyRanges != null ? denyRanges : Collections.emptyList();
    for (InetAddress addr : resolved) {
      Classification c = classify(addr, allow, deny, unsafeAllowPrivateRanges, unsafeAllowLoopback);
      if (!c.isAllowed()) {
        throw new HostDeniedException(host, addr, c);
      }
    }
  }

  /**
   * Classifies a single resolved {@link InetAddress}. Mirrors smokescreen's {@code classifyAddr}.
   */
  public static Classification classify(
      InetAddress address,
      List<CidrRange> allowRanges,
      List<CidrRange> denyRanges,
      boolean unsafeAllowPrivateRanges,
      boolean unsafeAllowLoopback) {
    boolean inAllow = anyContains(allowRanges, address);
    if (!isGlobalUnicast(address) || (!unsafeAllowLoopback && address.isLoopbackAddress())) {
      return inAllow
          ? Classification.ALLOW_USER_CONFIGURED
          : Classification.DENY_NOT_GLOBAL_UNICAST;
    }
    if (inAllow) {
      return Classification.ALLOW_USER_CONFIGURED;
    }
    if (anyContains(denyRanges, address)) {
      return Classification.DENY_USER_CONFIGURED;
    }
    if (isReserved(address)) {
      return Classification.DENY_RESERVED_RANGE;
    }
    if (isPrivate(address) && !unsafeAllowPrivateRanges) {
      return Classification.DENY_PRIVATE_RANGE;
    }
    return Classification.ALLOW_DEFAULT;
  }

  private static boolean anyContains(List<CidrRange> ranges, InetAddress address) {
    if (ranges == null || ranges.isEmpty()) {
      return false;
    }
    for (CidrRange range : ranges) {
      if (range.contains(address)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Approximates Go's {@code net.IP.IsGlobalUnicast}: excludes the unspecified address, multicast,
   * and link-local addresses. Loopback is intentionally <em>not</em> excluded here — callers handle
   * loopback separately, matching smokescreen.
   */
  private static boolean isGlobalUnicast(InetAddress address) {
    return !address.isAnyLocalAddress()
        && !address.isMulticastAddress()
        && !address.isLinkLocalAddress();
  }

  /** RFC 1918 for IPv4 and RFC 4193 ULA for IPv6. */
  private static boolean isPrivate(InetAddress address) {
    if (address instanceof Inet4Address || address instanceof Inet6Address) {
      return anyContains(RFC_PRIVATE_RANGES, address);
    }
    return false;
  }

  /**
   * Reserved / special-use ranges that should never be a legitimate outbound target. See {@link
   * #DEFAULT_RESERVED_RANGES} for the full list and rationale.
   */
  private static boolean isReserved(InetAddress address) {
    if (address instanceof Inet4Address || address instanceof Inet6Address) {
      return anyContains(DEFAULT_RESERVED_RANGES, address);
    }
    return false;
  }
}
