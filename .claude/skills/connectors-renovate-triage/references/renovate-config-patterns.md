# renovate.json patterns

`renovate.json` already has a handful of package-specific rules that exist for exactly the
reason this skill exists — a bump broke something, so a rule was added to stop it recurring.
Match their shape and style rather than inventing a new format. The recurring elements:
a `description` explaining the *why* in plain English, a `matchPackageNames` (or
`matchManagers`) scope, and either `enabled: false` or an `allowedVersions` exclusion.

## Block a specific update type for a package

This is the most common shape — used for things like disabling major-version bumps because
a downstream library isn't ready for the breaking changes yet:

```jsonc
{
  "description": "<why, specifically — what breaks and at what version, so a future reader doesn't have to rediscover it>",
  "matchUpdateTypes": ["major"],
  "matchPackageNames": ["<groupId>:<artifactId>"],
  "enabled": false
}
```

Real examples already in the file follow this exact shape — pinning a package to its
current major line because the next major drops a package that's still in use, and blocking
major bumps for a couple of libraries because a newer major would break compatibility with
another dependency in the tree (langchain4j, in one case). If your Step 5 diagnosis lands on
"real incompatibility, block this update type," this is the template to copy.

## Exclude one bad release without blocking the whole update type

When the problem is a single broken release rather than the update direction being wrong:

```jsonc
{
  "description": "<why this specific version is bad>",
  "matchPackageNames": ["<groupId>:<artifactId>"],
  "allowedVersions": "!/^<bad-version-regex>$/"
}
```

There's a real example of a related pattern already in the file, excluding a whole suffix
family of versions (Confluent's `-ce`/`-ccs`/`-cp` build variants) that get republished under
the same coordinates as the real Apache releases but shouldn't be treated as upgrades to
them. Use `allowedVersions` rather than `enabled: false` whenever you want future *good*
versions to keep flowing — it's the difference between "skip this one bad thing" and
"stop touching this package."

## Scope a rule to one base branch only

If the break is specific to one branch (e.g. an older `stable/8.x` line pinned to an older
transitive dependency that a newer version conflicts with), add `matchBaseBranches` rather
than writing a blanket rule that would also suppress a perfectly fine update on `main`:

```jsonc
{
  "matchBaseBranches": ["stable/8.7"],
  "matchPackageNames": ["<groupId>:<artifactId>"],
  "matchUpdateTypes": ["minor"],
  "enabled": false
}
```

The file already does exactly this for a couple of packages that only need special
treatment on specific stable branches.

## Base-branch config caveat

All of the branch-scoped rules above live in the *one* `renovate.json` at the repo root —
there isn't a separate copy per branch as far as this file shows, and `matchBaseBranches`
is how a single config produces different behavior per branch. Before adding a
branch-scoped rule, still run `git show <base>:renovate.json` for the branch in question
to make sure it hasn't diverged into its own copy at some point; if it has, you'd need to
edit that branch's file directly instead of (or in addition to) the root one.

## Detecting update type

Compare old → new semver from the PR diff:
- Major segment differs → `major`
- Minor segment differs, major same → `minor`
- Patch segment differs only → `patch`
- No version string change, only a hash/digest changed (Docker, GitHub Actions) → `digest`

## Before adding a new rule

Skim the existing `packageRules` array first. It already blocks majors for a couple of
Elasticsearch/OpenSearch clients, pins one SDK to its current major line, blocks a JSON
schema validator's major line, and excludes the Kafka Confluent-suffix versions — if the
package you're dealing with is already in there, extend or correct that entry rather than
adding a second one that could conflict with it.
