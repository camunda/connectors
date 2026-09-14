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
package io.camunda.connector.runtime.app.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches every runtime except the SaaS bundle. {@code camunda-saas-bundle} pulls this module in
 * transitively at runtime (it depends on the self-managed {@code connector-runtime-bundle}), so
 * this class and {@code camunda-saas-bundle}'s own {@code ConnectorInstancesSecurityConfiguration}
 * — which already covers {@code /configurations/**} with the Console JWT chain — end up on the same
 * classpath. Exclusion has to be structural (an environment check) rather than relying on
 * {@code @ConditionalOnMissingBean} ordering between a component-scanned class and this one.
 *
 * <p>Reuses the same {@code camunda.client.mode} property {@code ConnectorsAutoConfiguration}
 * already keys SaaS-only behavior on. Absent that property, this matches — i.e. it fails closed;
 * only an explicit {@code saas} opts out.
 */
public class SelfManagedRuntimeCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String mode = context.getEnvironment().getProperty("camunda.client.mode", "");
    return !"saas".equalsIgnoreCase(mode);
  }
}
