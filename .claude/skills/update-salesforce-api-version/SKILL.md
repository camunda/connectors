---
name: update-salesforce-api-version
description: Bump the Salesforce connector's default apiVersion to the latest Salesforce REST API release, and regenerate/version the element template correctly. Use when asked to update, bump, or refresh the Salesforce API version, or when Salesforce releases a new API version (Spring/Summer/Winter release) that should be picked up.
---

# Update the Salesforce connector's default API version

The Salesforce connector's `apiVersion` property (used to build every sObject/SOQL/Composite
request URL) has a hardcoded default value in the generator source. Salesforce ships a new REST
API version roughly three times a year (Spring/Summer/Winter releases); this skill keeps the
connector's default current.

## 1. Find the latest Salesforce REST API version

Check the official versions page — it marks the current release as "Latest":

```
https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_versions.htm
```

Note the version number (e.g. `67.0`) and confirm it's higher than the current default (see step 2).
Don't rely on training data for this number — always look it up fresh, since it changes several
times a year.

## 2. Update the generator source

File: `connectors/salesforce/src/test/java/io/camunda/connector/salesforce/GenerateElementTemplate.java`

- Find the `apiVersion` `StringProperty` (search for `.id("apiVersion")`) and update its
  `.value("vNN.0")` call to the new version, formatted as `"vNN.0"` (Salesforce version strings
  always have a `.0` suffix, e.g. `"v67.0"`).
- Bump the element template's version. This repo's convention is a **single source of truth**
  constant, `TEMPLATE_VERSION` (a `private static final long` near the top of the class) —
  increment it by 1. It feeds both `ElementTemplateBuilder#version(TEMPLATE_VERSION)` and
  `CommonProperties.version(TEMPLATE_VERSION)` in `connectorGroup()`. If a future refactor
  removed this constant and reintroduced two separate literals, restore the constant rather than
  editing both literals independently — that duplication is exactly what caused drift before.

Do **not** bump the version if you're only fixing a typo/non-functional change elsewhere; only bump
when the generated template's actual content changes (which an apiVersion default change always
does).

## 3. Archive the outgoing version as a versioned snapshot

Before regenerating, the *current* (pre-bump) template becomes historical. Add
`connectors/salesforce/element-templates/versioned/salesforce-connector-<old-version>.json` as an
**exact byte-for-byte copy of what's currently on `main`** — do NOT regenerate it locally and copy
that output. The generator's presets use `java.util.Map.of(...)` for 2-key maps, whose iteration
order is randomized per JVM process (JDK 9+ `SALT32L` salting) — a fresh `mvn exec:java` run can
serialize semantically identical presets with different key order than the JVM run that produced
main's actual committed bytes, which fails the CI `check-versioned-element-templates` job's
byte-for-byte `cmp` despite the content being equivalent. Instead:

```bash
git fetch origin main --quiet
git show origin/main:connectors/salesforce/element-templates/salesforce-connector.json \
  > connectors/salesforce/element-templates/versioned/salesforce-connector-<old-version>.json
```

Verify it's an exact match before moving on:

```bash
diff <(git show origin/main:connectors/salesforce/element-templates/salesforce-connector.json) \
  connectors/salesforce/element-templates/versioned/salesforce-connector-<old-version>.json && echo IDENTICAL
```

Do **not** add a snapshot for the *new* version — that's the responsibility of whichever future
change next bumps past it, per the same reasoning your snapshot follows now.

## 4. Regenerate and verify

```bash
./mvnw -q -pl connectors/salesforce -am install -DskipTests
./mvnw -q -pl connectors/salesforce exec:java \
  -Dexec.mainClass=io.camunda.connector.salesforce.GenerateElementTemplate \
  -Dexec.classpathScope=test
git status --short -- connectors/salesforce   # should show only the .java file, the regenerated
                                                # non-versioned JSON, and the new versioned snapshot
```

Run the full test suite and the cross-repo element template validator (the same one CI runs):

```bash
./mvnw -q -pl connectors/salesforce test
./mvnw -q --batch-mode -pl element-template-generator/validator -am package -DskipTests
./element-template-generator/validator/target/appassembler/bin/element-template-validator
```

The validator must report `No findings` for this to be mergeable — pay particular attention to
`condition-property-order` (a property's condition can't reference another property that appears
later in the generated `properties[]` array — array order is fixed by
`reorderPropertiesByGroup()`'s group sequence, so a moved/renamed property can trip this) and
`current-version-bump` (checks the versioned-snapshot bookkeeping from step 3).

Finally, sanity-check the versioned-snapshot CI gate locally before pushing:

```bash
git fetch origin main --quiet
TARGET_BRANCH=main bash .github/workflows/scripts/check_versioned_element_templates.sh
```

## 5. Commit

One commit covering the version bump, snapshot, and regenerated JSON together (they're one
logical change). Mention the old and new API version and template version in the message body.
