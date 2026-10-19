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
package io.camunda.connector.runtime.outbound;

import org.springframework.context.ApplicationContext;

/**
 * Test-only, local to this module: clears the shared BPMN-model and intrinsic-function allow-list
 * caches. {@link BpmnModelCacheHolder} and {@link IntrinsicFunctionAllowListCacheHolder} are
 * deliberately package-private (see their own javadocs) and never exposed as production API. This
 * class declares the same package purely to reach them -- on the plain classpath (no JPMS module
 * boundaries here), package-private access only depends on the package name matching, not on which
 * jar/module a class physically came from, so this file can live entirely in this e2e-test module's
 * own test sources without connector-runtime-spring publishing anything.
 *
 * <p>Exists for {@code IntrinsicFunctionsTests}: {@code @CamundaSpringProcessTest} resets the
 * embedded broker between test methods within that class, recycling its process-definition/
 * instance key sequence, while these caches are ordinary Spring singletons that outlive that reset
 * -- so without an explicit clear, one method's cached allow-list can be served back for a
 * different method's deployed model that happened to land on the same recycled key. A real,
 * persistent Zeebe cluster never recycles a processDefinitionKey within its own lifetime, so this
 * mismatch is specific to that broker-reset test harness, not a production concern (see
 * security-testing-findings#275, PR #8991).
 */
public final class StaticAnalysisCacheResetter {

  private StaticAnalysisCacheResetter() {}

  public static void clearAll(ApplicationContext applicationContext) {
    applicationContext.getBean(BpmnModelCacheHolder.class).cache().clear();
    applicationContext.getBean(IntrinsicFunctionAllowListCacheHolder.class).cache().clear();
  }
}
