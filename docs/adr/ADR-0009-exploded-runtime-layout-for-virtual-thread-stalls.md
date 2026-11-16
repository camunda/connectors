# ADR-0009: Exploded Runtime Layout in Docker Images Against Virtual-Thread Stalls

## Status
Accepted

## Context

Since 8.9 the connector runtime runs outbound jobs on virtual threads by default. On JDK 24 and
later, the runtime can stall completely under load
([#8440](https://github.com/camunda/connectors/issues/8440),
[#10102](https://github.com/camunda/connectors/issues/10102)).

The root cause is a JDK limitation:

- A virtual thread stays pinned to its carrier while it loads a class.
- Since JDK 24, a virtual thread that holds a monitor can unmount.

When the runtime runs from the Spring Boot fat jar, every library is a nested jar, and class loading
and resource reads share the `UrlNestedJarFile` monitor of that jar. A job thread that reads a
resource, for example HttpClient's `VersionInfo` reading `version.properties`, holds the monitor and
unmounts. Virtual threads that load classes for the first time then wait on the same monitor while
pinned. Once every carrier is pinned, nothing runs.

There were three options:

1. Run the Docker images from an exploded (unpacked) jar layout and keep virtual threads on.
2. Run job handlers on a bounded platform-thread pool on JDK 24 and later. This rules the deadlock
   out for every deployment, but gives up virtual threads by default.
3. Both: platform threads by default, and the exploded layout to make opting into virtual threads
   safer.

Independently of the choice, `camunda.connector.virtual-threads.enabled=false` was not a usable
workaround: it fell back to the SDK executor, sized by `camunda.client.execution-threads`, which
defaults to 1, so the runtime processed one job at a time.

## Decision

We chose option 1, and accept that the fix lowers the risk of a stall rather than ruling it out.

- **Exploded layout.** The connector runtime Docker images (`camunda/connectors`,
  `camunda/connectors-bundle`, `camunda/connectors-bundle-saas`) unpack the fat jar into
  `/opt/app/runtime` at build time and start with `-cp "/opt/app/runtime:/opt/app/*"` and the same
  `PropertiesLauncher`. Each library then gets its own JDK `JarFile` for class loading, and resource
  reads use a separate cached `UrlJarFile`, so the shared monitor no longer exists. In a local
  repro the runtime stalled in 10 of 30 runs nested, and in 0 of 30 runs exploded.
- **Virtual threads stay the default.**
- **Usable opt-out.** With `camunda.connector.virtual-threads.enabled=false`, the runtime uses its
  own platform-thread pool sized to the larger of `camunda.client.execution-threads` and
  `camunda.client.worker.defaults.max-jobs-active`. This is the escape hatch if a stall still
  occurs.
- **Release scope.** The layout change ships in 8.11 only, because it is a breaking change for
  users who depend on the image internals (see Consequences). The opt-out fix is backported to
  `stable/8.9` and `stable/8.10`
  ([#10496](https://github.com/camunda/connectors/pull/10496),
  [#10497](https://github.com/camunda/connectors/pull/10497)), since those releases also default to
  virtual threads on JDK 24 and later.

## Consequences

### Positive

- The stall pattern seen in #8440 and #10102 is gone from the Docker images, and virtual threads
  stay on.
- Disabling virtual threads is now a real workaround, on every deployment type and in 8.9 and 8.10.
- The runtime starts faster exploded: 11.6 s instead of 17.8 s for the default bundle.

### Negative

- **Not airtight.** Rarer patterns remain possible in theory, for example an unpinned explicit
  `ClassLoader.loadClass` that holds a `JarFile` while it unmounts. Only platform threads rule the
  deadlock out.
- **Docker images only.** c8Run and anyone running the fat jar with `java -jar` still use the
  nested layout.
- **Memory.** About 40 MB more live heap and 20 to 50 MB more RSS, because the JDK `ZipFile` keeps
  each jar's central directory on the heap. The runtime also keeps about 690 file descriptors open
  instead of about 30.
- **Breaking for image internals.** A custom command, entrypoint, or exec probe that uses
  `-cp "/opt/app/*"` must change to `-cp "/opt/app/runtime:/opt/app/*"`. Tooling that copies,
  patches, or inspects `/opt/app/*-with-dependencies.jar` must use `/opt/app/runtime` instead.
  The 8.11 release announcements list this as a breaking change.
- **CI coverage.** PR image builds use the Dockerfiles from `main`, so a Dockerfile change in a PR
  is only built from the PR's own Dockerfile after merge, or by the label-gated AI agent tests for
  the default bundle.

Revisit option 2 if a stall is reported on the exploded layout, or option 1 entirely once the JDK
no longer pins virtual threads during class loading.
