# AI Agent Chat With Tools Example

This example demonstrates how to deploy and run an AI-driven chat process in Camunda 8, where an AI agent can answer questions and use external tools (APIs, scripts, etc.) to provide more accurate responses. The process showcases tool integration, user feedback, and human-in-the-loop capabilities.

---

## Prerequisites

- **Camunda 8.8+** (SaaS or Self-Managed)
- Access to Camunda Connectors (Agentic AI, HTTP, etc.)
- Outbound internet access for connectors (to reach APIs)
- (Optional) Credentials for any external APIs/tools you want to use

---

## Secrets & Configuration

This example requires AWS Bedrock access. You need to set up the following credentials. Create the following secrets in your Camunda cluster:

| Secret Name                  | Purpose                        |
|------------------------------|--------------------------------|
| `AWS_BEDROCK_ACCESS_KEY`     | AWS Bedrock access key         |
| `AWS_BEDROCK_SECRET_KEY`     | AWS Bedrock secret key         |
| ...                          | ...                            |

Configure the connectors in the Web Modeler or via environment variables as needed.

---

## How to Deploy & Run

1. **Import the BPMN Model**
	- Open Camunda Web Modeler.
	- Import `ai-agent-chat-with-tools.bpmn` and all the form files from this folder.

2. **Configure Connectors**
	- Configure any HTTP connectors or other tools you want the agent to use.
    - Feel free to add your own tools by creating new service tasks in the `Agent Tools` ad-hoc sub-process.

3. **Set Secrets**
	- In Camunda Console, add any required secrets (see above).
    - If you use c8run, set the secrets as environment variables and restart `c8run`
    - If you use c8run with Docker, add the secrets in the `connector-secrets.txt` file and restart `c8run`

4. **Deploy the Process**
	- Deploy the process to your Camunda 8 cluster.

5. **Start a New Instance**
	- Use the Web Modeler to start an instance by filling out the form to start an instance.
	- Use tasklist to fill out the form to start a new instance.

6. **Interact**
	- The agent will respond, possibly using tools.

---

## BPMN Process Overview

The process (`ai-agent-chat-with-tools.bpmn`) works as follows:

1. **Start Event**: User submits an initial chat request via a form.
2. **AI Agent Task**: The Agentic AI connector receives the request, context, and available tools. It generates a response and may request tool calls.
3. **Tool Call Gateway**: If the agent wants to use tools, the process enters the `Agent Tools` ad-hoc sub-process.
4. **Agent Tools Sub-Process**: For each tool call requested by the agent, the corresponding task is executed. Tools include:
	- Get Date and Time
	- Load user by ID (HTTP API)
	- List users (HTTP API)
	- Search recipe (HTTP API)
	- Superflux product calculation (script)
	- Ask human to send email (user task)
	- Send email (script)
	- Fetch a joke (HTTP API)
	- Fetch URL (HTTP API)
5. **Loopback**: Tool results are returned to the agent, which may generate further tool calls or a final answer.
6. **User Feedback**: The user is asked if they are satisfied with the answer.
	- If not, the process loops for follow-up.
	- If yes, the process ends.

**Key Features:**
- Dynamic tool invocation by the agent
- Human-in-the-loop for actions like sending emails
- Extensible: add your own tools as new tasks in the sub-process

---

_Made with ❤️ by Camunda_
