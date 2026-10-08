# Cleanup review integration fixture

This small Eclipse Java project gives the pull-request workflow binding-aware source files on which it can demonstrate complete cleanup reviews. The fixtures are not part of the Maven reactor or production distribution.

Both classes intentionally retain `Charset.forName(...)`. The conservative review profile should propose `StandardCharsets` without changing behavior:

- `ExplicitEncodingExample.java` has a small change near the bottom. Its new import is outside the PR diff context. The review must show the full file patch, with no separately applicable call-only fragment.
- `GroupedEncodingExample.java` is introduced as a new PR file with two separated conversions. The PR exposes the entire source. A single grouped suggestion must include the new import and both conversions, with the unchanged lines between them.

The fixture cases depend on the PR diff: after these files are merged, later PRs may expose different line ranges. The Node regressions create their own exact Git histories for durable range and grouping checks.
