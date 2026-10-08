# Cleanup review integration fixture

This small Eclipse Java project gives the pull-request workflow binding-aware source files on which it can demonstrate complete cleanup commits. It is outside the Maven reactor and production distribution.

Both classes intentionally retain `Charset.forName(...)`. The conservative profile should replace those calls with `StandardCharsets` without changing behavior:

- `ExplicitEncodingExample.java` demonstrates an import outside the original PR diff context.
- `GroupedEncodingExample.java` demonstrates two distant conversions that both require the new import.

The bot commit must contain every import and conversion across both files. Its ordinary GitHub comparison shows the actual changed lines, and the cleanup PR targets the original PR branch so its merge accepts the complete result. The Node regressions create their own Git histories to verify completeness independently of which fixture lines a later PR exposes.
