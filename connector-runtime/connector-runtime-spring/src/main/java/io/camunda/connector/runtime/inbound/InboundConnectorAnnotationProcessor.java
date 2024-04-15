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

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor;
import io.camunda.zeebe.spring.client.bean.BeanInfo;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

/** */
public class InboundConnectorAnnotationProcessor extends AbstractZeebeAnnotationProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final InboundConnectorFactory inboundConnectorFactory;

  private final ConfigurableBeanFactory configurableBeanFactory;

  public InboundConnectorAnnotationProcessor(
      final InboundConnectorFactory inboundConnectorFactory,
      final ConfigurableBeanFactory configurableBeanFactory) {
    this.inboundConnectorFactory = inboundConnectorFactory;
    this.configurableBeanFactory = configurableBeanFactory;
  }

  @Override
  public boolean isApplicableFor(ClassInfo beanInfo) {
    return beanInfo.hasClassAnnotation(InboundConnector.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void configureFor(ClassInfo beanInfo) {
    beanInfo
        .getAnnotation(InboundConnector.class)
        .map(inboundConnector -> registerInboundConnector(inboundConnector, beanInfo));
  }

  private InboundConnectorConfiguration registerInboundConnector(
      InboundConnector inboundConnector, BeanInfo beanInfo) {
    var scope = configurableBeanFactory.getMergedBeanDefinition(beanInfo.getBeanName()).getScope();
    if (!SCOPE_PROTOTYPE.equals(scope)) {
      throw new IllegalStateException(
          "Only \""
              + SCOPE_PROTOTYPE
              + "\" scope is supported for inbound connectors but found: \""
              + scope
              + "\" for bean: "
              + beanInfo.getBean());
    } else {
      InboundConnectorConfiguration configuration =
          getInboundConnectorConfiguration(inboundConnector, beanInfo);
      LOGGER.info(
          "Configuring inbound connector {} of bean '{}'", configuration, beanInfo.getBeanName());
      inboundConnectorFactory.registerConfiguration(configuration);
      return configuration;
    }
  }

  private InboundConnectorConfiguration getInboundConnectorConfiguration(
      InboundConnector inboundConnector, BeanInfo beanInfo) {
    var deduplicationProperties = Arrays.asList(inboundConnector.deduplicationProperties());
    return new InboundConnectorConfiguration(
        inboundConnector.name(),
        inboundConnector.type(),
        (Class<? extends InboundConnectorExecutable>) beanInfo.getTargetClass(),
        () ->
            (InboundConnectorExecutable) configurableBeanFactory.getBean(beanInfo.getTargetClass()),
        deduplicationProperties);
  }

  @Override
  public void start(final ZeebeClient client) {}

  @Override
  public void stop(ZeebeClient client) {}
}
