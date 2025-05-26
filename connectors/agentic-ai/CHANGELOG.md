# Agentic AI connectors changelog

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
