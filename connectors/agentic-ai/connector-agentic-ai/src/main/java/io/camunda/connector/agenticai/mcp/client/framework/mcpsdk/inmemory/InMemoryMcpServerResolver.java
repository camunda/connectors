/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.ClassUtils;

/**
 * Resolves the {@link InMemoryMcpServer} bean referenced by an {@code IN_MEMORY} MCP client
 * configuration. A class name never causes instantiation: it is only used to look up an existing
 * bean.
 */
public class InMemoryMcpServerResolver {

  private static final String PROPERTY_PREFIX = "camunda.connector.agenticai.mcp.client.clients";

  private final ListableBeanFactory beanFactory;

  public InMemoryMcpServerResolver(ListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  /**
   * @throws IllegalStateException if the referenced server cannot be resolved to exactly one bean
   */
  public InMemoryMcpServer resolve(
      String clientId, InMemoryMcpClientTransportConfiguration config) {
    if (config.serverBeanName() != null) {
      return resolveByBeanName(clientId, config.serverBeanName());
    }
    return resolveByClassName(clientId, Objects.requireNonNull(config.serverClassName()));
  }

  private InMemoryMcpServer resolveByBeanName(String clientId, String beanName) {
    final var property = property(clientId, "server-bean-name");
    try {
      return beanFactory.getBean(beanName, InMemoryMcpServer.class);
    } catch (BeansException e) {
      throw new IllegalStateException(
          "%s: no bean named '%s' of type %s: %s"
              .formatted(property, beanName, InMemoryMcpServer.class.getName(), e.getMessage()),
          e);
    }
  }

  private InMemoryMcpServer resolveByClassName(String clientId, String className) {
    final var property = property(clientId, "server-class-name");

    final Class<?> type;
    try {
      type = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
    } catch (ClassNotFoundException | LinkageError e) {
      throw new IllegalStateException(
          "%s: class '%s' cannot be loaded".formatted(property, className), e);
    }

    if (!InMemoryMcpServer.class.isAssignableFrom(type)) {
      throw new IllegalStateException(
          "%s: class '%s' does not implement %s"
              .formatted(property, className, InMemoryMcpServer.class.getName()));
    }

    final var candidates = beanFactory.getBeanNamesForType(type);
    if (candidates.length != 1) {
      throw new IllegalStateException(
          "%s: expected exactly one bean of class '%s' but found %d%s"
              .formatted(
                  property,
                  className,
                  candidates.length,
                  candidates.length == 0 ? "" : " " + Arrays.toString(candidates)));
    }

    return (InMemoryMcpServer) beanFactory.getBean(candidates[0], type);
  }

  private static String property(String clientId, String field) {
    return "%s.%s.in-memory.%s".formatted(PROPERTY_PREFIX, clientId, field);
  }
}
