# Fraud Detection Process Example

This example demonstrates how to deploy and run an AI-driven fraud detection process in Camunda 8, where an AI agent analyzes
a tax submission.

---

## Prerequisites

- **Camunda 8.8+** (SaaS or Self-Managed)
- Access to Camunda Connectors (Agentic AI, HTTP, etc.)
- Outbound internet access for connectors (to reach APIs)

---

## Secrets & Configuration

This example requires AWS Bedrock and OpenAI access. You need to set up the following credentials. Create the following secrets in your Camunda cluster:

| Secret Name              | Purpose                |
|--------------------------|------------------------|
| `AWS_BEDROCK_ACCESS_KEY` | AWS Bedrock access key |
| `AWS_BEDROCK_SECRET_KEY` | AWS Bedrock secret key |
| `OPENAI_API_KEY`         | OpenAI API key         |
| ...                      | ...                    |

Configure the connectors in the Web Modeler or via environment variables as needed.

---

## How to Deploy & Run

1. **Import the BPMN Model**
	- Open Camunda Web Modeler.
	- Import `fraud-detection-service-task-process.bpmn` and all the form files from this folder.

2. **Set Secrets**
	- In Camunda Console, add any required secrets (see above).
    - If you use c8run, set the secrets as environment variables and restart `c8run`
    - If you use c8run with Docker, add the secrets in the `connector-secrets.txt` file and restart `c8run`

3. **Deploy the Process**
	- Deploy the process to your Camunda 8 cluster.

4. **Start a New Instance**
	- Use the Web Modeler to start an instance by filling out the form to start an instance.
	- Use tasklist to fill out the form to start a new instance.

5. **Interact**
	- The agent will respond, possibly using tools.

_Made with ❤️ by Camunda_
