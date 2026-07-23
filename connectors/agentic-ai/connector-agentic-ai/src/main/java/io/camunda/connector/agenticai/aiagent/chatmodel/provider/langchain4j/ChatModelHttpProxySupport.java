/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import com.azure.core.http.ProxyOptions;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * LangChain4j-specific proxy support layered on top of the provider-neutral {@link
 * HttpTransportSupport}. Keeps the langchain4j {@link JdkHttpClientBuilder} wrapping (which the
 * neutral transport support has no reason to know about) while delegating the actual proxy-aware
 * client/builder construction to {@link HttpTransportSupport}.
 */
public class ChatModelHttpProxySupport {

  private final HttpTransportSupport httpTransportSupport;

  public ChatModelHttpProxySupport(HttpTransportSupport httpTransportSupport) {
    this.httpTransportSupport = httpTransportSupport;
  }

  /**
   * Configures an {@link HttpClient.Builder} with proxy settings, then wraps it in a {@link
   * CloseableJdkHttpClientBuilder}. Callers set timeouts on the returned builder directly (e.g.
   * {@code .connectTimeout(...).readTimeout(...)}), pass it to a langchain4j model builder as the
   * {@code httpClientBuilder}, then pass it to {@link CloseableChatModelDelegate} as the resource
   * to close.
   */
  public CloseableJdkHttpClientBuilder createJdkHttpClientBuilder() {
    return new CloseableJdkHttpClientBuilder(httpTransportSupport.jdkHttpClientBuilder());
  }

  /**
   * A {@link JdkHttpClientBuilder} that wraps a configured {@link HttpClient.Builder} and captures
   * the {@link HttpClient} produced when langchain4j calls {@link HttpClient.Builder#build()}.
   * Extends {@link JdkHttpClientBuilder} so it can be passed directly to langchain4j model
   * builders. Implements {@link AutoCloseable} so it can be passed directly to {@link
   * CloseableChatModelDelegate}.
   *
   * <p>All {@link HttpClient.Builder} method calls are forwarded to the real builder so any
   * configuration langchain4j applies (SSL context, version, executor, etc.) takes effect on the
   * actual client. {@link CapturingBridge#build()} captures the resulting instance for {@link
   * #close()}.
   */
  // @NullUnmarked: builtClient is set via the CapturingBridge callback during build(), not in the
  // constructor.
  @NullUnmarked
  public static final class CloseableJdkHttpClientBuilder extends JdkHttpClientBuilder
      implements AutoCloseable {

    private HttpClient builtClient;

    CloseableJdkHttpClientBuilder(HttpClient.Builder delegate) {
      httpClientBuilder(new CapturingBridge(delegate));
    }

    @Override
    public void close() {
      if (builtClient != null) {
        builtClient.close();
      }
    }

    private final class CapturingBridge implements HttpClient.Builder {
      private final HttpClient.Builder delegate;

      CapturingBridge(HttpClient.Builder delegate) {
        this.delegate = delegate;
      }

      @Override
      public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
        delegate.cookieHandler(cookieHandler);
        return this;
      }

      @Override
      public HttpClient.Builder connectTimeout(Duration duration) {
        delegate.connectTimeout(duration);
        return this;
      }

      @Override
      public HttpClient.Builder sslContext(SSLContext sslContext) {
        delegate.sslContext(sslContext);
        return this;
      }

      @Override
      public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
        delegate.sslParameters(sslParameters);
        return this;
      }

      @Override
      public HttpClient.Builder executor(Executor executor) {
        delegate.executor(executor);
        return this;
      }

      @Override
      public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
        delegate.followRedirects(policy);
        return this;
      }

      @Override
      public HttpClient.Builder version(HttpClient.Version version) {
        delegate.version(version);
        return this;
      }

      @Override
      public HttpClient.Builder priority(int priority) {
        delegate.priority(priority);
        return this;
      }

      @Override
      public HttpClient.Builder proxy(ProxySelector proxySelector) {
        delegate.proxy(proxySelector);
        return this;
      }

      @Override
      public HttpClient.Builder authenticator(Authenticator authenticator) {
        delegate.authenticator(authenticator);
        return this;
      }

      @Override
      public HttpClient build() {
        if (builtClient != null) {
          throw new IllegalStateException("HttpClient has already been built");
        }
        return builtClient = delegate.build();
      }
    }
  }

  public ApacheHttpClient.Builder createAwsHttpClientBuilder(@Nullable URI endpointOverride) {
    return httpTransportSupport.awsHttpClientBuilder(endpointOverride);
  }

  public Optional<ProxyOptions> createAzureProxyOptions(String endpoint) {
    return httpTransportSupport.azureProxyOptions(endpoint);
  }
}
