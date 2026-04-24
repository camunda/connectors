# Element Template Generation Instructions

## Issue
The Mistral AI provider configuration was added to the code, but the element templates were not regenerated to include it in the UI dropdown.

## Solution

The element templates are automatically generated from Java annotations using the `element-template-generator-maven-plugin`. To regenerate them after adding a new provider:

### Step 1: Generate Element Templates

Run the following Maven command from the root of the repository:

```bash
mvn clean compile io.camunda.connector:element-template-generator-maven-plugin:generate-templates -pl connectors/agentic-ai
```

Or run a full build which will trigger the generator:

```bash
mvn clean install -DskipTests
```

### Step 2: Verify the Changes

After generation, check that the element templates in `connectors/agentic-ai/element-templates/` include Mistral AI:

1. **agenticai-aiagent-outbound-connector.json** - Should include "Mistral AI" in the provider dropdown
2. **agenticai-aiagent-job-worker.json** - Should be updated by the Groovy transform script
3. **hybrid/agenticai-aiagent-outbound-connector-hybrid.json** - Should include Mistral AI

### Step 3: Verify Provider Configuration

The generated template should include:
- Mistral AI in the provider dropdown choices
- Conditional properties for Mistral configuration:
  - `provider.mistral.endpoint` (optional base URL)
  - `provider.mistral.authentication.apiKey` (required API key)
  - `provider.mistral.model.model` (required model name)
  - `provider.mistral.model.parameters.maxTokens` (optional)
  - `provider.mistral.model.parameters.temperature` (optional)
  - `provider.mistral.model.parameters.topP` (optional)
  - `provider.mistral.model.parameters.safePrompt` (optional)
  - `provider.mistral.model.parameters.randomSeed` (optional)

### What Was Added

The following files were modified to support Mistral AI:

1. **MistralProviderConfiguration.java** - New provider configuration with `@TemplateProperty` annotations
2. **ProviderConfiguration.java** - Added Mistral to the sealed interface and `@JsonSubTypes`
3. **ChatModelFactoryImpl.java** - Added `createMistralChatModelBuilder()` method
4. **pom.xml** - Added `langchain4j-mistral-ai` dependency

All the necessary annotations are in place (`@TemplateSubType`, `@TemplateProperty`, etc.), so the plugin should automatically generate the correct element template entries.

## Why This is Needed

The element template generator reads the Java annotations on the provider configuration classes and generates JSON element templates that:
1. Add provider options to dropdowns in the Camunda Modeler
2. Define conditional properties that appear when a provider is selected
3. Set up proper data bindings for the BPMN process variables

Without regenerating the templates, users won't see "Mistral AI" as an option in the provider dropdown, even though the backend code fully supports it.
