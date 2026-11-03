#!/usr/bin/env groovy

def loader = new GroovyClassLoader(getClass().classLoader)
def T = loader.parseClass(new File(project.basedir, '../bin/TemplateTransforms.groovy'))
def A = loader.parseClass(new File(project.basedir, 'bin/AgentTemplateTransforms.groovy'))

def file = T.existingFile(T.requiredProperty(binding, 'sourceFile'))
def outputFile = new File(T.requiredProperty(binding, 'outputFile'))

// optional: agentType for the zeebe:agentDefinition marker; unset means no marker is added
def agentType = T.optionalProperty(binding, 'agentType')

// optional: mark the template as deprecated; unset means no "deprecated" block is added
def deprecationMessage = T.optionalProperty(binding, 'deprecationMessage')
def deprecationDocumentationRef = T.optionalProperty(binding, 'deprecationDocumentationRef')

// optional: "templateId=file" tuples (one per line) of default-system-prompt deviations, keyed by
// the generated template's own id; a template whose id has no entry keeps the generator's own
// default
def systemPromptOverrides = T.optionalProperty(binding, 'systemPromptOverrides')

// optional: "true" for templates whose OpenAI provider has per-API-family effort properties
def moveOpenAiEffortAfterModel = T.optionalProperty(binding, 'moveOpenAiEffortAfterModel')?.toString()?.toBoolean()

def json = T.readJson(file)
List properties = (List) json.get('properties')

// this script never changes a template's own id, so it is the stable lookup key
def systemPromptDefault = A.systemPromptOverride(systemPromptOverrides, (String) json.id)
if (systemPromptDefault) {
    T.requireIds(properties, ["data.systemPrompt.prompt"], "System prompt property")
}
if (agentType) {
    T.requireTaskDefinitionType(properties)
}

// never carry over a marker from the source template if this run doesn't want one
json = T.applyDeprecation(json, deprecationMessage, deprecationDocumentationRef)
if (deprecationMessage && !json.name?.toString()?.endsWith(" (Deprecated)")) {
    json.name += " (Deprecated)"
}

def updatedProperties = []

properties.each { property ->
    // never carry over a marker from the source template; this script adds its own below
    if (property.binding?.type == "zeebe:agentDefinition") {
        return
    }

    if (systemPromptDefault && property.id == "data.systemPrompt.prompt") {
        property.value = '="' + systemPromptDefault + '"'
    }

    updatedProperties.add(property)

    // Mark the element as a native agent definition so the engine creates an agent-definition
    // record at deploy time.
    if (agentType && T.isTaskDefinitionType(property)) {
        updatedProperties.add([
            value: (String) agentType,
            binding: [
                type: "zeebe:agentDefinition",
                property: "agentType"
            ],
            type: "Hidden"
        ])
    }
}

if (moveOpenAiEffortAfterModel) {
    updatedProperties = A.moveOpenAiEffortAfterModel(updatedProperties)
}

json.put('properties', updatedProperties)
T.writeJson(outputFile, json)
