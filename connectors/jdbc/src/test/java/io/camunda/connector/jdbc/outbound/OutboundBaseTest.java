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

package io.camunda.connector.jdbc.outbound;

import io.camunda.connector.jdbc.BaseTest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

public class OutboundBaseTest extends BaseTest {

  public static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(SecretsConstant.Authentication.URI, ActualValue.Authentication.URI)
        .secret(SecretsConstant.Authentication.PORT, ActualValue.Authentication.PORT)
        .secret(SecretsConstant.Authentication.HOST, ActualValue.Authentication.HOST)
        .secret(SecretsConstant.Query.QUERY, ActualValue.Query.QUERY)
        .secret(SecretsConstant.Variables.VARIABLES, ActualValue.Variables.VARIABLES);
  }
}
