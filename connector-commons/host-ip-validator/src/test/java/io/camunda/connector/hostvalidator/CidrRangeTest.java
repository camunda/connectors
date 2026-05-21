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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class CidrRangeTest {

  @Test
  void ipv4SlashEightContainsExpected() throws Exception {
    CidrRange range = CidrRange.parse("10.0.0.0/8");
    assertThat(range.contains(InetAddress.getByName("10.0.0.1"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("10.255.255.255"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("11.0.0.0"))).isFalse();
    assertThat(range.contains(InetAddress.getByName("9.255.255.255"))).isFalse();
  }

  @Test
  void ipv4SlashTwelveBoundary() throws Exception {
    CidrRange range = CidrRange.parse("172.16.0.0/12");
    assertThat(range.contains(InetAddress.getByName("172.16.0.0"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("172.31.255.255"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("172.32.0.0"))).isFalse();
    assertThat(range.contains(InetAddress.getByName("172.15.255.255"))).isFalse();
  }

  @Test
  void ipv6UlaRange() throws Exception {
    CidrRange range = CidrRange.parse("fc00::/7");
    assertThat(range.contains(InetAddress.getByName("fc00::1"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("fd00::1"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("fe00::1"))).isFalse();
  }

  @Test
  void ipv4NotContainedInIpv6Range() throws Exception {
    CidrRange range = CidrRange.parse("fc00::/7");
    assertThat(range.contains(InetAddress.getByName("10.0.0.1"))).isFalse();
  }

  @Test
  void bareAddressTreatedAsHostRoute() throws Exception {
    CidrRange range = CidrRange.parse("1.2.3.4");
    assertThat(range.contains(InetAddress.getByName("1.2.3.4"))).isTrue();
    assertThat(range.contains(InetAddress.getByName("1.2.3.5"))).isFalse();
    assertThat(range.prefixLength()).isEqualTo(32);
  }

  @Test
  void invalidCidrRejected() {
    assertThatThrownBy(() -> CidrRange.parse("10.0.0.0/abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CidrRange.parse("10.0.0.0/40"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CidrRange.parse("not.an.ip/8"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
