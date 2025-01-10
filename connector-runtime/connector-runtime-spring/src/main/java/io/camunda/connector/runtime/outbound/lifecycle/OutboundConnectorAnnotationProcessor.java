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
package io.camunda.connector.runtime.outbound.lifecycle;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor;
import io.camunda.zeebe.spring.client.bean.BeanInfo;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class OutboundConnectorAnnotationProcessor extends AbstractZeebeAnnotationProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OutboundConnectorManager outboundConnectorManager;
  private final OutboundConnectorFactory outboundConnectorFactory;

  public OutboundConnectorAnnotationProcessor(
      final OutboundConnectorManager outboundConnectorManager,
      final OutboundConnectorFactory outboundConnectorFactory) {
    this.outboundConnectorManager = outboundConnectorManager;
    this.outboundConnectorFactory = outboundConnectorFactory;
  }

  @Override
  public boolean isApplicableFor(ClassInfo beanInfo) {
    return beanInfo.hasClassAnnotation(OutboundConnector.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void configureFor(ClassInfo beanInfo) {
    beanInfo
        .getAnnotation(OutboundConnector.class)
        .map(outboundConnector -> registerOutboundConnector(outboundConnector, beanInfo));
  }

  private OutboundConnectorConfiguration registerOutboundConnector(
      OutboundConnector outboundConnector, BeanInfo beanInfo) {
    OutboundConnectorConfiguration configuration =
        new OutboundConnectorConfiguration(
            outboundConnector.name(),
            outboundConnector.inputVariables(),
            outboundConnector.type(),
            (Class<? extends OutboundConnectorFunction>) beanInfo.getTargetClass(),
            () -> (OutboundConnectorFunction) beanInfo.getBean());
    LOGGER.info(
        "Configuring outbound connector {} of bean '{}'", configuration, beanInfo.getBeanName());
    outboundConnectorFactory.registerConfiguration(configuration);
    return configuration;
  }

  @Override
  public void start(final CamundaClient client) {
    outboundConnectorManager.start(client);
  }

  @Override
  public void stop(CamundaClient client) {
    outboundConnectorManager.stop();
  }
}
