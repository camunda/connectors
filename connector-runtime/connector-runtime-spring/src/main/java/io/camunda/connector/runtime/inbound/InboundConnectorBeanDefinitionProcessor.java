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
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.inbound.util.AnnotationUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class InboundConnectorBeanDefinitionProcessor
    implements BeanDefinitionRegistryPostProcessor {
  private static final Logger LOG =
      LoggerFactory.getLogger(InboundConnectorBeanDefinitionProcessor.class);
  private final List<InboundConnectorProperties> preparedProperties = new ArrayList<>();

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
      throws BeansException {
    ListableBeanFactory listableBeanFactory = (ListableBeanFactory) registry;
    String[] handlerBeans =
        listableBeanFactory.getBeanNamesForType(InboundConnectorExecutable.class);
    LOG.info("Found inbound connector beans: {}", (Object) handlerBeans);
    for (String beanName : handlerBeans) {
      BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
      InboundConnector inboundConnectorAnnotation = findInboundConnectorAnnotation(beanDefinition);
      if (inboundConnectorAnnotation != null) {
        InboundConnectorProperties properties =
            getProperties(inboundConnectorAnnotation, beanDefinition, beanName);
        preparedProperties.add(properties);
      }
    }
  }

  protected InboundConnector findInboundConnectorAnnotation(BeanDefinition beanDefinition) {
    InboundConnector annotation = null;

    if (beanDefinition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
      AnnotatedTypeMetadata metadata = annotatedBeanDefinition.getFactoryMethodMetadata();
      if (metadata == null) {
        metadata = annotatedBeanDefinition.getMetadata();
      }
      annotation = AnnotationUtil.get(InboundConnector.class, metadata);
    }

    if (annotation == null) {
      LOG.warn("Did not find @InboundConnector annotation on bean: {}", beanDefinition);
    } else {
      LOG.info("Found annotation {} on bean {}", annotation, beanDefinition);
    }
    return annotation;
  }

  private InboundConnectorProperties getProperties(
      InboundConnector inboundConnector, BeanDefinition beanDefinition, String beanName) {
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
      InboundConnectorProperties properties =
          getInboundConnectorProperties(inboundConnector, beanDefinition, beanName);
      return properties;
    }
  }

  private InboundConnectorProperties getInboundConnectorProperties(
      InboundConnector inboundConnector, BeanDefinition beanDefinition, String beanName) {
    var deduplicationProperties = Arrays.asList(inboundConnector.deduplicationProperties());
    Class<? extends InboundConnectorExecutable> inboundConnectorClass;
    try {
      inboundConnectorClass =
          (Class<? extends InboundConnectorExecutable>)
              Class.forName(beanDefinition.getBeanClassName());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          "Error while loading class for bean with class name " + beanDefinition.getBeanClassName(),
          e);
    }
    return new InboundConnectorProperties(
        beanName,
        inboundConnector.name(),
        inboundConnector.type(),
        inboundConnectorClass,
        deduplicationProperties);
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    InboundConnectorFactory inboundConnectorFactory =
        beanFactory.getBean(InboundConnectorFactory.class);
    preparedProperties.stream()
        .map(p -> fromProperties(p, beanFactory))
        .forEach(inboundConnectorFactory::registerConfiguration);
  }

  private InboundConnectorConfiguration fromProperties(
      InboundConnectorProperties properties, BeanFactory beanFactory) {
    LOG.info("Configuring inbound connector {} of bean '{}'", properties, properties.beanName());
    return new InboundConnectorConfiguration(
        properties.name(),
        properties.type(),
        properties.clazz(),
        () -> (InboundConnectorExecutable) beanFactory.getBean(properties.beanName()),
        properties.deduplicationProperties());
  }

  private record InboundConnectorProperties(
      String beanName,
      String name,
      String type,
      Class<? extends InboundConnectorExecutable> clazz,
      List<String> deduplicationProperties) {}
}
