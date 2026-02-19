# AI Agent Chat With Tools And MCP Resources

This example demonstrates an AI Agent process that leverages both **tools** and **MCP resources** to provide
intelligent, context-aware responses in a chat-like interaction.

## Overview

The process implements a conversational AI agent that:

1. **Fetches resources** from a local MCP (Model Context Protocol) server at startup
2. **Processes resources** by grouping them into text and document content types
3. **Runs an AI Agent** (using Amazon Bedrock with Claude) that can:
    - Use the fetched resources as context for answering questions
    - Execute tools like listing users, loading user details, or requesting human assistance
4. **Collects user feedback** and allows follow-up conversations

## Prerequisites

### MCP Test Server

This example requires a local MCP server to be running. The MCP test server can be pulled from Camunda's Docker
registry.

For more information about the MCP test server, including configuration options and available features, see:

- **Repository**: [https://github.com/camunda/mcp/tree/main/mcp-test-server](https://github.com/camunda/mcp/tree/main/mcp-test-server)

#### Running the MCP Server Locally

Pull and run the MCP test server from the Docker registry:

```bash
docker pull registry.camunda.cloud/mcp/mcp-test-server:latest
docker run -p 12001:12001 registry.camunda.cloud/mcp/mcp-test-server:latest
```

The server will be available at `http://localhost:12001/mcp`.

> ⚠️ **Note**: The MCP test server is intended for **development and testing purposes only**. Do not use it in
> production environments.

### AWS Bedrock Credentials

The AI Agent uses Amazon Bedrock with the `eu.anthropic.claude-sonnet-4-5-20250929-v1:0` model. You need to configure the
following secret:

- `AWS_BEDROCK_API_KEY`: Your AWS Bedrock long-term API key. Follow the [AWS Bedrock Documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/getting-started-api-keys.html) to get instructions on creating a long-term API key.

## Process Flow

```
Start → List Resources → Read Resources → Group Resources → AI Agent ↔ User Feedback → End
                                                              ↑              ↓
                                                              └──────────────┘
                                                              (follow-up loop)
```

### Key Components

1. **List Resources** (MCP Client): Queries the MCP server to discover available resources
2. **Read Resources** (MCP Client): Reads the content of each available resource
3. **Group Resources**: Separates resources into text content and document content for the AI
4. **AI Agent** (Ad-hoc Sub-Process): The main AI agent that processes user requests with:
    - **System prompt** including resource context
    - **Tools** for user lookup and human-in-the-loop email sending
    - **Memory** for maintaining conversation context
5. **User Feedback** (User Task): Allows users to review responses and provide follow-up questions

### Available Tools

The AI Agent has access to the following tools within the ad-hoc sub-process:

| Tool                        | Description                                               |
|-----------------------------|-----------------------------------------------------------|
| **List Users**              | Lists all available users from an external API            |
| **Load User by ID**         | Fetches detailed user information including email address |
| **Ask Human to Send Email** | Human-in-the-loop task for email approval and sending     |

## Files

| File                                               | Description                                             |
|----------------------------------------------------|---------------------------------------------------------|
| `ai-agent-chat-with-mcp-resources.bpmn`            | The main BPMN process definition                        |
| `ai-agent-chat-mcp-resources-initial-request.form` | Form for the initial user input                        |
| `ai-agent-chat-mcp-resources-user-feedback.form`   | Form for reviewing AI responses and providing follow-up |
| `ai-agent-chat-human-send-email-request.form`      | Form for the human-in-the-loop email sending task       |

## Usage

1. **Start the MCP test server** locally (see Prerequisites)
2. **Deploy the process** to your Camunda 8 cluster
3. **Configure the secret** `AWS_BEDROCK_API_KEY` in your cluster
4. **Start a process instance** and enter your initial question
5. The AI Agent will:
    - Use MCP resources as additional context
    - Call tools as needed to fulfill the request
    - Return a response for your review
6. **Provide feedback**: Mark as satisfied to end, or enter a follow-up question to continue

## Configuration

The process is pre-configured with:

- **MCP Server URL**: `http://localhost:12001/mcp`
- **LLM Provider**: Amazon Bedrock (us-east-1 region)
- **Model**: `eu.anthropic.claude-sonnet-4-5-20250929-v1:0`

To modify these settings, edit the corresponding input mappings in the BPMN file using Camunda Modeler.

## Example Usage

Example inputs which can be entered in the initial form:

- `Send Ervin a joke about Asian cooking`: this will need to do multiple tool calling steps to find a user named "Ervin"
  and to compose an e-mail for the "Ask human to send email" task. The LLM should use the provided Jokes Guide (markdown
  resource) and the Cooking Essentials PDF as a knowledge base to formulate a joke about Asian cooking. The email
  sending user can provide feedback to update the message such as "include emojis" or "include a Spanish translation".

_Made with ❤️ by Camunda_
