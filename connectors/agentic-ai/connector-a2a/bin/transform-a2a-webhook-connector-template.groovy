#!/usr/bin/env groovy

def T = new GroovyClassLoader(getClass().classLoader)
    .parseClass(new File(project.basedir, '../bin/TemplateTransforms.groovy'))

def file = T.existingFile(T.requiredProperty(binding, 'sourceFile'))
def outputFilePath = new File(T.requiredProperty(binding, 'outputFile'))

def json = T.readJson(file)

// every id this script looks up must exist, so a rename in the source template fails the build
T.requireIds(
    (List) json.get('properties'),
    ["inbound.clientResponse", "activationCondition", "correlationKeyProcess", "correlationKeyPayload"],
    "Properties in ${file}"
)

// copy existing file to versioned directory if version was updated
T.backupIfVersionBumped(outputFilePath, json)

// Transform properties
def updatedProperties = []

((List) json.get('properties')).each { property ->
    if (property.id == "inbound.clientResponse") {
        // change inbound.clientResponse text field to be an input mapping mapping to internal_clientResponse
        property.id = "internal_clientResponse"
        property.binding = [
                name: "internal_clientResponse",
                type: "zeebe:input"
        ]
        updatedProperties.add(property)

        // add a hidden property reading from the input
        updatedProperties.add([
                id: "inbound.clientResponse",
                value: "=internal_clientResponse",
                binding: [
                        name: "inbound.clientResponse",
                        type: "zeebe:property"
                ],
                type: "Hidden"
        ])
    } else if (property.id == "activationCondition") {
        property.value = "=is defined(request.body.status.state) and not(list contains([\"submitted\", \"working\"], request.body.status.state))"
        updatedProperties.add(property)
    }  else if (property.id == "correlationKeyProcess") {
        property.value = "=internal_clientResponse.result.id + (if is defined(internal_clientResponse.pushNotificationData.token) then (\"-\" + internal_clientResponse.pushNotificationData.token) else \"\")"
        property.type = "Hidden"
        property.remove("feel")
        property.remove("constraints")
        updatedProperties.add(property)
    } else if (property.id == "correlationKeyPayload") {
        property.value = "=request.body.id + (if is defined(request.headers.`x-a2a-notification-token`) then (\"-\" + request.headers.`x-a2a-notification-token`) else \"\")"
        property.type = "Hidden"
        property.remove("feel")
        property.remove("constraints")
        updatedProperties.add(property)
    } else {
        updatedProperties.add(property)
    }
}

json.put('properties', updatedProperties)
T.writeJson(outputFilePath, json)
