/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.configuration;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Logs a warning at startup for each configuration property that the connector no longer reads but
 * that the environment still sets. Spring Boot ignores an unbound property silently, so without the
 * warning an operator does not see that the setting has no effect. The binder also finds the
 * relaxed forms of a name, such as an environment variable.
 */
public class AgenticAiRemovedPropertiesWarning implements InitializingBean {

  private static final Logger LOG =
      LoggerFactory.getLogger(AgenticAiRemovedPropertiesWarning.class);

  static final String REWRITE_V1_PROVIDER_CONFIG_TO_V2 =
      "camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2";

  private static final List<RemovedProperty> REMOVED_PROPERTIES =
      List.of(
          new RemovedProperty(
              REWRITE_V1_PROVIDER_CONFIG_TO_V2,
              "v1 provider configurations always run on the native providers"));

  private final Environment environment;

  public AgenticAiRemovedPropertiesWarning(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    final var binder = Binder.get(environment);
    for (final var removed : REMOVED_PROPERTIES) {
      if (binder.bind(removed.name(), Bindable.of(String.class)).isBound()) {
        LOG.warn(
            "The configuration property '{}' is removed and has no effect: {}. Remove it from the configuration.",
            removed.name(),
            removed.consequence());
      }
    }
  }

  private record RemovedProperty(String name, String consequence) {}
}
