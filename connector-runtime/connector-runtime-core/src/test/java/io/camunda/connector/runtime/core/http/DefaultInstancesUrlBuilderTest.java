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
package io.camunda.connector.runtime.core.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

public class DefaultInstancesUrlBuilderTest {

  private final DnsResolver dnsResolver = (host) -> new String[] {"10.0.0.10", "10.0.0.11"};
  private final DnsResolver faultyDnsResolver =
      (host) -> {
        throw new RuntimeException("Host not found!");
      };
  private final DnsResolver emptyDnsResolver =
      (host) -> {
        return new String[] {};
      };
  private static final String HEADLESS_SERVICE_URL = "http://headless-service-url:8080";

  @Test
  public void shouldReturnUrls_whenDnsResolverReturnsIps() {
    // given
    DefaultInstancesUrlBuilder urlBuilder =
        new DefaultInstancesUrlBuilder(8080, HEADLESS_SERVICE_URL, dnsResolver);

    // when
    List<String> urls = urlBuilder.buildUrls("test/path");

    // then
    assertThat(urls).hasSize(2);
    assertThat(urls.get(0)).isEqualTo("http://10.0.0.10:8080/test/path");
    assertThat(urls.get(1)).isEqualTo("http://10.0.0.11:8080/test/path");
  }

  @Test
  public void shouldThrowException_whenHostNotFound() {
    assertThatThrownBy(
            () ->
                new DefaultInstancesUrlBuilder(8080, HEADLESS_SERVICE_URL, faultyDnsResolver)
                    .buildUrls("test/path"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("An error occurred while resolving hostname: headless-service-url");
  }

  @Test
  public void shouldThrowException_whenReturnEmptyAddresses() {
    assertThatThrownBy(
            () ->
                new DefaultInstancesUrlBuilder(8080, HEADLESS_SERVICE_URL, emptyDnsResolver)
                    .buildUrls("test/path"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to resolve hostname: headless-service-url");
  }
}
