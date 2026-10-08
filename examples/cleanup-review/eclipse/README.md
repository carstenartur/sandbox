# Standalone Eclipse cleanup consumer

Copy this directory **including its hidden files** to a new repository. It is
an independent Java 11 Eclipse project, not a Sandbox module. It requires no
Sandbox sources, Maven reactor, extra secret or copied cleanup profile.
Existing Eclipse projects should copy **only `.github/workflows/cleanup.yml`**;
do not replace their `.project`, `.classpath`, `.settings` or source level.

Commit the example on your default branch. Create a feature branch, make a
small change in `src/example/EncodingExample.java`, and open a PR. The bot's
**Review cleanup diff** link should show one new import and both replacements.
Use **Open cleanup PR**, create that PR and merge it into the feature branch
(not the default branch) to accept the complete result. This is a proposal,
not an automatic push to your original PR branch. Fork PRs are excluded.

Compile and run before and after; the output must remain `2:1`:

```bash
javac --release 11 -d bin src/example/EncodingExample.java
java -cp bin example.EncodingExample
```

The example workflow follows the development channel. Pin a reviewed full
Sandbox commit SHA and a tested cleanup-image digest for reproducible use.
See [the integration guide](https://github.com/carstenartur/sandbox/blob/main/GITHUB_ACTIONS.md)
for permissions, Eclipse/PDE dependencies, Maven/Gradle boundaries and diagnosis.

The `consumer-e2e` Maven profile copies this example to a separate Git repository,
runs the selected Docker image, compiles and executes before/after, checks the
complete patch round trip, and verifies unchanged Eclipse metadata. That test
qualifies the consumer files and runner; it does not claim a separate hosted
repository or an automatically completed GitHub merge.

CI selects the candidate image from the existing Linux distribution build. The
Java 11 Charset example requires the corrected Java 7 DSL guard; an older image
may silently miss these calls. The integration guide records that limitation.
