/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import io.camunda.connector.api.error.ConnectorRetryException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class BodyAwareRetryStrategyTest {

  private final BodyAwareRetryStrategy strategy = new BodyAwareRetryStrategy();
  private final FetchResponse rateLimited = new FetchResponse(429, Map.of("retry-after", "1"));

  @Test
  void doesNotRetryRequestsThatStreamABody() {
    var upload =
        new FetchOptions.Builder("https://upload.box.com/api/2.0/files/content", "POST")
            .fileStream(new ByteArrayInputStream(new byte[] {1}))
            .build();

    assertThat(strategy.shouldRetry(upload, rateLimited, 0)).isFalse();
  }

  @Test
  void stillRetriesRequestsWithoutABody() {
    var list = new FetchOptions.Builder("https://api.box.com/2.0/folders/0/items", "GET").build();

    assertThat(strategy.shouldRetry(list, rateLimited, 0)).isTrue();
  }

  @Test
  void closesTheDiscardedBinaryResponseWhenRetrying() {
    var closed = new AtomicBoolean();
    var content =
        new ByteArrayInputStream(new byte[] {1}) {
          @Override
          public void close() {
            closed.set(true);
          }
        };
    var response =
        new FetchResponse.Builder(429, Map.of("retry-after", "1")).content(content).build();
    var download =
        new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET").build();

    assertThat(strategy.shouldRetry(download, response, 0)).isTrue();
    assertThat(closed).isTrue();
  }

  @Test
  void keepsTheFinalBinaryResponseOpenWhenNotRetrying() {
    var closed = new AtomicBoolean();
    var content =
        new ByteArrayInputStream(new byte[] {1}) {
          @Override
          public void close() {
            closed.set(true);
          }
        };
    var response = new FetchResponse.Builder(200, Map.of()).content(content).build();
    var download =
        new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET").build();

    assertThat(strategy.shouldRetry(download, response, 0)).isFalse();
    assertThat(closed).isFalse();
  }

  @Test
  void closesTheBinaryResponseWhenTheDelegateThrows() {
    var closed = new AtomicBoolean();
    var content =
        new ByteArrayInputStream(new byte[] {1}) {
          @Override
          public void close() {
            closed.set(true);
          }
        };
    var response = new FetchResponse.Builder(401, Map.of()).content(content).build();
    var download =
        new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET")
            .auth(new BoxDeveloperTokenAuth("token"))
            .build();

    assertThatThrownBy(() -> strategy.shouldRetry(download, response, 0))
        .isInstanceOf(RuntimeException.class);
    assertThat(closed).isTrue();
  }

  private static final class Tracked extends ByteArrayInputStream {
    final AtomicBoolean closed = new AtomicBoolean();

    Tracked() {
      super(new byte[] {1});
    }

    @Override
    public void close() {
      closed.set(true);
    }
  }

  @Test
  void closesFollowedRedirectResponses() {
    var content = new Tracked();
    var response =
        new FetchResponse.Builder(302, Map.of("location", "https://dl.box.com/x"))
            .content(content)
            .build();
    var download =
        new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET")
            .followRedirects(true)
            .build();

    assertThat(strategy.shouldRetry(download, response, 0)).isFalse();
    assertThat(content.closed).isTrue();
  }

  @Test
  void keepsRedirectResponseOpenWhenNotFollowingRedirects() {
    var content = new Tracked();
    var response =
        new FetchResponse.Builder(302, Map.of("location", "https://dl.box.com/x"))
            .content(content)
            .build();
    var download =
        new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET")
            .followRedirects(false)
            .build();

    assertThat(strategy.shouldRetry(download, response, 0)).isFalse();
    assertThat(content.closed).isFalse();
  }

  @Test
  void throwsRetryExceptionAndClosesWhenDownloadStays202() {
    var content = new Tracked();
    var response = new FetchResponse.Builder(202, Map.of()).content(content).build();
    var download =
        new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET").build();

    assertThatThrownBy(() -> strategy.shouldRetry(download, response, 0))
        .isInstanceOf(ConnectorRetryException.class);
    assertThat(content.closed).isTrue();
  }
}
