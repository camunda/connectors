# Manager-specific mechanics

The repo's `renovate.json` runs four managers: `maven`, `dockerfile`, a custom `regex`
manager for `docker-images.properties`, and `github-actions`. Each stores its version
differently, so "revert the version" means something slightly different in each case.

## Maven

Versions live either as a property in a `pom.xml` (often the root or `parent/pom.xml`,
declared like `<version.jackson>2.22.1</version.jackson>`), directly on a `<dependency>` in
whichever module owns it, or — for anything pulled in via a BOM import like
`com.google.cloud:libraries-bom` — controlled by a single BOM version that transitively
pins dozens of other coordinates at once.

That last case is worth pausing on: if the culprit is a BOM, reverting *just* the literal
line Renovate touched puts the BOM back, which is usually sufficient, but double check the
error isn't actually about one of the BOM's *managed* dependencies moving in a way that a
plain revert of the BOM version won't undo cleanly (rare, but worth a second look if the
first revert doesn't fix it).

In every case, the PR diff from Step 2 already shows the exact file and line Renovate
changed — the revert is restoring that line to its old value, not searching the module tree
from scratch.

## Dockerfile

Three Dockerfiles carry version pins as `FROM` lines with a tag and/or digest:
- `connector-runtime/connector-runtime-application/Dockerfile`
- `bundle/default-bundle/Dockerfile`
- `bundle/camunda-saas-bundle/Dockerfile`

Base images come from Minimus (`reg.mini.dev/...`), a hardened-image provider — a bump here
that breaks the build is reverting the tag/digest in the `FROM` line. Same rule as Maven:
the diff already tells you old vs. new.

## `docker-images.properties` (custom regex manager)

Per the README, integration tests that use Testcontainers list the images they need in a
`docker-images.properties` file under a module's `test/resources/` — there can be more than
one across the repo, one per module that needs it. The PR diff will show which file(s)
changed. A bump here breaking a `@SlowTest` integration test is usually reproducible locally
if you have Docker running (see Step 4b in the main skill for the bisection approach).

**Quirk worth knowing before writing a `packageRule` for one of these**: the custom manager
is configured with `"depNameTemplate": "{{{matchString}}}"`, meaning Renovate's internal
"package name" for these entries is the *entire matched line* (`key=image:tag`), not a
clean image name. That means a `matchPackageNames` rule written against today's string will
stop matching once the value changes again. If you need to block a specific image from
bumping, scope the rule by `matchManagers: ["regex"]` plus `matchFileNames` (a glob for the
specific `docker-images.properties` path) instead of by package name — and it's worth a
quick check against `docs.renovatebot.com` for the regex manager if the exact matcher
behavior matters, rather than assuming.

## GitHub Actions

`.github/workflows/*.yml` files pin actions as `uses: owner/repo@<sha-or-tag>`. A digest bump
that breaks a workflow is reverted the same way — restore the old ref in the `uses:` line.
There's already a real example of scoping a rule to one specific action in the existing
config (see `references/renovate-config-patterns.md`) — a good template to copy the shape
of if a different action needs the same treatment.

Worth flagging specifically: some of the actions managed here are linters/checkers
(hadolint, actionlint, and similar), not runtime dependencies. When one of these fails
after a bump, it's frequently not "broken" at all — it's stricter, and it's flagging
something real in the Dockerfile/workflow/code it lints. See Step 5's fourth case in the
main skill before reaching for a revert on one of these.
