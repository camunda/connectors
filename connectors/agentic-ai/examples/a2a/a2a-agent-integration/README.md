# AI Agent A2A Integration Example

This example demonstrates how to connect an AI Agent to external agents using the Agent-to-Agent (A2A) client
functionalities.

---

## Prerequisites

- **Camunda 8.8+** (SaaS or Self-Managed)
- Access to Camunda Connectors (Agentic AI, HTTP, etc.)
- The following 3 A2A agents from the [a2a-samples](https://github.com/a2aproject/a2a-samples) repository running and
  accessible under their default URLs (needs a Google Gemini API key):
    - [Currency Conversion Agent](https://github.com/a2aproject/a2a-samples/tree/main/samples/python/agents/langgraph)
      on `http://localhost:10000`
    - [Content Writer Agent](https://github.com/a2aproject/a2a-samples/tree/main/samples/java/agents/content_writer) on
      `http://localhost:10002`
    - [Content Editor Agent](https://github.com/a2aproject/a2a-samples/tree/main/samples/java/agents/content_editor) on
      `http://localhost:10003`

---

## Secrets & Configuration

This example requires AWS Bedrock access. You need to set up the following credentials. Create the following secrets in
your Camunda cluster:

| Secret Name              | Purpose                |
|--------------------------|------------------------|
| `AWS_BEDROCK_ACCESS_KEY` | AWS Bedrock access key |
| `AWS_BEDROCK_SECRET_KEY` | AWS Bedrock secret key |
| ...                      | ...                    |

Configure the connectors in the Web Modeler or via environment variables as needed.

---

## Example Usage

Example inputs which can be entered in the initial form:

### Currency Conversion

Will typically ask for additional input as it is missing the target currency.

> How much is the exchange rate for 1 USD? Do not ask right away for more information. Just forward the question to the
> relevant tool.

### Blog Post Creation

Runs through all 3 A2A agents to achieve the goal.

> Write an engaging blog post showcasing the capabilities of the currency conversion agent. Make sure to pass it to the
> content editor for review.
>
> List the main capabilities of the currency agent and include an example by executing the agent and including input and
> output.

_Made with ❤️ by Camunda_
