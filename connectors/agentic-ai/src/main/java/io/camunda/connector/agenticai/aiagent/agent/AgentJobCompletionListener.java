/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal lifecycle callback used to thread post-completion hooks from the agent request handler
 * to the connector function.
 *
 * <p>Implementations capture per-execution state (e.g. conversation store, agent context) at the
 * point the response is built. The connector function delegates to it from its SDK-level {@link
 * io.camunda.connector.api.outbound.JobCompletionListener} once Zeebe has accepted or rejected the
 * job completion command.
 */
public interface AgentJobCompletionListener {

  Logger LOGGER = LoggerFactory.getLogger(AgentJobCompletionListener.class);

  /**
   * Chains an arbitrary number of listeners, skipping nulls. Each listener is called regardless of
   * whether a previous one threw; exceptions are logged and swallowed.
   */
  static AgentJobCompletionListener compose(AgentJobCompletionListener... listeners) {
    final var nonNull = Arrays.stream(listeners).filter(Objects::nonNull).toList();
    return switch (nonNull.size()) {
      case 0 -> null;
      case 1 -> nonNull.getFirst();
      default ->
          new AgentJobCompletionListener() {
            @Override
            public void onJobCompleted() {
              for (var listener : nonNull) {
                try {
                  listener.onJobCompleted();
                } catch (Exception e) {
                  LOGGER.error("Unexpected exception in job completion listener", e);
                }
              }
            }

            @Override
            public void onJobCompletionFailed(JobCompletionFailure failure) {
              for (var listener : nonNull) {
                try {
                  listener.onJobCompletionFailed(failure);
                } catch (Exception e) {
                  LOGGER.error("Unexpected exception in job completion failure listener", e);
                }
              }
            }
          };
    };
  }

  default void onJobCompleted() {}

  default void onJobCompletionFailed(JobCompletionFailure failure) {}
}
