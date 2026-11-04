#!/usr/bin/env groovy

def loader = new GroovyClassLoader(getClass().classLoader)
def T = loader.parseClass(new File(project.basedir, '../bin/TemplateTransforms.groovy'))
def A = loader.parseClass(new File(project.basedir, 'bin/AgentTemplateTransforms.groovy'))

static def replaceDocumentationLinks(String text) {
    if (text == null) return null
    return text.replace(
        "out-of-the-box-connectors/agentic-ai-aiagent-task/",
        "out-of-the-box-connectors/agentic-ai-aiagent-subprocess/"
    )
}

// Swaps the element kind tag on leaf steps (those with a presetId) that the provider-steps
// transform set on the task template. A source template without steps is left alone.
static def retagLeafSteps(List steps, String fromTag, String toTag) {
    steps?.each { step ->
        if (step.presetId) {
            if (step.name.startsWith(fromTag)) {
                step.name = (toTag + step.name.substring(fromTag.length())).toString()
            }
        } else if (step.steps) {
            retagLeafSteps((List) step.steps, fromTag, toTag)
        }
    }
}

def file = T.existingFile(T.requiredProperty(binding, 'sourceFile'))
def outputFilePath = new File(T.requiredProperty(binding, 'outputFile'))
def templateId = T.requiredProperty(binding, 'templateId')
def connectorType = T.requiredProperty(binding, 'connectorType')

// optional: agentType for the zeebe:agentDefinition marker; unset means no marker is added
def agentType = T.optionalProperty(binding, 'agentType')

// optional: mark the derived template as deprecated; unset means no "deprecated" block is added
def deprecationMessage = T.optionalProperty(binding, 'deprecationMessage')
def deprecationDocumentationRef = T.optionalProperty(binding, 'deprecationDocumentationRef')

// optional: "templateId=file" tuples (one per line) of default-system-prompt deviations, keyed by
// the derived template's own id (this script always assigns the derived template's id from the
// templateId property above, so that is the stable lookup key here, not the source template's own
// id). A template whose id has no entry keeps the source template's default.
def systemPromptOverrides = T.optionalProperty(binding, 'systemPromptOverrides')
def systemPromptDefault = A.systemPromptOverride(systemPromptOverrides, templateId)

// optional: "true" for templates whose OpenAI provider has per-API-family effort properties
def moveOpenAiEffortAfterModel = T.optionalProperty(binding, 'moveOpenAiEffortAfterModel')?.toString()?.toBoolean()

def json = T.readJson(file)

// Properties to drop from the task template
def skipProperties = [
    "data.tools.containerElementId",
    "data.tools.toolCallResults",
    "resultExpression"
]

// every id this script looks up must exist, so a rename in the source template fails the build
T.requireIds((List) json.get("groups"), ["limits"], "Groups in ${file}")
T.requireIds(
    (List) json.get('properties'),
    skipProperties + [
        "id",
        "resultVariable",
        "data.agentContext",
        "data.response.includeAssistantMessage",
        "data.limits.maxModelCalls"
    ] + (systemPromptDefault ? ["data.systemPrompt.prompt"] : []),
    "Properties in ${file}"
)
T.requireTaskDefinitionType((List) json.get('properties'))

// never carry over a "deprecated" block from the source template; this script decides
// deprecation for the derived template on its own, via the properties above
json = T.applyDeprecation(json, deprecationMessage, deprecationDocumentationRef)

def isHybrid = json.id?.toString()?.contains("-hybrid")

// copy existing file to versioned directory if version was updated
if (!isHybrid) {
    T.backupIfVersionBumped(outputFilePath, json)
}

// Update template metadata
json.id = templateId
json.name = "AI Agent Sub-process"
json.description = "Run a multi-step AI reasoning loop with dynamic tool selection"
json.documentationRef = replaceDocumentationLinks(json.documentationRef)

if (isHybrid) {
    json.id += "-hybrid"
    json.name = "Hybrid " + json.name
}

if (deprecationMessage) {
    json.name += " (Deprecated)"
}

retagLeafSteps((List) json.get('steps'), "AI Agent Task: ", "AI Agent Sub-process: ")

// Change BPMN element configuration
json.appliesTo = ["bpmn:SubProcess"]
json.elementType.value = "bpmn:AdHocSubProcess"

// Transform groups
def updatedGroups = []

((List) json.get('groups')).each { group ->
    if (group.tooltip) {
        group.tooltip = replaceDocumentationLinks(group.tooltip)
    }

    updatedGroups.add(group)

    if (group.id == "limits") {
        updatedGroups.add([
            id: "events",
            label: "Event handling",
            tooltip : "Configure how event sub-process results are handled. Results are added as user messages to the running agent.",
            openByDefault: false
        ])
    }
}

json.put('groups', updatedGroups)

// Transform properties
def updatedProperties = []

((List) json.get('properties')).each { property ->
    if (property.id in skipProperties) {
        return
    }

    // never carry over a marker from the source template; this script adds its own below
    if (property.binding?.type == "zeebe:agentDefinition") {
        return
    }

    if (property.description) {
        property.description = replaceDocumentationLinks(property.description)
    }

    if (property.tooltip) {
        property.tooltip = replaceDocumentationLinks(property.tooltip)
    }

    if (systemPromptDefault && property.id == "data.systemPrompt.prompt") {
        property.value = '="' + systemPromptDefault + '"'
    }

    // Update specific property values and bindings
    if (T.isTaskDefinitionType(property)) {
        property.value = connectorType

        // Add new hidden properties after the type property
        updatedProperties.add(property)

        updatedProperties.add([
            id: "outputCollection",
            binding: [
                property: "outputCollection",
                type: "zeebe:adHoc"
            ],
            value: "toolCallResults",
            type: "Hidden"
        ])

        updatedProperties.add([
            id: "outputElement",
            binding: [
                property: "outputElement",
                type: "zeebe:adHoc"
            ],
            value: "={\n  id: toolCall._meta.id,\n  name: toolCall._meta.name,\n  content: toolCallResult,\n  completedAt: now()\n}",
            type: "Hidden"
        ])

        // Mark the ad-hoc sub-process as an agentic tool container so linting rules
        // (e.g. fromAi() validation) can detect it.
        updatedProperties.add([
            value: "true",
            binding: [
                name: "io.camunda.agenticai.toolContainer",
                type: "zeebe:property"
            ],
            type: "Hidden"
        ])

        // Mark the element as a native agent definition so the engine creates an agent-definition
        // record at deploy time.
        if (agentType) {
            updatedProperties.add([
                value: (String) agentType,
                binding: [
                    type: "zeebe:agentDefinition",
                    property: "agentType"
                ],
                type: "Hidden"
            ])
        }
    } else if (property.id == "id") {
        property.value = templateId
        updatedProperties.add(property)
    } else if (property.id == "resultVariable") {
        property.binding = [source: "=agent", type: "zeebe:output"]
        property.value = "agent"
        updatedProperties.add(property)
    } else if (property.id == "data.agentContext") {
        property.id = "agentContext"
        property.description = "Initial agent context from previous interactions. Avoid reusing context variables across agents to prevent issues with stale data or tool access."
        property.optional = false
        property.feel = "required"
        property.binding.name = "agentContext"
        property.remove("value")
        property.remove("constraints")
        updatedProperties.add(property)
    } else {
        updatedProperties.add(property)

        // Add includeAgentContext after includeAssistantMessage
        if (property.id == "data.response.includeAssistantMessage") {
            updatedProperties.add([
                id: "data.response.includeAgentContext",
                label: "Include agent context",
                description: "Include the agent context as part of the result object.",
                optional: true,
                feel: "static",
                group: "response",
                binding: [
                    name: "data.response.includeAgentContext",
                    type: "zeebe:input"
                ],
                tooltip: "Use this option if you need to re-inject the previous agent context into a future agent execution, for example when modeling a user feedback loop between an agent and a user task.",
                type: "Boolean"
            ])
        }

        // Add events behavior property after limits
        if (property.id == "data.limits.maxModelCalls") {
            updatedProperties.add([
                id: "data.events.behavior",
                label: "Event handling behavior",
                description: "Behavior on completing an event sub-process.",
                optional: false,
                value: "WAIT_FOR_TOOL_CALL_RESULTS",
                constraints: [
                    notEmpty: true
                ],
                group: "events",
                binding: [
                    name: "data.events.behavior",
                    type: "zeebe:input"
                ],
                type: "Dropdown",
                choices: [
                    [
                        name: "Wait for tool call results",
                        value: "WAIT_FOR_TOOL_CALL_RESULTS"
                    ],
                    [
                        name: "Cancel tool calls",
                        value: "INTERRUPT_TOOL_CALLS"
                    ]
                ]
            ])
        }
    }
}

// empty input mapping for local agent variable — must be last so it is
// evaluated after the agentContext input has read =agent.context
updatedProperties.add([
    binding: [
        name: "agent",
        type: "zeebe:input"
    ],
    type: "Hidden"
])

if (moveOpenAiEffortAfterModel) {
    updatedProperties = A.moveOpenAiEffortAfterModel(updatedProperties)
}

json.put('properties', updatedProperties)
T.writeJson(outputFilePath, json)
