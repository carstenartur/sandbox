# Sandbox Tools Reference Architecture

> **Navigation**: [Main README](../README.md) | [Module README](README.md) | [Maintenance status](TODO.md)

## Architectural status

`sandbox_tools` is a retained reference bundle for the iterator-loop-to-enhanced-for cleanup that was upstreamed to Eclipse JDT UI. It is no longer a competing runtime implementation.

The ownership boundary is deliberate:

```text
Eclipse JDT UI
    owns the active cleanup extension and production behavior

sandbox_tools
    retains historical implementation classes and a cheat sheet
    contributes no org.eclipse.jdt.ui.cleanUps element

sandbox_tools_test
    retains transformation tests
    verifies the runtime extension registry has one JDT-owned contribution

sandbox_tools_help
    explains how to use the maintained JDT cleanup and why Sandbox is dormant
```

## Runtime extension contract

The cleanup id is:

```text
org.eclipse.jdt.ui.cleanup.toolscleanup
```

Only `org.eclipse.jdt.ui` may contribute that id in the current Sandbox product. `sandbox_tools/plugin.xml` intentionally contains no `org.eclipse.jdt.ui.cleanUps` extension.

This matters because the JDT cleanup registry enumerates matching extension elements. Reusing the same id in Sandbox would not make the older implementation override the upstream one; both descriptors could be instantiated for the same option.

The regression test inspects the live Eclipse extension registry and asserts:

1. exactly one matching cleanup element exists;
2. its contributor is `org.eclipse.jdt.ui`;
3. `sandbox_tools` is not among the contributors.

## Update and packaging decision

`sandbox_tools_feature` remains installable and keeps the existing feature and bundle identities. This is preferable to merely removing it from the update-site category:

- users with an older installation receive the replacement bundle;
- the replacement bundle lacks the obsolete cleanup extension;
- the duplicate registration is therefore neutralized after update;
- the cheat sheet and reference Help remain available.

The feature is explicitly labelled as a reference feature. It is not advertised as an independently maintained cleanup.

## Retained implementation

The archived implementation consists primarily of:

- `UseIteratorToForLoopCleanUp` — JDT cleanup wrapper;
- `UseIteratorToForLoopCleanUpCore` — option and fix integration;
- `UseIteratorToForLoopFixCore` — operation selection and rewrite dispatch;
- `WhileToForEach` — recognition and rewriting of canonical iterator-driven `while` loops.

The code depends on resolved JDT AST and binding information. It validates traversal structure before replacing the loop.

## Historical transformation contract

A typical accepted source form is:

```java
Iterator<Element> iterator = elements.iterator();
while (iterator.hasNext()) {
    Element element = iterator.next();
    use(element);
}
```

The target is:

```java
for (Element element : elements) {
    use(element);
}
```

The retained analyzer rejects patterns when iterator operations, identity, mutation, advancement count, or control flow cannot be represented safely by an enhanced `for` loop.

## Maintenance boundary

Production behavior, user-facing cleanup options, and bug fixes belong to Eclipse JDT UI. Sandbox maintenance is limited to:

- keeping the duplicate registration absent;
- keeping the reference bundle buildable on the current target;
- preserving useful historical tests and documentation;
- performing an upstream comparison only for a concrete research or contribution task.

There is no standing commitment to synchronize the archived implementation periodically.

## Verification

The narrow verification command is:

```bash
xvfb-run --auto-servernum mvn --no-transfer-progress -pl sandbox_tools_test -am verify
```

The complete distribution build additionally proves that the reference feature remains installable without reintroducing the retired extension.

## References

- [Eclipse JDT UI repository](https://github.com/eclipse-jdt/eclipse.jdt.ui)
- [Module README](README.md)
- [Installed Help overview](../sandbox_tools_help/html/index.html)
