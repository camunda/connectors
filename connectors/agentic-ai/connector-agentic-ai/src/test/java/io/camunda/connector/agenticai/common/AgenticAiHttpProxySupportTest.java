/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common;

import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTP;
import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.client.jdk.proxy.JdkProxyAuthenticator;
import io.camunda.connector.http.client.client.jdk.proxy.JdkProxySelector;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
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
import software.amazon.awssdk.http.apache.ApacheHttpClient;

class AgenticAiHttpProxySupportTest {

  private static final String PROXY_HOST = "proxy.example.com";
  private static final int PROXY_PORT = 8080;
  private static final String PROXY_USER = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";
  private static final String NON_PROXY_HOST_LOCALHOST_REGEX = "localhost.*";
  private static final String NON_PROXY_HOST_127 = "127\\.0\\.0\\.1";

  @Test
  void shouldConfigureProxyOnHttpClientBuilder() throws Exception {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isPresent().get().isInstanceOf(JdkProxySelector.class);
    assertThat(client.authenticator()).isPresent().get().isInstanceOf(JdkProxyAuthenticator.class);
    var auth =
        client
            .authenticator()
            .get()
            .requestPasswordAuthenticationInstance(
                "proxy.example.com",
                InetAddress.getByName("127.0.0.1"),
                8080,
                "http",
                "Proxy Authentication Required",
                null,
                null,
                Authenticator.RequestorType.PROXY);
    assertThat(auth).isNotNull();
    assertThat(auth.getUserName()).isEqualTo("user");
    assertThat(new String(auth.getPassword())).isEqualTo("pass");
  }

  @Test
  void shouldConfigureProxyWithoutCredentials() throws Exception {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 3128, null, null);
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isPresent().get().isInstanceOf(JdkProxySelector.class);
    // Authenticator is always set when a proxy is configured, but it returns null credentials
    // when none are provided — the actual credential check is handled by JdkProxyAuthenticator
    assertThat(client.authenticator()).isPresent().get().isInstanceOf(JdkProxyAuthenticator.class);
    var auth =
        client
            .authenticator()
            .get()
            .requestPasswordAuthenticationInstance(
                "proxy.example.com",
                InetAddress.getByName("127.0.0.1"),
                3128,
                "http",
                "Proxy Authentication Required",
                null,
                null,
                Authenticator.RequestorType.PROXY);
    assertThat(auth).isNull();
  }

  @Test
  void shouldNotConfigureProxyWhenDisabled() {
    var httpProxySupport = new AgenticAiHttpProxySupport(ProxyConfiguration.NONE);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isEmpty();
    assertThat(client.authenticator()).isEmpty();
  }

  @Test
  void shouldExposeProxyConfiguration() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    assertThat(httpProxySupport.getProxyConfiguration()).isSameAs(proxyConfig);
  }

  @Test
  void shouldExposeJdkHttpClientProxyConfigurator() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    assertThat(httpProxySupport.getJdkHttpClientProxyConfigurator())
        .isNotNull()
        .isInstanceOf(JdkHttpClientProxyConfigurator.class);
  }

  @Test
  void shouldConfigureProxyForHttpOnly() throws Exception {
    ProxyConfiguration httpOnlyProxy =
        protocol -> {
          if (ProxyConfiguration.SCHEME_HTTP.equals(protocol)) {
            return Optional.of(
                new ProxyDetails(
                    ProxyConfiguration.SCHEME_HTTP, "proxy.example.com", 8080, null, null));
          }
          return Optional.empty();
        };
    var httpProxySupport = new AgenticAiHttpProxySupport(httpOnlyProxy);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isPresent().get().isInstanceOf(JdkProxySelector.class);
    assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("http")).isPresent();
    assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("https")).isEmpty();
  }

  @Test
  void okHttpProxyReturnsEmptyWhenNoProxyConfiguredForScheme() {
    var httpProxySupport = new AgenticAiHttpProxySupport(ProxyConfiguration.NONE);

    assertThat(httpProxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTPS)).isEmpty();
  }

  @Test
  void okHttpProxyDerivesProxyWithoutCredentials() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, null, null);
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    var result = httpProxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTPS);

    assertThat(result).isPresent();
    var okHttpProxy = result.get();
    assertThat(okHttpProxy.proxy())
        .isEqualTo(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080)));
    assertThat(okHttpProxy.hasCredentials()).isFalse();
    assertThat(okHttpProxy.username()).isNull();
    assertThat(okHttpProxy.password()).isNull();
  }

  @Test
  void okHttpProxyDerivesProxyWithCredentials() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    var result = httpProxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTPS);

    assertThat(result).isPresent();
    var okHttpProxy = result.get();
    assertThat(okHttpProxy.hasCredentials()).isTrue();
    assertThat(okHttpProxy.username()).isEqualTo("user");
    assertThat(okHttpProxy.password()).isEqualTo("pass");
  }

  @Test
  void okHttpProxyQueriesProxyDetailsForTheRequestedSchemeOnly() {
    ProxyConfiguration httpOnlyProxy =
        protocol -> {
          if (ProxyConfiguration.SCHEME_HTTP.equals(protocol)) {
            return Optional.of(
                new ProxyDetails(
                    ProxyConfiguration.SCHEME_HTTP, "proxy.example.com", 8080, null, null));
          }
          return Optional.empty();
        };
    var httpProxySupport = new AgenticAiHttpProxySupport(httpOnlyProxy);

    assertThat(httpProxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTPS)).isEmpty();
    assertThat(httpProxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTP)).isPresent();
  }

  @Test
  void azureProxyOptionsReturnsEmptyWhenNoProxyConfiguredForScheme() {
    var httpProxySupport = new AgenticAiHttpProxySupport(ProxyConfiguration.NONE);

    assertThat(httpProxySupport.azureProxyOptions(SCHEME_HTTPS)).isEmpty();
  }

  @Test
  void azureProxyOptionsAppliesNonProxyHosts() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, null, null);
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock
          .when(NonProxyHosts::getNonProxyHostsPatterns)
          .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST_REGEX, NON_PROXY_HOST_127));

      var result = httpProxySupport.azureProxyOptions(SCHEME_HTTPS);

      assertThat(result).isPresent();
      var proxyOptions = result.get();
      assertThat(proxyOptions.getAddress())
          .isEqualTo(new InetSocketAddress("proxy.example.com", 8080));
      assertThat(proxyOptions.getNonProxyHosts()).contains("localhost").contains("127");
      assertThat(proxyOptions.getUsername()).isNull();
      assertThat(proxyOptions.getPassword()).isNull();
    }
  }

  @Test
  void azureProxyOptionsAppliesCredentials() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock.when(NonProxyHosts::getNonProxyHostsPatterns).thenReturn(Stream.empty());

      var result = httpProxySupport.azureProxyOptions(SCHEME_HTTPS);

      assertThat(result).isPresent();
      var proxyOptions = result.get();
      assertThat(proxyOptions.getUsername()).isEqualTo("user");
      assertThat(proxyOptions.getPassword()).isEqualTo("pass");
    }
  }

  @Test
  void shouldResolveMultiRegionalDefaultHost() {
    assertThat(AgenticAiHttpProxySupport.defaultGoogleGenAiBaseUrl("us"))
        .isEqualTo("https://aiplatform.us.rep.googleapis.com");
    assertThat(AgenticAiHttpProxySupport.defaultGoogleGenAiBaseUrl("eu"))
        .isEqualTo("https://aiplatform.eu.rep.googleapis.com");
    assertThat(AgenticAiHttpProxySupport.defaultGoogleGenAiBaseUrl("EU"))
        .isEqualTo("https://aiplatform.eu.rep.googleapis.com");
  }

  private static ProxyConfiguration testProxyConfiguration(
      String host, int port, String user, String password) {
    return protocol -> {
      if (ProxyConfiguration.SCHEME_HTTP.equals(protocol)
          || ProxyConfiguration.SCHEME_HTTPS.equals(protocol)) {
        return Optional.of(
            new ProxyDetails(ProxyConfiguration.SCHEME_HTTP, host, port, user, password));
      }
      return Optional.empty();
    };
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CreateAwsHttpClientBuilder {

    @Mock private ProxyConfiguration proxyConfiguration;

    private AgenticAiHttpProxySupport proxySupport;

    @BeforeEach
    void setUp() {
      proxySupport = new AgenticAiHttpProxySupport(proxyConfiguration);
    }

    @Test
    void shouldConfigureProxyForHttpsEndpoint() {
      // given
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

      ApacheHttpClient.Builder httpClientBuilder =
          Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);

      try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
        apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

        // when
        ApacheHttpClient.Builder result =
            proxySupport.createAwsHttpClientBuilder(URI.create("https://bedrock.amazonaws.com"));

        // then
        assertThat(result).isSameAs(httpClientBuilder);
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
        verify(httpClientBuilder)
            .proxyConfiguration(
                notNull(software.amazon.awssdk.http.apache.ProxyConfiguration.class));
      }
    }

    @Test
    void shouldConfigureProxyForHttpEndpoint() {
      // given
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTP)).thenReturn(Optional.empty());

      ApacheHttpClient.Builder httpClientBuilder =
          Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);

      try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
        apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

        // when
        proxySupport.createAwsHttpClientBuilder(URI.create("http://localhost:8080"));

        // then
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTP);
      }
    }

    @Test
    void shouldDefaultToHttpsSchemeWhenEndpointIsNull() {
      // given
      when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

      ApacheHttpClient.Builder httpClientBuilder =
          Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);

      try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
        apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

        // when
        proxySupport.createAwsHttpClientBuilder(null);

        // then
        verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
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
}
