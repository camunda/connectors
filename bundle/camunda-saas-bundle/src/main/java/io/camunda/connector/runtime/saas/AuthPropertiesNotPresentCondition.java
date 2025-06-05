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
package io.camunda.connector.runtime.saas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AuthPropertiesNotPresentCondition implements Condition {

  private static final String CLIENT_ID_PROPERTY = "camunda.client.auth.client-id";
  private static final String CLIENT_SECRET_PROPERTY = "camunda.client.auth.client-secret";

  private final Logger LOG = LoggerFactory.getLogger(AuthPropertiesNotPresentCondition.class);

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    final boolean containsClientId = context.getEnvironment().containsProperty(CLIENT_ID_PROPERTY);
    final boolean containsClientSecret =
        context.getEnvironment().containsProperty(CLIENT_SECRET_PROPERTY);

    if (containsClientId && containsClientSecret) {
      LOG.info(
          "Camunda client authentication properties are present. "
              + "Custom credentials provider (connector secrets) will NOT be used.");
      return false;
    }

    if (!containsClientId) {
      LOG.error("Camunda client authentication property '{}' is NOT present.", CLIENT_ID_PROPERTY);
    }
    if (!containsClientSecret) {
      LOG.error(
          "Camunda client authentication property '{}' is NOT present.", CLIENT_SECRET_PROPERTY);
    }
    LOG.info("Custom credentials provider (connector secrets) will be used.");
    return true;
  }
}
