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
package io.camunda.connector.runtime.outbound.job;

import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionIntrinsicFunctionAllowListCache;

/**
 * Unlike {@link ConfigurableSecretFilterFactory}, there is no {@code LAX} mode: a security
 * allow-list that fails open on a BPMN-fetch/parse error would silently re-open exactly the hole
 * this mechanism exists to close, which is a materially different risk than a secret failing to
 * resolve. {@code ENABLED} always fails closed, propagating the cache's exception to the caller —
 * matching {@code SecretFilterMode.STRICT}'s behavior with no lenient alternative offered.
 */
public class ConfigurableIntrinsicFunctionAllowListFactory
    implements IntrinsicFunctionAllowListFactory {

  private final IntrinsicFunctionAllowListMode mode;
  private final ProcessDefinitionIntrinsicFunctionAllowListCache allowListCache;

  public ConfigurableIntrinsicFunctionAllowListFactory(
      IntrinsicFunctionAllowListMode mode,
      ProcessDefinitionIntrinsicFunctionAllowListCache allowListCache) {
    this.mode = mode;
    this.allowListCache = allowListCache;
  }

  @Override
  public IntrinsicFunctionAllowList create(IntrinsicFunctionAllowListContext context) {
    return switch (mode) {
      case DISABLED -> IntrinsicFunctionAllowList.allowAll();
      case ENABLED ->
          IntrinsicFunctionAllowList.allowOnly(allowListCache.getAllowedFunctions(context));
    };
  }

  public enum IntrinsicFunctionAllowListMode {
    DISABLED,
    ENABLED
  }
}
