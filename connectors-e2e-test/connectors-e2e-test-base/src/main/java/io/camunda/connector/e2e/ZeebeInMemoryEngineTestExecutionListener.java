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
package io.camunda.connector.e2e;

import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.spring.test.AbstractZeebeTestExecutionListener;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Replacement for {@code io.camunda.zeebe.spring.test.ZeebeTestExecutionListener}.
 *
 * <p>The original listener picks the engine port via {@code TestSocketUtils}, which only probes
 * {@code localhost}, while the in-memory engine's gRPC server binds the wildcard address. When e2e
 * modules run in parallel (e.g. {@code mvn -T 1C}) next to Testcontainers, the probed port is
 * frequently already in use on another interface, failing with "Address already in use" and then
 * hanging for 5 minutes while stopping the half-started engine. {@link EngineFactory#create()} lets
 * the OS assign a free port on the wildcard address instead.
 */
public class ZeebeInMemoryEngineTestExecutionListener extends AbstractZeebeTestExecutionListener
    implements TestExecutionListener, Ordered {

  private ZeebeTestEngine zeebeEngine;

  @Override
  public void beforeTestMethod(@NonNull TestContext testContext) {
    var engine = EngineFactory.create();
    engine.start();
    zeebeEngine = engine;
    setupWithZeebeEngine(testContext, zeebeEngine);
  }

  @Override
  public void afterTestMethod(@NonNull TestContext testContext) {
    if (zeebeEngine == null) {
      return;
    }
    try {
      cleanup(testContext, zeebeEngine);
    } finally {
      zeebeEngine.stop();
      zeebeEngine = null;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
