# Iterator-to-Enhanced-For Reference

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [Maintenance status](TODO.md)

## Status

The iterator-loop cleanup in this module was contributed to Eclipse JDT UI and is maintained there. The Sandbox implementation remains as historical source and regression-test material, but it is **not registered as an active cleanup**.

Current runtime contract:

- `sandbox_tools` does not contribute to `org.eclipse.jdt.ui.cleanUps`;
- `org.eclipse.jdt.ui` is the sole runtime owner of `org.eclipse.jdt.ui.cleanup.toolscleanup`;
- installing or updating `sandbox_tools_feature` therefore cannot instantiate a second cleanup implementation;
- the feature remains installable to update older installations safely and to provide its cheat sheet and offline Help.

## What the historical implementation does

It recognizes canonical iterator-driven `while` loops and rewrites them as enhanced `for` loops when the iterator has no behavior that the target form cannot preserve.

**Before**

```java
Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    String value = iterator.next();
    consume(value);
}
```

**After**

```java
for (String value : values) {
    consume(value);
}
```

The retained implementation rejects cases such as iterator removal, multiple advancement, escaped iterator identity, or non-canonical traversal.

## Using the maintained cleanup

Use the standard Eclipse Java cleanup workflow:

1. Select Java source.
2. Choose **Source → Clean Up...**.
3. Configure a normal Eclipse JDT cleanup profile.
4. Enable the standard option for conversion to enhanced `for` loops.
5. Review the preview and run the relevant tests.

There is no separate Sandbox cleanup option or Sandbox Tools cleanup tab.

## What remains in Sandbox

- the historical implementation under `org.sandbox.jdt.internal.*`;
- the original regression tests in `sandbox_tools_test`;
- a cheat sheet that guides users to the maintained JDT cleanup;
- offline Help explaining runtime ownership and the retirement decision.

New production fixes belong in Eclipse JDT UI. Sandbox does not promise periodic synchronization; compare implementations only for a concrete research, regression, or contribution need.

## Verification

The runtime registration test checks that exactly one contribution exists for `org.eclipse.jdt.ui.cleanup.toolscleanup` and that its contributor is `org.eclipse.jdt.ui`.

Run the module and its dependencies with:

```bash
xvfb-run --auto-servernum mvn --no-transfer-progress -pl sandbox_tools_test -am verify
```

## Documentation

- [Architecture and ownership](ARCHITECTURE.md)
- [Maintenance status](TODO.md)
- [Installed Eclipse Help](../sandbox_tools_help/html/index.html)

## License

Eclipse Public License 2.0 (EPL-2.0)
