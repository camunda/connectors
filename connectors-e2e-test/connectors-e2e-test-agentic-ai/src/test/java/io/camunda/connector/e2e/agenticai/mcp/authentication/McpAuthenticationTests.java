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
package io.camunda.connector.e2e.agenticai.mcp.authentication;

import org.junit.jupiter.api.Nested;
import org.springframework.test.context.TestPropertySource;

public class McpAuthenticationTests {

  @Nested
  @TestPropertySource(properties = {"mcp.test.transport=HTTP", "mcp.test.auth=BASIC"})
  public class BasicAuthenticationHttpTest extends BaseMcpAuthenticationTest {}

  @Nested
  @TestPropertySource(properties = {"mcp.test.transport=SSE", "mcp.test.auth=BASIC"})
  public class BasicAuthenticationSseTest extends BaseMcpAuthenticationTest {}

  @Nested
  @TestPropertySource(properties = {"mcp.test.transport=HTTP", "mcp.test.auth=API_KEY"})
  public class ApiKeyAuthenticationHttpTest extends BaseMcpAuthenticationTest {}

  @Nested
  @TestPropertySource(properties = {"mcp.test.transport=SSE", "mcp.test.auth=API_KEY"})
  public class ApiKeyAuthenticationSseTest extends BaseMcpAuthenticationTest {}

  @Nested
  @TestPropertySource(properties = {"mcp.test.transport=HTTP", "mcp.test.auth=OAUTH2"})
  public class OAuth2AuthenticationHttpTest extends BaseMcpAuthenticationTest {}

  @Nested
  @TestPropertySource(properties = {"mcp.test.transport=SSE", "mcp.test.auth=OAUTH2"})
  public class OAuth2AuthenticationSseTest extends BaseMcpAuthenticationTest {}
}
