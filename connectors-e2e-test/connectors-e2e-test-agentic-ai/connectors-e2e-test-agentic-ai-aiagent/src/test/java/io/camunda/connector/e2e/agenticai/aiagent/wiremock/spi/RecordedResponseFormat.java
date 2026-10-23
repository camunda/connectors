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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi;

import java.util.Map;

/**
 * The structured-output configuration recorded from a request, in a provider-agnostic shape. {@code
 * schemaName} is {@code null} for providers that don't put the configured schema name on the wire.
 * {@code jsonSchema} is always the raw JSON schema object (never wrapped in a provider-specific
 * envelope), regardless of where it sits in the actual wire body.
 */
public record RecordedResponseFormat(
    String type, String schemaName, Map<String, Object> jsonSchema) {}
