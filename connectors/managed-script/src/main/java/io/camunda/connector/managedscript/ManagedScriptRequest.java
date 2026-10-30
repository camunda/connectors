/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

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
            tooltip =
                "The script defines execute(variables, context), which receives this object as"
                    + " variables and returns an object: the result of the task, mapped by the"
                    + " result variable or expression. Return a business error as data, for example"
                    + " {\"error\": \"INSUFFICIENT_FUNDS\"}, and map it with the error expression.",
            type = PropertyType.Text,
            feel = FeelMode.required,
            optional = true)
        Map<String, Object> scriptInput) {}
