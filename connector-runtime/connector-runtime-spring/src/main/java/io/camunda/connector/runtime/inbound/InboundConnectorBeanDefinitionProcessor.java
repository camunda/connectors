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
package io.camunda.connector.runtime.inbound;

import static org.springframework.beans.factory.config.BeanDefinition.*;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.env.Environment;

public class InboundConnectorBeanDefinitionProcessor
    implements BeanDefinitionRegistryPostProcessor, BeanPostProcessor {
  private static final Logger LOG =
      LoggerFactory.getLogger(InboundConnectorBeanDefinitionProcessor.class);
  private final List<InboundConnectorConfiguration> preparedConfigurations = new ArrayList<>();

  private final Environment environment;

  public InboundConnectorBeanDefinitionProcessor(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
      throws BeansException {
    ListableBeanFactory listableBeanFactory = (ListableBeanFactory) registry;
    String[] handlerBeans =
        listableBeanFactory.getBeanNamesForType(InboundConnectorExecutable.class, true, false);
    LOG.info("Found inbound connector beans: {}", (Object) handlerBeans);
    for (String beanName : handlerBeans) {
      BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
      InboundConnector inboundConnectorAnnotation =
          listableBeanFactory.findAnnotationOnBean(beanName, InboundConnector.class);
      if (inboundConnectorAnnotation != null) {
        InboundConnectorConfiguration configuration =
            getProperties(
                inboundConnectorAnnotation, beanDefinition, beanName, listableBeanFactory);
        preparedConfigurations.add(configuration);
      }
    }
  }

  private InboundConnectorConfiguration getProperties(
      InboundConnector inboundConnector,
      BeanDefinition beanDefinition,
      String beanName,
      BeanFactory beanFactory) {
    var scope = beanDefinition.getScope();
    if (!SCOPE_PROTOTYPE.equals(scope)) {
      throw new IllegalStateException(
          "Only \""
              + SCOPE_PROTOTYPE
              + "\" scope is supported for inbound connectors but found: \""
              + scope
              + "\" for bean: "
              + beanDefinition);
    } else {
      return getInboundConnectorConfiguration(inboundConnector, beanName, beanFactory);
    }
  }

  private InboundConnectorConfiguration getInboundConnectorConfiguration(
      InboundConnector inboundConnector, String beanName, BeanFactory beanFactory) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(inboundConnector.name(), environment::getProperty);
    var deduplicationProperties = Arrays.asList(inboundConnector.deduplicationProperties());

    var configuration =
        new InboundConnectorConfiguration(
            inboundConnector.name(),
            configurationOverrides.typeOverride().orElse(inboundConnector.type()),
            null,
            () -> beanFactory.getBean(beanName, InboundConnectorExecutable.class),
            deduplicationProperties);
    LOG.debug("Creating inbound connector configuration from bean {}: {}", beanName, configuration);
    return configuration;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof InboundConnectorFactory inboundConnectorFactory) {
      LOG.info(
          "Configuring {} inbound connectors from bean context", preparedConfigurations.size());
      preparedConfigurations.forEach(inboundConnectorFactory::registerConfiguration);
    }
    return bean;
  }
}
