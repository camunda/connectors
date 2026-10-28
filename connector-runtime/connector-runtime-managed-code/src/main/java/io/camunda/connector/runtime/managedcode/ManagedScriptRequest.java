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
package io.camunda.connector.runtime.managedcode;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import java.util.Map;

/**
 * Input of a managed-script task. The script is linked, not passed as input: the engine resolves
 * the link and hands its resource key over in the {@code linkedResources} job header.
 *
 * @param scriptInput the object passed to the script, built by one FEEL expression; the only
 *     variable the runtime fetches for the job
 */
@TemplateLinkedResource(
    linkName = LinkedResources.SCRIPT_LINK,
    resourceType = ManagedScriptFunction.RESOURCE_TYPE,
    group = "script",
    resourceIdLabel = "Script file",
    resourceIdDescription = "File name of the .py or .js script, deployed as a resource",
    bindingTypeLabel = "Script binding")
public record ManagedScriptRequest(
    @TemplateProperty(
            group = "input",
            label = "Script input",
            description = "Object passed to the script, for example ={a: order.amount, b: tax}",
            type = PropertyType.Text,
            feel = FeelMode.required,
            optional = true)
        Map<String, Object> scriptInput) {}
