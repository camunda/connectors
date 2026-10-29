/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.Resource;
import io.camunda.connector.managedscript.ScriptResources.ResourceNotVisibleException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ScriptResourcesTest {

  private static final long KEY = 7L;

  private final CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);

  @Test
  void fetchesTheNameAndTheContentOfTheScript() throws Exception {
    final var metadata = mock(Resource.class);
    when(metadata.getResourceName()).thenReturn("sum.py");
    answer(client.newResourceGetRequest(KEY).send(), metadata);
    answer(client.newResourceContentBinaryGetRequest(KEY).send(), "print(1)");

    final var script = resources(Duration.ofSeconds(1)).get(client, "default", KEY);

    assertThat(script.name()).isEqualTo("sum.py");
    assertThat(new String(script.content(), StandardCharsets.UTF_8)).isEqualTo("print(1)");
  }

  @Test
  void stopsWaitingForASlowRequestAtTheVisibilityTimeout() throws Exception {
    final CamundaFuture<Resource> slow = mock();
    when(slow.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
    when(client.newResourceGetRequest(KEY).send()).thenReturn(slow);

    assertThatThrownBy(() -> resources(Duration.ofMillis(200)).get(client, "default", KEY))
        .isInstanceOf(ResourceNotVisibleException.class);
    verify(slow, atLeastOnce()).cancel(true);
  }

  @Test
  void pollsANotFoundScriptUntilTheVisibilityTimeout() throws Exception {
    final CamundaFuture<Resource> notFound = mock();
    when(notFound.get(anyLong(), any(TimeUnit.class)))
        .thenThrow(new ExecutionException(new ClientHttpException(404, "not found")));
    when(client.newResourceGetRequest(KEY).send()).thenReturn(notFound);

    final long start = System.nanoTime();
    assertThatThrownBy(() -> resources(Duration.ofMillis(200)).get(client, "default", KEY))
        .isInstanceOf(ResourceNotVisibleException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void givesEachFetchWhatIsLeftOfTheVisibilityTimeout() {
    final List<Duration> timeouts = new ArrayList<>();
    final var resources =
        new ScriptResources(
            1,
            (camundaClient, resourceKey, timeout) -> {
              timeouts.add(timeout);
              throw new ResourceNotVisibleException(resourceKey, null);
            },
            Duration.ofMillis(200),
            Duration.ofMillis(20));

    assertThatThrownBy(() -> resources.get(client, "default", KEY))
        .isInstanceOf(ResourceNotVisibleException.class);
    assertThat(timeouts).isNotEmpty().allMatch(t -> t.compareTo(Duration.ofMillis(200)) <= 0);
    assertThat(timeouts).isSortedAccordingTo((a, b) -> b.compareTo(a));
  }

  private static ScriptResources resources(Duration visibilityTimeout) {
    return new ScriptResources(
        1, ScriptResources::fetchFromCamunda, visibilityTimeout, Duration.ofMillis(20));
  }

  @SuppressWarnings("unchecked")
  private static <T> void answer(CamundaFuture<?> request, T value) throws Exception {
    when(((CamundaFuture<T>) request).get(anyLong(), any(TimeUnit.class))).thenReturn(value);
  }
}
