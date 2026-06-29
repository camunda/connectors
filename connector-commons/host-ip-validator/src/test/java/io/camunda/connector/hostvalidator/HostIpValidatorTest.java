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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.hostvalidator.HostIpValidator.Classification;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostIpValidatorTest {

  @Test
  void publicAddressIsAllowedByDefault() throws Exception {
    InetAddress addr = InetAddress.getByName("8.8.8.8");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.ALLOW_DEFAULT);
  }

  @Test
  void loopbackIsDeniedAsNotGlobalUnicast() throws Exception {
    InetAddress addr = InetAddress.getByName("127.0.0.1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_NOT_GLOBAL_UNICAST);
  }

  @Test
  void ipv6LoopbackIsDeniedAsNotGlobalUnicast() throws Exception {
    InetAddress addr = InetAddress.getByName("::1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_NOT_GLOBAL_UNICAST);
  }

  @Test
  void linkLocalIsDeniedAsNotGlobalUnicast() throws Exception {
    // 169.254.0.0/16 — IMDS lives here on AWS/GCP/Azure.
    InetAddress addr = InetAddress.getByName("169.254.169.254");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_NOT_GLOBAL_UNICAST);
  }

  @Test
  void multicastIsDeniedAsNotGlobalUnicast() throws Exception {
    InetAddress addr = InetAddress.getByName("224.0.0.1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_NOT_GLOBAL_UNICAST);
  }

  @Test
  void rfc1918IsDeniedAsPrivateRange() throws Exception {
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("10.0.0.5"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_PRIVATE_RANGE);
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("172.16.5.5"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_PRIVATE_RANGE);
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("192.168.1.1"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_PRIVATE_RANGE);
  }

  @Test
  void ipv6UlaIsDeniedAsPrivateRange() throws Exception {
    InetAddress addr = InetAddress.getByName("fc00::1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_PRIVATE_RANGE);
  }

  @Test
  void ipv4MappedPrivateAddressIsDeniedAsPrivateRange() throws Exception {
    // ::ffff:10.0.0.1 — IPv4-mapped form of 10.0.0.1. Must not bypass the RFC 1918 check.
    byte[] mapped = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 10, 0, 0, 1};
    InetAddress addr = Inet6Address.getByAddress(null, mapped, 0);
    assertThat(addr).isInstanceOf(Inet6Address.class);
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_PRIVATE_RANGE);
  }

  @Test
  void privateRangeIsAllowedWhenUnsafeFlagSet() throws Exception {
    InetAddress addr = InetAddress.getByName("10.0.0.5");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), true, false))
        .isEqualTo(Classification.ALLOW_DEFAULT);
  }

  @Test
  void ipv4ThisNetworkRangeIsDeniedAsReserved() throws Exception {
    // Java's isAnyLocalAddress() only matches 0.0.0.0 exactly, but 0.0.0.1 routes to loopback on
    // Linux — must be blocked by the reserved-range check.
    InetAddress addr = InetAddress.getByName("0.0.0.1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void ipv4LimitedBroadcastIsDeniedAsReserved() throws Exception {
    // 255.255.255.255 isn't flagged by any Java is* method.
    InetAddress addr = InetAddress.getByName("255.255.255.255");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void ipv4CgnSharedAddressIsDeniedAsReserved() throws Exception {
    InetAddress addr = InetAddress.getByName("100.64.0.1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void ipv4TestNetIsDeniedAsReserved() throws Exception {
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("192.0.2.1"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("198.51.100.1"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("203.0.113.1"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void ipv6TransitionPrefixesAreDeniedAsReserved() throws Exception {
    // 2002::/16 (6to4) — 2002:0a00:0001:: would decode to embedded IPv4 10.0.0.1.
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("2002:0a00:0001::"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
    // 64:ff9b::/96 (NAT64) — 64:ff9b::10.0.0.1 embeds IPv4 10.0.0.1.
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("64:ff9b::10.0.0.1"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
    // 2001::/32 (Teredo).
    assertThat(
            HostIpValidator.classify(
                InetAddress.getByName("2001::1"), List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void ipv6DocumentationRangeIsDeniedAsReserved() throws Exception {
    InetAddress addr = InetAddress.getByName("2001:db8::1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), false, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void reservedRangeIsNotUnlockedByUnsafeAllowPrivateRanges() throws Exception {
    // The unsafeAllowPrivateRanges escape hatch is for RFC 1918, not for reserved/special-use
    // ranges — those must stay blocked.
    InetAddress addr = InetAddress.getByName("192.0.2.1");
    assertThat(HostIpValidator.classify(addr, List.of(), List.of(), true, false))
        .isEqualTo(Classification.DENY_RESERVED_RANGE);
  }

  @Test
  void reservedRangeIsOverriddenByExplicitAllow() throws Exception {
    // An operator can still allow a reserved range if they have a legitimate need (e.g. a test
    // harness pointing at TEST-NET).
    InetAddress addr = InetAddress.getByName("198.18.0.1");
    List<CidrRange> allow = List.of(CidrRange.parse("198.18.0.0/15"));
    assertThat(HostIpValidator.classify(addr, allow, List.of(), false, false))
        .isEqualTo(Classification.ALLOW_USER_CONFIGURED);
  }

  @Test
  void userConfiguredDenyOverridesAllowDefault() throws Exception {
    InetAddress addr = InetAddress.getByName("8.8.8.8");
    List<CidrRange> deny = List.of(CidrRange.parse("8.8.8.0/24"));
    assertThat(HostIpValidator.classify(addr, List.of(), deny, false, false))
        .isEqualTo(Classification.DENY_USER_CONFIGURED);
  }

  @Test
  void allowRangeOverridesPrivateAndDeny() throws Exception {
    InetAddress privateAddr = InetAddress.getByName("10.0.0.5");
    List<CidrRange> allow = List.of(CidrRange.parse("10.0.0.0/8"));
    List<CidrRange> deny = List.of(CidrRange.parse("10.0.0.0/16"));
    assertThat(HostIpValidator.classify(privateAddr, allow, deny, false, false))
        .isEqualTo(Classification.ALLOW_USER_CONFIGURED);
  }

  @Test
  void allowRangeOverridesLoopback() throws Exception {
    InetAddress addr = InetAddress.getByName("127.0.0.1");
    List<CidrRange> allow = List.of(CidrRange.parse("127.0.0.0/8"));
    assertThat(HostIpValidator.classify(addr, allow, List.of(), false, false))
        .isEqualTo(Classification.ALLOW_USER_CONFIGURED);
  }

  @Test
  void allowRangeOverridesLoopbackValidation() throws Exception {
    List<CidrRange> allow = List.of(CidrRange.parse("127.0.0.0/8"));
    HostIpValidator.validate("127.0.0.1", allow, List.of(), false, false);
  }

  @Test
  void hostnameValidationRejectsLoopback() {
    assertThatExceptionOfType(HostDeniedException.class)
        .isThrownBy(() -> HostIpValidator.validate("localhost", List.of(), List.of(), false, false))
        .satisfies(
            ex ->
                assertThat(ex.classification()).isEqualTo(Classification.DENY_NOT_GLOBAL_UNICAST));
  }

  @Test
  void hostnameValidationRejectsLiteralPrivateIp() {
    assertThatThrownBy(
            () -> HostIpValidator.validate("10.0.0.1", List.of(), List.of(), false, false))
        .isInstanceOf(HostDeniedException.class);
  }

  @Test
  void hostnameValidationRejectsUnknownHost() {
    assertThatThrownBy(
            () ->
                HostIpValidator.validate(
                    "no-such-host.invalid.camunda.test", List.of(), List.of(), false, false))
        .isInstanceOf(UnknownHostException.class);
  }

  @Test
  void blankHostnameRejected() {
    assertThatThrownBy(() -> HostIpValidator.validate("", List.of(), List.of(), false, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HostIpValidator.validate(null, List.of(), List.of(), false, false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
