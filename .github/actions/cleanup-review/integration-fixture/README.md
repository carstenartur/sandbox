# Cleanup review integration fixture

This small Eclipse Java project gives the pull-request workflow a binding-aware source file on which it can demonstrate an actual applicable Suggested Change.

`ExplicitEncodingExample.java` intentionally retains `Charset.forName("UTF-8")`. When that file changes in a pull request, the conservative review profile should propose `StandardCharsets.UTF_8` without changing behavior. The fixture is not part of the Maven reactor or production distribution.
