/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Loads the bundled model capability matrix YAML as a low-precedence {@link PropertySource} so
 * library consumers can override any value via their own {@code application.yml}.
 *
 * <p>The bundled file lives at {@code classpath:capabilities/model-capabilities.yaml} and is
 * structured under the {@code camunda.connector.agenticai.aiagent.framework.capabilities} prefix.
 * It is registered with {@code addLast(...)} so any user-supplied source — including {@code
 * application.yml}, environment variables and command-line arguments — wins.
 */
public class CapabilityMatrixEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String BUNDLED_RESOURCE = "capabilities/model-capabilities.yaml";
  private static final String PROPERTY_SOURCE_NAME = "agentic-ai-bundled-capability-matrix";

  private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    final Resource resource = new ClassPathResource(BUNDLED_RESOURCE);
    if (!resource.exists()) {
      return;
    }
    try {
      final List<PropertySource<?>> sources = loader.load(PROPERTY_SOURCE_NAME, resource);
      sources.forEach(environment.getPropertySources()::addLast);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load bundled capability matrix from classpath:" + BUNDLED_RESOURCE, e);
    }
  }
}
