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
package io.camunda.connector.runtime.app;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.springframework.context.annotation.Scope;

/**
 * A prototype-scoped outbound connector fixture used to verify (#6961) that {@code
 * beanFactory.getBean(name, type)}-backed instance suppliers yield a genuinely fresh instance on
 * every call, rather than always returning one globally shared instance.
 */
@OutboundConnector(
    name = "TEST_PROTOTYPE",
    type = TestPrototypeOutboundConnector.TYPE,
    inputVariables = {})
@Scope("prototype")
public class TestPrototypeOutboundConnector implements OutboundConnectorFunction {

  public static final String TYPE = "io.camunda:test-outbound-prototype:1";

  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }
}
