# GitHub Connector

Camunda connector to interact with GitHub repositories as part of your process automation.
This connector reuses the base implementation of the [HTTP REST Connector](../http/rest/README.md)
by providing a compatible element template.

## Outbound Connector

Perform actions on GitHub from a BPMN service task.

### Authentication

| Type                  | Description                                                                 |
|-----------------------|-----------------------------------------------------------------------------|
| Personal Access Token | Authenticate using a GitHub PAT. Recommended for user-level access.         |
| GitHub App            | Authenticate as a GitHub App using a private key, App ID, and Installation ID. Recommended for automation. |

### Supported Operations

#### Actions

| Operation                        | Description                                                       |
|----------------------------------|-------------------------------------------------------------------|
| Create a workflow dispatch event | Trigger a `workflow_dispatch` event for a GitHub Actions workflow |

#### Branches

| Operation       | Description                                   |
|-----------------|-----------------------------------------------|
| Get a branch    | Get details of a specific branch              |
| List branches   | List all branches in a repository             |
| Merge a branch  | Merge one branch into another                 |

#### Code Scanning

| Operation                                     | Description                                       |
|-----------------------------------------------|---------------------------------------------------|
| List code scanning alerts for an organization | List alerts across all repos in an organization   |
| List code scanning alerts for a repository    | List all open code scanning alerts in a repository |

#### Collaborators

| Operation                     | Description                                  |
|-------------------------------|----------------------------------------------|
| List repository collaborators | List users with push access to a repository  |

#### Issues

| Operation                       | Description                                                   |
|---------------------------------|---------------------------------------------------------------|
| Create an issue                 | Open a new issue in a repository                              |
| Create an issue comment         | Post a comment on an existing issue                           |
| Get an issue                    | Retrieve details of an existing issue by number               |
| List commits                    | List commits on a repository branch                           |
| Search issues and pull requests | Search across issues and PRs using a query string             |
| Update an issue                 | Edit the title, body, state, assignees, labels, or milestone  |

#### Labels

| Operation      | Description                                                       |
|----------------|-------------------------------------------------------------------|
| Create a label | Create a new label with a name, color, and optional description   |
| Delete a label | Remove a label from a repository                                  |
| Get a label    | Retrieve details of a label by name                               |
| List labels    | List all labels defined in a repository                           |
| Update a label | Rename a label or change its color and description                |

> **Tip:** The Labels operations are especially useful for release automation — for example, creating backport labels when a release branch is cut, and deleting them once the branch is merged or closed.

#### Pull Requests

| Operation             | Description             |
|-----------------------|-------------------------|
| Create a pull request | Open a new pull request |

#### References

| Operation          | Description                                       |
|--------------------|---------------------------------------------------|
| Create a reference | Create a Git ref (branch or tag) from a given SHA |

#### Releases

| Operation        | Description                                        |
|------------------|----------------------------------------------------|
| Create a release | Publish a new release with a tag and release notes |
| Delete a release | Remove a release by ID                             |
| List releases    | Retrieve all releases for a repository             |
| Update a release | Edit an existing release                           |

#### Repositories

| Operation                          | Description                                                       |
|------------------------------------|-------------------------------------------------------------------|
| Create an organization invitation  | Invite a user to an organization by email                         |
| Create an organization repository  | Create a new repository inside an organization                    |
| Create or Update File Content      | Create or update a file in a repository (base64-encoded content)  |
| Delete a repository                | Permanently delete a repository                                   |
| Get a repository                   | Retrieve metadata for a repository                                |
| Get repository content             | Retrieve file or directory content from a repository              |
| List organization repositories     | List all repos belonging to an organization                       |
| List repository contributors       | List users who have contributed to a repository                   |
| Update a repository                | Edit repository settings (visibility, description, etc.)          |

## Inbound Connector (Webhook)

Trigger a BPMN process on GitHub webhook events.

The GitHub Webhook Connector supports the following start element types:

- **Start Event** — starts a new process instance when a GitHub event is received
- **Intermediate Catch Event** — resumes a waiting process on a matching event
- **Message Start Event** — starts a process via a message correlation on a GitHub event
- **Boundary Event** — catches a GitHub event on an active task

Configure your GitHub repository's webhook to point to the connector URL and set the
**Content type** to `application/json`. The connector verifies the webhook signature using
a shared secret.

## Resources

- [GitHub REST API documentation](https://docs.github.com/en/rest)
- [Camunda Connector documentation](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/github/)
