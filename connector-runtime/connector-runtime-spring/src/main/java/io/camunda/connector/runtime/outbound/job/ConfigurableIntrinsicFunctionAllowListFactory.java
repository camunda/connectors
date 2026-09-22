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
 * Unlike {@link ConfigurableSecretFilterFactory}, there is no {@code LAX} mode, and {@code
 * DISABLED} does not mean {@code allowAll()}. A security allow-list that fails open — whether on a
 * BPMN-fetch/parse error or because an operator turned the mechanism off — would silently re-open
 * security-testing-findings#275's exact hole, which is a materially different risk than an
 * over-permissive secret resolution: an intrinsic-function call is an RCE-class primitive
 * (arbitrary document read/link-minting), not a leak. {@code DISABLED} therefore means "stop
 * consulting the deployed BPMN model and refuse every call instead" — a safe, permanent kill switch
 * for a topology that cannot reach the BPMN-fetch endpoint (e.g. an outbound-only deployment
 * without a {@code CamundaOperateClient} bean), not a way back to unconditional dispatch. {@code
 * ENABLED} (the default) always fails closed on a fetch/parse error too, propagating the cache's
 * exception to the caller — matching {@code SecretFilterMode.STRICT}'s behavior with no lenient
 * alternative offered. That fetch is deferred to the first {@code isAllowed} call via {@link
 * LazyLoadingIntrinsicFunctionAllowList} rather than done eagerly here, so a job whose variables
 * never contain an intrinsic-function call never pays for it — see that class's javadoc.
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
      case DISABLED -> IntrinsicFunctionAllowList.allowNone();
      case ENABLED ->
          new LazyLoadingIntrinsicFunctionAllowList(
              () -> allowListCache.getAllowedFunctions(context));
    };
  }

  public enum IntrinsicFunctionAllowListMode {
    DISABLED,
    ENABLED
  }
}
