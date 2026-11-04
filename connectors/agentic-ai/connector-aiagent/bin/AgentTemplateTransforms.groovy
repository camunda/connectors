/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/**
 * AI Agent specific helpers for the element template transform scripts. Builds on the generic
 * TemplateTransforms, which must be parsed by the same GroovyClassLoader first:
 * <pre>
 * def loader = new GroovyClassLoader(getClass().classLoader)
 * def T = loader.parseClass(new File(project.basedir, '../bin/TemplateTransforms.groovy'))
 * def A = loader.parseClass(new File(project.basedir, 'bin/AgentTemplateTransforms.groovy'))
 * </pre>
 */
class AgentTemplateTransforms {

    /**
     * Returns the default system prompt for the template id from "templateId=file" lines, or null
     * if the template id has no entry.
     */
    static String systemPromptOverride(Object overrides, String templateId) {
        def file = TemplateTransforms.parseKeyValueLines(overrides)[templateId]
        return file ? TemplateTransforms.existingFile(file).getText('UTF-8').stripTrailing() : null
    }

    /**
     * OpenAI's effort is declared per API family (a sibling of the model, like the other
     * per-family request parameters), so it's emitted before the model. Moves it after the model,
     * matching Anthropic and Bedrock.
     */
    static List moveOpenAiEffortAfterModel(List properties) {
        return TemplateTransforms.moveAfter(
            properties,
            ["provider.openai.api.completions.effort", "provider.openai.api.responses.effort"],
            "provider.openai.model.model"
        )
    }
}
