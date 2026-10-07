---
name: create-issue
description: Create a GitHub issue in camunda/connectors with the correct template, labels (kind, component, severity, likelihood, affects, scope), native issue type, and parent link. Use when asked to create, file, or open an issue — for bugs, features, or tasks.
---

# Create Issue

Creates a well-formed GitHub issue in `camunda/connectors` following the repo's templates. Prevents
missing labels, skipped required fields, and missing parent links.

Form-driven labels (`severity/*`, `likelihood/*`, `affects/*`, `discovered-by/*`) are applied
automatically after creation by `.github/workflows/OPENED_ISSUE_LABELER.yml`, which scans the body
for the dropdown HTML comment markers using `.github/opened_issue_labeler.yml`. This skill applies
the remaining labels explicitly. `gh issue create` also bypasses the native Issue Type, so that is
set explicitly too.

## Prerequisites

```bash
gh auth status  # must succeed
```

If this fails, do not abort. Check whether `mcp__github__*` tools are available in this session; if
so, use them for the rest of this flow instead of the `gh` examples below. MCP tools use separate
credentials, so they often keep working through a `gh` auth failure. If they are not available
either, retry `gh auth status` once before asking the user to check their authentication.

## Procedure

### Step 1 — Pick the template

```bash
ls .github/ISSUE_TEMPLATE/
```

That listing is authoritative. Default mapping:

| User intent                    | Template              | `kind` label       | Issue Type |
|--------------------------------|-----------------------|--------------------|------------|
| Bug, defect, crash, regression | `BUG_REPORT.yml`      | `kind/bug`         | `Bug`      |
| Feature, improvement           | `FEATURE_REQUEST.md`  | `kind:enhancement` | `Feature`  |
| Task, chore, cleanup           | `TASK.md`             | `kind:task`        | `Task`     |

Note the label spelling: `kind/bug` uses a slash, while `kind:enhancement` and `kind:task` use a
colon. Always take the label from the template's `labels:` front matter rather than from this
table if they differ. If the intent is ambiguous, ask the user to pick one.

### Step 1.5 — Check for existing issues

Search for likely duplicates using the issue's likely title keywords. `--state` accepts one value
at a time, so run both:

```bash
gh search issues '<title keywords>' --repo camunda/connectors --state open --limit 10
gh search issues '<title keywords>' --repo camunda/connectors --state closed --limit 10
```

If a close match turns up, show its number, state, and title, and ask whether to comment on it,
link the new work to it, or proceed with a new issue anyway. Never silently create a near-duplicate.

### Step 2 — Read the template and collect field values

```bash
cat ".github/ISSUE_TEMPLATE/<chosen>"
```

Ask the user for a free-form description. Infer a value for every field in the template from it:

- `BUG_REPORT.yml`: skip `type: markdown` items; walk the `body:` list (dropdowns and textareas).
- `FEATURE_REQUEST.md` / `TASK.md`: the Markdown body below the front matter is the structure.
  Keep its headings and replace the placeholder text.

Only ask a follow-up when the description gives too little signal. Batch all uncertain fields into a
single question. If `validations.required: true` and a value can't be inferred, the user must
provide one. Leave optional fields empty when nothing can be inferred.

For bugs, if impact, reproduction path, or the affected connector is unclear, ask about those first;
they drive severity, likelihood, and the `scope:*` label.

### Step 3 — Determine labels

Apply explicitly:

1. **`kind` label** from Step 1.
2. **`component/connectors`** — always, on every issue.
3. **`scope:*`** — the connector or area concerned (e.g. `scope:kafka`, `scope:rest`,
   `scope:runtime`, `scope:sdk`). Check the live set and use the best match, none if nothing fits:

   ```bash
   gh label list --repo camunda/connectors --search "scope:" --limit 100
   ```

4. Optionally `support` when the issue originates from a support ticket.

**Bugs: do not apply these yourself.** The labeler derives them from the dropdown markers in the
body (Step 5), so the markers must be rendered correctly. For the summary, state which labels to
expect:

| Form value                                         | Label (applied by the labeler)                                                           |
|----------------------------------------------------|------------------------------------------------------------------------------------------|
| Severity: Low / Medium / High / Critical / Unknown | `severity/low`, `severity/mid`, `severity/high`, `severity/critical`, `severity/unknown` |
| Likelihood: Low / Medium / High / Unknown          | `likelihood/low`, `likelihood/mid`, `likelihood/high`, `likelihood/unknown`              |
| Affected version: 8.X (one per selection)          | `affects/8.X`                                                                            |
| Discovered by (if not "Not applicable")            | `discovered-by/*`, `qa/automation-found`                                                 |

Medium maps to `mid`. Severity guide: `critical` = stop-the-world with no workaround, `high` =
noticeable impact with no workaround, `mid` = noticeable impact with a workaround, `low` = little
to no impact.

**Verify the explicit labels exist before creating the issue**; `gh issue create` fails on unknown
labels:

```bash
gh label list --repo camunda/connectors --limit 300 --json name --jq '.[].name'
```

If a needed label is missing, tell the user and ask whether to create it (`gh label create`) or
skip it. Do not create labels without asking.

### Step 4 — Ask for the parent issue

> "Does this issue have a parent issue or epic? If so, please provide the issue number or URL."

If provided, verify it and fetch its title:

```bash
gh issue view <number> --repo camunda/connectors --json number,title,url
```

If there is no parent, omit the parent link.

### Step 5 — Compose the issue body

Mirror the template exactly; do not invent sections.

- **`BUG_REPORT.yml`:** for each item, render `attributes.label` as a `### Heading` and the value
  as content. For dropdowns, render the chosen option's text verbatim, including its HTML comment
  markers (e.g. `<!-- High- -->`), so the body matches what the GitHub form would produce.
- **`FEATURE_REQUEST.md` / `TASK.md`:** reuse the template's `__Heading__` sections and fill them in.

Append a **Links** line with the parent issue URL if one was provided.

When pointing at specific lines of repository code, use a **stable GitHub permalink** (commit SHA
in the URL) rather than a bare `path:line`.

### Step 6 — Propose a summary and ask for approval

First scan the body for sensitive data and replace UUIDs, customer/user IDs, internal hostnames,
tokens, and email addresses (including inside pasted logs) with `<redacted>`.

Print the summary and wait for explicit confirmation. Do NOT call `gh issue create` yet.

```
Issue summary
────────────────────────────────────────────
Title:   <proposed title>
Type:    <Bug | Feature | Task>
Labels:  <explicit labels from Step 3>  (severity/*, likelihood/*, affects/* applied automatically via labeler)
Parent:  https://github.com/camunda/connectors/issues/<N>  (or "none")

Body:
<full proposed body>
────────────────────────────────────────────
Create this issue? (y/N)
```

If the user requests changes, revise and ask again.

### Step 7 — Create the issue, set the type, link the parent

After confirmation, create the issue, passing the explicit labels from Step 3:

```bash
body_file=$(mktemp)
printf '%s' "<body>" > "$body_file"

gh issue create \
  --repo camunda/connectors \
  --title "<title>" \
  --body-file "$body_file" \
  --label "<kind label>" \
  --label "component/connectors" \
  --label "<each further label>"   # repeat per label

rm -f "$body_file"
```

Capture the new issue number from the returned URL, then fetch its database ID and node ID (the
database ID is for the sub-issues API, the node ID for setting the Issue Type):

```bash
gh api repos/camunda/connectors/issues/<new-issue-number> --jq '{id: .id, node_id: .node_id}'
```

Set the native Issue Type (Bug, Feature, or Task from Step 1), resolving its ID by name:

```bash
type_id=$(gh api graphql -f query='{
  repository(owner: "camunda", name: "connectors") {
    issueTypes(first: 20) { nodes { id name } }
  }
}' | jq -r --arg t "<Bug|Feature|Task>" \
  '.data.repository.issueTypes.nodes[]
   | select(.name | ascii_downcase == ($t | ascii_downcase)) | .id')

gh api graphql -f query="mutation {
  updateIssue(input: {id: \"<node_id>\", issueTypeId: \"$type_id\"}) {
    issue { id }
  }
}"
```

If a parent was provided, register the native sub-issue relationship:

```bash
gh api \
  --method POST \
  repos/camunda/connectors/issues/<parent-number>/sub_issues \
  --input - <<< "{\"sub_issue_id\": <new-issue-database-id>}"
```

Print the URL of the created issue.
