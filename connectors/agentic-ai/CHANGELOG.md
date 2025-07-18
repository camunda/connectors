# Agentic AI connectors changelog

## 8.8.0-alpha7

- Added support to provide custom storage backends in a self-managed deployment:
  - https://github.com/camunda/connectors/pull/5035
- Changed the way how prompt parameters are resolved to be less intrusive. Now only defined parameters
  are considered in `{{curlyBraces}}` syntax while keeping other usages of curly braces in the prompt text intact.
  - Breaking change: the default parameters for current date and time are not available by default anymore but can be
    added on demand with FEEL's temporal functions.
  - https://github.com/camunda/connectors/pull/5065

## 8.8.0-alpha6

- Added support to define the response format and to opt into JSON mode with a configurable schema for supported models:
  - https://github.com/camunda/connectors/pull/4833
- Added support for storing conversation history as Camunda JSON documents instead of using process variables:
  - https://github.com/camunda/connectors/pull/4899
- Initial, experimental support for MCP clients:
  - https://github.com/camunda/connectors/pull/4860
  - https://github.com/camunda/connectors/pull/4822

## 8.8.0-alpha5

- Revised property panel with updated structure and better tooltips/descriptions:
    - https://github.com/camunda/connectors/pull/4762
    - https://github.com/camunda/connectors/pull/4764
    - https://github.com/camunda/connectors/pull/4768
- Introduced our own data structures for messages and tool calling and abstracted
  Langchain4J usage to a framework module. The behavior is mostly the same, but with some minor breaking changes:
    - It is not possible to define which response should be mapped back to the process by configuring the `Response`
      section in the properties panel. Depending on the configuration the response will contain `responseText`,
      `responseMessage`, or both.
    - The in-process memory does not hold serialized Camunda documents anymore. Instead, document references are used in
      process variables which are resolved before calling the LLM.

## 8.8.0-alpha4

- First alpha release of the Agentic AI connectors
