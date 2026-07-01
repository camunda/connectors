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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Immutable CIDR range (IPv4 or IPv6). Parsed from notation such as {@code 10.0.0.0/8} or {@code
 * fc00::/7}. A bare address (no {@code /prefix}) is treated as a host route ({@code /32} for IPv4,
 * {@code /128} for IPv6).
 */
public final class CidrRange {

  private final byte[] networkBytes;
  private final int prefixLength;
  private final String original;

  private CidrRange(byte[] networkBytes, int prefixLength, String original) {
    this.networkBytes = networkBytes;
    this.prefixLength = prefixLength;
    this.original = original;
  }

  public static CidrRange parse(String cidr) {
    Objects.requireNonNull(cidr, "cidr must not be null");
    String address;
    int prefix;
    int slash = cidr.indexOf('/');
    if (slash < 0) {
      address = cidr;
      prefix = -1;
    } else {
      address = cidr.substring(0, slash);
      try {
        prefix = Integer.parseInt(cidr.substring(slash + 1));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid CIDR prefix in '" + cidr + "'", e);
      }
    }

    InetAddress parsed;
    try {
      parsed = InetAddress.getByName(address);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid CIDR address in '" + cidr + "'", e);
    }

    byte[] bytes = parsed.getAddress();
    int maxPrefix = bytes.length * 8;
    if (prefix < 0) {
      prefix = maxPrefix;
    }
    if (prefix > maxPrefix) {
      throw new IllegalArgumentException(
          "CIDR prefix " + prefix + " exceeds maximum " + maxPrefix + " in '" + cidr + "'");
    }

    maskInPlace(bytes, prefix);
    return new CidrRange(bytes, prefix, cidr);
  }

  public boolean contains(InetAddress address) {
    byte[] target = unmapIPv4(address.getAddress());
    if (target.length != networkBytes.length) {
      return false;
    }
    int fullBytes = prefixLength / 8;
    for (int i = 0; i < fullBytes; i++) {
      if (target[i] != networkBytes[i]) {
        return false;
      }
    }
    int remainingBits = prefixLength % 8;
    if (remainingBits == 0) {
      return true;
    }
    int mask = 0xFF << (8 - remainingBits);
    return (target[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
  }

  public int prefixLength() {
    return prefixLength;
  }

  @Override
  public String toString() {
    return original;
  }

  /**
   * Unmaps IPv4-mapped IPv6 addresses ({@code ::ffff:a.b.c.d}) to their 4-byte IPv4 form so a
   * 16-byte representation of an IPv4 address still matches IPv4 CIDR ranges. Without this, an
   * attacker could bypass an IPv4 private-range check by supplying e.g. {@code [::ffff:10.0.0.1]}.
   */
  private static byte[] unmapIPv4(byte[] bytes) {
    if (bytes.length != 16) {
      return bytes;
    }
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return bytes;
      }
    }
    if (bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) {
      return bytes;
    }
    byte[] ipv4 = new byte[4];
    System.arraycopy(bytes, 12, ipv4, 0, 4);
    return ipv4;
  }

  private static void maskInPlace(byte[] bytes, int prefix) {
    int fullBytes = prefix / 8;
    int remainingBits = prefix % 8;
    if (remainingBits != 0 && fullBytes < bytes.length) {
      int mask = 0xFF << (8 - remainingBits);
      bytes[fullBytes] = (byte) (bytes[fullBytes] & mask);
      fullBytes++;
    }
    for (int i = fullBytes; i < bytes.length; i++) {
      bytes[i] = 0;
    }
  }
}
