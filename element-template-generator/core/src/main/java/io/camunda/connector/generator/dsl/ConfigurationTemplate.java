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
package io.camunda.connector.generator.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;

/**
 * A configuration template embedded in an element template under the {@code configurationTemplates}
 * key. Describes the field definitions a configuration editor (Hub / Modeler) uses to render and
 * validate a configuration of a given type. Properties use {@code property} bindings (not {@code
 * zeebe:input}) and carry no {@code feel}.
 *
 * <p>{@code kind} declares the class of configuration produced (e.g. {@code CREDENTIAL}); it is the
 * discriminator written into the created instance's metadata bag.
 */
@JsonInclude(Include.NON_NULL)
public record ConfigurationTemplate(
    String id, String kind, long version, String name, List<Property> properties) {}
