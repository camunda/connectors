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
package io.camunda.connector.api.document;

/**
 * Represents a reference to a document that can be resolved by calling a connector of the specified
 * type with the specified variables.
 *
 * @param type the type of the connector that can resolve the document, e.g.
 *     "io.camunda:http-json:1"
 * @param variables the variables that should be passed to the connector when resolving the document
 */
public record DocumentReference(String type, Object variables) {}
