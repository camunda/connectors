# Contributing to Camunda Connectors

We welcome new contributions! We take pride in maintaining and encouraging a friendly, welcoming, and collaborative
community.

Anyone is welcome to contribute to Camunda Connectors! The best way to get started is to choose an
existing [issue](#starting-on-an-issue).

- [Prerequisites](#prerequisites)
    - [Contributor License Agreement](#contributor-license-agreement)
    - [Code of Conduct](#code-of-conduct)
- [GitHub Issue Guidelines](#github-issue-guidelines)
    - [Starting on an Issue](#starting-on-an-issue)
- [Build and Run from Source](#build-and-run-from-source)
    - [Build](#build)
    - [Run](#run)
    - [Test Execution](#test-execution)
- [Creating a Pull Request](#creating-a-pull-request)
- [Reviewing a Pull Request](#reviewing-a-pull-request)
    - [Review Emoji Code](#review-emoji-code)
    - [Stale Pull Requests](#stale-pull-requests)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Connector Development Guide](#connector-development-guide)
- [Stacked Pull Requests](#stacked-pull-requests)

## Prerequisites

### Contributor License Agreement

You will be asked to sign our [Contributor License Agreement](https://cla-assistant.io/camunda-community-hub/community)
when you open a Pull Request. We are not asking you to assign copyright to us but to give us the right to distribute
your code without restriction. We ask this of all contributors to assure our users of the origin and continuing
existence of the code.

> [!NOTE]
> In most cases, you will only need to sign the CLA once.

### Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/). By participating, you
are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/)
unacceptable behavior as soon as possible.

## GitHub Issue Guidelines

If you want to report a bug or request a new feature, feel free to open a new issue
on [GitHub](https://github.com/camunda/connectors/issues).

If you report a bug, please help speed up problem diagnosis by providing as much information as possible. Ideally, that
would include:

- The connector name and version
- Steps to reproduce the issue
- Expected vs actual behavior
- Relevant logs or error messages
- Environment details (Camunda version, runtime configuration)

> [!NOTE]
> If you have a general usage question, please ask on the [forum](https://forum.camunda.io/).

Every issue should have a meaningful name and a description that either describes:

- A new feature with details about the use case the feature would solve or improve
- A problem, how we can reproduce it, and what the expected behavior would be
- A change and the intention of how this would improve the system

### Starting on an Issue

The `main` branch contains the current in-development state of the project. To work on an issue, follow these steps:

1. Check that a [GitHub issue](https://github.com/camunda/connectors/issues) exists for the task you want to work on. If
   one does not, create one.
2. Check that no one is already working on the issue, and make sure the team would accept a pull request for this topic.
3. Checkout the `main` branch and pull the latest changes.

   ```bash
   git checkout main
   git pull
   ```
4. Create a new branch with the naming scheme `issueId-description`.

   ```bash
   git checkout -b 123-add-new-connector
   ```
5. Follow the code style guidelines while coding (see [Connector Development Guide](#connector-development-guide)).
6. Implement the required changes on your branch and regularly push your changes to the origin so that the CI can run.

   ```bash
   git commit -am 'feat: add new feature'
   git push -u origin 123-add-new-connector
   ```
7. When ready, prepare the branch for review by squashing commits into meaningful units with helpful messages.
8. After finishing up the squashing, force push your changes to your branch.

   ```bash
   git push --force-with-lease
   ```

## Build and Run from Source

### Project Structure

```
connectors/
├── connector-sdk/          # SDK for building connectors (Java 17)
├── connector-runtime/      # Execution environment for connectors
├── connectors/             # 30+ out-of-the-box connectors
├── element-template-generator/  # Automated template generation
├── apps/                   # Applications we deliver (Docker images)
│   ├── connector-runtime-application/  # Standalone runtime, no connectors
│   └── bundle/             # Runtime bundled with out-of-the-box connectors
├── connector-commons/      # Shared utilities
└── connectors-e2e-test/    # End-to-end tests
```

### Build

> [!NOTE]
> - **SDK** uses Java 17
> - **Runtime and Connectors** use Java 21

#### Quick Build (Recommended for Development)

```bash
mvn clean package -Dquickly
```

This skips long-running tests for faster iteration.

#### Full Build

```bash
mvn clean package
```

#### Build Specific Connector

```bash
mvn clean package -pl connectors/http/rest
```

#### Generate Element Templates

```bash
./connectors/create-element-templates-symlinks.sh
```

#### Build Docker Bundle

```bash
cd apps/bundle/default-bundle && docker build -t camunda/connectors-bundle:latest .
```

### Run

#### Run with Spring Boot

```bash
cd apps/connector-runtime-application
mvn spring-boot:run
```

#### Run Specific Tests

```bash
# Run unit tests for a specific connector
mvn test -pl connectors/http/rest -Dtest=HttpJsonFunctionTest

# Run integration tests (requires Docker)
mvn verify -pl connectors/kafka
```

### Test Execution

- **Unit tests**: `*Test.java` - Run with `mvn test`
- **Integration tests**: Use `@SlowTest` annotation - Run with `mvn verify`
- **E2E tests**: Located in `connectors-e2e-test/` - Run with `mvn verify -Pe2e`

#### Test Troubleshooting

If tests fail with Docker-related errors:

1. Ensure Docker is running
2. Check that required images are available
3. Verify `docker-images.properties` contains the correct image versions

## Creating a Pull Request

When creating a pull request, please:

1. **Link the related issue** in the PR description
2. **Provide a clear description** of what changes were made and why
3. **Include tests** for new functionality
4. **Update documentation** if needed (README, element templates, etc.)
5. **Ensure CI passes** before requesting review

## Reviewing a Pull Request

### Review Emoji Code

We use emoji codes to communicate review feedback clearly:

| Emoji                  | Meaning                         |
|------------------------|---------------------------------|
| 👍 `:+1:`              | I like this                     |
| ❓ `:question:`         | I have a question               |
| 💭 `:thought_balloon:` | Thought or idea (non-blocking)  |
| 🔧 `:wrench:`          | Suggestion for improvement      |
| ❌ `:x:`                | This needs to change (blocking) |
| 🎨 `:art:`             | Style/formatting suggestion     |
| 📝 `:memo:`            | Documentation suggestion        |

### Stale Pull Requests

Pull requests with no activity for 30 days may be closed. If you need more time:

- Leave a comment explaining the delay
- Mark as draft if work is paused

## Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>: <subject>

[optional body]

[optional footer(s)]
```

### Types

| Type       | Description                           |
|------------|---------------------------------------|
| `feat`     | A new feature                         |
| `fix`      | A bug fix                             |
| `docs`     | Documentation changes                 |
| `style`    | Code style changes (formatting, etc.) |
| `refactor` | Code refactoring                      |
| `test`     | Adding or updating tests              |
| `chore`    | Maintenance tasks                     |
| `deps`     | Dependency updates                    |

### Examples

```bash
# Feature
feat: add support for multipart uploads

# Bug fix
fix: handle empty request body gracefully

Fixes #1234

# Breaking change
feat!: change default timeout to 30s

BREAKING CHANGE: Default timeout changed from 10s to 30s.
Update your configurations if you rely on the previous default.
```

## Connector Development Guide

For detailed guidance on developing connectors, please refer to:

- [`.github/copilot-instructions.md`](.github/copilot-instructions.md) - Comprehensive development patterns
- [`connector-sdk/core/README.md`](connector-sdk/core/README.md) - SDK documentation
- [`connectors/http/rest/`](connectors/http/rest/) - Reference implementation for outbound connectors
- [`connectors/webhook/`](connectors/webhook/) - Reference implementation for inbound connectors

### Quick Reference

```mermaid
graph TD
    A[New Connector] --> B{Type?}
    B -->|Outbound| C[Implement OutboundConnectorFunction]
    B -->|Inbound| D[Implement InboundConnectorExecutable]
    C --> E[Add @OutboundConnector annotation]
D --> F[Add @InboundConnector annotation]
E --> G[Create Request/Response models]
F --> G
G --> H[Add @ElementTemplate annotation]
H --> I[Write tests]
I --> J[Generate element template]
J --> K[Update ServiceLoader registration]
```

### Standard Connector Structure

```
my-connector/
├── pom.xml
├── src/main/java/io/camunda/connector/
│   ├── MyConnectorFunction.java
│   └── model/
│       ├── MyRequest.java
│       └── MyResponse.java
├── src/test/java/
│   ├── MyConnectorTest.java
│   ├── BaseTest.java
│   └── GenerateElementTemplate.java
├── src/test/resources/
│   └── docker-images.properties
└── element-templates/
    └── my-connector.json
```

## Stacked Pull Requests

For large features, consider using **stacked PRs** - breaking down a large change into smaller, incremental pull
requests that build on top of each other. This approach makes code reviews easier and provides faster feedback.

📖 **See [Stacked Pull Requests Guide](docs/stacked-pull-requests.md) for detailed instructions, diagrams, and tooling
recommendations.**

---

Thank you for contributing to Camunda Connectors! 🚀
