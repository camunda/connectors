/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTP;
import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.ProxyOptions;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

@ExtendWith(MockitoExtension.class)
class ChatModelHttpProxySupportTest {

  private static final String PROXY_HOST = "proxy.example.com";
  private static final int PROXY_PORT = 8080;
  private static final String PROXY_USER = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";

  private static final String HTTPS_ENDPOINT = "https://example.com";
  private static final String HTTP_ENDPOINT = "http://example.com";

  private static final String NON_PROXY_HOST_LOCALHOST = "localhost";
  private static final String NON_PROXY_HOST_LOCALHOST_REGEX = "localhost.*";
  private static final String NON_PROXY_HOST_127 = "127\\.0\\.0\\.1";
  private static final String NON_PROXY_HOST_INTERNAL = "*.internal.com";

  @Mock private JdkHttpClientProxyConfigurator proxyConfigurator;
  @Mock private ProxyConfiguration proxyConfiguration;

  private ChatModelHttpProxySupport proxySupport;

  @BeforeEach
  void setUp() {
    proxySupport = new ChatModelHttpProxySupport(proxyConfigurator);
  }

  @Nested
  class CreateJdkHttpClientBuilder {

    @Test
    void shouldCreateJdkHttpClientBuilderWithProxyConfiguration() {
      // when
      JdkHttpClientBuilder result = proxySupport.createJdkHttpClientBuilder();

      // then
      assertThat(result).isNotNull();
      verify(proxyConfigurator).configure(any(HttpClient.Builder.class));
    }
  }

  @Nested
  class CreateAwsHttpClient {

    @BeforeEach
    void setUp() {
      when(proxyConfigurator.getProxyConfiguration()).thenReturn(proxyConfiguration);
    }

    @Test
    void shouldCreateAwsHttpClient() {
      // given
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

      ApacheHttpClient.Builder httpClientBuilder =
          Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);
      when(httpClientBuilder.build()).thenReturn(Mockito.mock(SdkHttpClient.class));

      try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
        apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

        // when
        SdkHttpClient result = proxySupport.createAwsHttpClient(SCHEME_HTTPS);

        // then
        assertThat(result).isNotNull();
        verify(httpClientBuilder)
            .proxyConfiguration(
                notNull(software.amazon.awssdk.http.apache.ProxyConfiguration.class));
      }
    }

    @Test
    void shouldCreateAwsProxyConfigurationWithoutProxy() {
      // given
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

      // when
      var result = proxySupport.createAwsProxyConfiguration(SCHEME_HTTPS);

      // then
      assertThat(result).isNotNull();
      assertThat(result.host()).isNull();
      assertThat(result.username()).isNull();
      assertThat(result.password()).isNull();
      // Default scheme is HTTP when no proxy configured and useSystemPropertyValues is true
      assertThat(result.scheme()).isEqualTo(SCHEME_HTTP);
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
    }

    @Test
    void shouldCreateAwsProxyConfigurationWithProxy() {
      // given
      var proxyDetails = new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        nonProxyHostsMock
            .when(NonProxyHosts::getNonProxyHostRegexPatterns)
            .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST_REGEX, NON_PROXY_HOST_127));

        // when
        var result = proxySupport.createAwsProxyConfiguration(SCHEME_HTTPS);

        // then
        assertThat(result).isNotNull();
        assertThat(result.host()).isEqualTo(PROXY_HOST);
        assertThat(result.port()).isEqualTo(PROXY_PORT);
        assertThat(result.scheme()).isEqualTo(SCHEME_HTTPS);
        assertThat(result.nonProxyHosts())
            .containsExactlyInAnyOrder(NON_PROXY_HOST_LOCALHOST_REGEX, NON_PROXY_HOST_127);
        assertThat(result.username()).isNull();
        assertThat(result.password()).isNull();
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
        nonProxyHostsMock.verify(NonProxyHosts::getNonProxyHostRegexPatterns);
      }
    }

    @Test
    void shouldCreateAwsProxyConfigurationProxyAndCredentials() {
      // given
      var proxyDetails =
          new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASSWORD);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        nonProxyHostsMock
            .when(NonProxyHosts::getNonProxyHostRegexPatterns)
            .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST_REGEX));

        // when
        var result = proxySupport.createAwsProxyConfiguration(SCHEME_HTTPS);

        // then
        assertThat(result).isNotNull();
        assertThat(result.host()).isEqualTo(PROXY_HOST);
        assertThat(result.port()).isEqualTo(PROXY_PORT);
        assertThat(result.scheme()).isEqualTo(SCHEME_HTTPS);
        assertThat(result.nonProxyHosts()).containsExactly(NON_PROXY_HOST_LOCALHOST_REGEX);
        assertThat(result.username()).isEqualTo(PROXY_USER);
        assertThat(result.password()).isEqualTo(PROXY_PASSWORD);
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
      }
    }

    @Test
    void shouldHandleHttpScheme() {
      // given
      var proxyDetails = new ProxyDetails(SCHEME_HTTP, PROXY_HOST, PROXY_PORT, null, null);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTP)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        nonProxyHostsMock
            .when(NonProxyHosts::getNonProxyHostRegexPatterns)
            .thenReturn(Stream.empty());

        // when
        var result = proxySupport.createAwsProxyConfiguration(SCHEME_HTTP);

        // then
        assertThat(result).isNotNull();
        assertThat(result.host()).isEqualTo(PROXY_HOST);
        assertThat(result.port()).isEqualTo(PROXY_PORT);
        assertThat(result.scheme()).isEqualTo(SCHEME_HTTP);
        assertThat(result.nonProxyHosts()).isEmpty();
        assertThat(result.username()).isNull();
        assertThat(result.password()).isNull();
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTP);
      }
    }
  }

  @Nested
  class CreateAzureProxyOptions {

    @BeforeEach
    void setUp() {
      when(proxyConfigurator.getProxyConfiguration()).thenReturn(proxyConfiguration);
    }

    @Test
    void shouldReturnEmptyWhenNoProxyConfigured() {
      // given
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

      // when
      Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTPS_ENDPOINT);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldCreateProxyOptionsWithProxy() {
      // given
      var proxyDetails = new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        nonProxyHostsMock
            .when(NonProxyHosts::getNonProxyHostsPatterns)
            .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST, NON_PROXY_HOST_INTERNAL));

        // when
        Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTPS_ENDPOINT);

        // then
        assertThat(result).isPresent();
        ProxyOptions proxyOptions = result.get();
        assertThat(proxyOptions.getType()).isEqualTo(ProxyOptions.Type.HTTP);
        assertThat(proxyOptions.getAddress().getHostName()).isEqualTo(PROXY_HOST);
        assertThat(proxyOptions.getAddress().getPort()).isEqualTo(PROXY_PORT);
        // Non-proxy hosts are joined with pipe separator
        String nonProxyHosts = proxyOptions.getNonProxyHosts();
        assertThat(nonProxyHosts).isNotEmpty();
        assertThat(nonProxyHosts.split("\\|")).hasSize(2);
        // Credentials should not be set
        assertThat(proxyOptions.getUsername()).isNull();
      }
    }

    @Test
    void shouldCreateProxyOptionsWithProxyAndCredentials() {
      // given
      var proxyDetails =
          new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASSWORD);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        nonProxyHostsMock
            .when(NonProxyHosts::getNonProxyHostsPatterns)
            .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST));

        // when
        Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTPS_ENDPOINT);

        // then
        assertThat(result).isPresent();
        ProxyOptions proxyOptions = result.get();
        assertThat(proxyOptions.getType()).isEqualTo(ProxyOptions.Type.HTTP);
        assertThat(proxyOptions.getAddress().getHostName()).isEqualTo(PROXY_HOST);
        assertThat(proxyOptions.getAddress().getPort()).isEqualTo(PROXY_PORT);
        assertThat(proxyOptions.getNonProxyHosts()).isNotEmpty();
        // Verify credentials were set correctly
        assertThat(proxyOptions.getUsername()).isEqualTo(PROXY_USER);
        assertThat(proxyOptions.getPassword()).isEqualTo(PROXY_PASSWORD);
      }
    }

    @Test
    void shouldHandleHttpScheme() {
      // given
      var proxyDetails = new ProxyDetails(SCHEME_HTTP, PROXY_HOST, PROXY_PORT, null, null);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTP)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        nonProxyHostsMock.when(NonProxyHosts::getNonProxyHostsPatterns).thenReturn(Stream.empty());

        // when
        Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTP_ENDPOINT);

        // then
        assertThat(result).isPresent();
        ProxyOptions proxyOptions = result.get();
        assertThat(proxyOptions.getType()).isEqualTo(ProxyOptions.Type.HTTP);
        assertThat(proxyOptions.getAddress().getHostName()).isEqualTo(PROXY_HOST);
        assertThat(proxyOptions.getAddress().getPort()).isEqualTo(PROXY_PORT);
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTP);
      }
    }

    @Test
    void shouldThrowExceptionWhenEndpointHasNoScheme() {
      // when/then
      String endpointWithoutScheme = "example.com";
      assertThatThrownBy(() -> proxySupport.createAzureProxyOptions(endpointWithoutScheme))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid endpoint URI: " + endpointWithoutScheme);
    }

    @Test
    void shouldHandleDuplicateNonProxyHosts() {
      // given
      var proxyDetails = new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

      try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
        // Simulate duplicates that should be removed by .distinct()
        nonProxyHostsMock
            .when(NonProxyHosts::getNonProxyHostsPatterns)
            .thenReturn(
                Stream.of(
                    NON_PROXY_HOST_LOCALHOST,
                    NON_PROXY_HOST_LOCALHOST,
                    NON_PROXY_HOST_INTERNAL,
                    NON_PROXY_HOST_LOCALHOST));

        // when
        Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTPS_ENDPOINT);

        // then
        assertThat(result).isPresent();
        ProxyOptions proxyOptions = result.get();
        // Verify duplicates were removed - nonProxyHosts should contain unique patterns only
        String nonProxyHosts = proxyOptions.getNonProxyHosts();
        assertThat(nonProxyHosts).isNotEmpty();
        // After distinct(), should only have 2 unique patterns
        String[] patterns = nonProxyHosts.split("\\|");
        assertThat(patterns).hasSize(2);
      }
    }
  }
}
