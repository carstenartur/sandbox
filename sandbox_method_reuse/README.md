# Method Reuse Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Method Reuse** plugin provides two separate Eclipse Cleanup transformations:

1. **Extract repeated code sequences into a shared method** — finds a contiguous statement sequence that occurs more than once inside one Java type, delegates the semantic proof and rewrite to Eclipse JDT Extract Method, creates one private method, and replaces every duplicate accepted by JDT.
2. **Replace inline code sequences with calls to an existing method** — keeps an already available compatible method and replaces a matching inline sequence with a call to it.

These modes solve related but different problems. The first creates the common method; the second reuses one that already exists.

## Repeated-sequence extraction

The Cleanup profile defines a minimum sequence length of 3, 4, or 5 statements. The default is 3. Candidate discovery is deliberately only a performance filter. The actual transformation is Eclipse JDT's `ExtractMethodRefactoring` with duplicate replacement enabled.

JDT remains authoritative for:

- input parameters and variable mappings;
- a possible return value;
- static or instance context;
- checked exceptions;
- legal `return`, `break`, and `continue` behavior;
- destination and method-name conflicts;
- the duplicate occurrences that can be replaced safely.

A coarse structural or textual match never authorizes a change by itself.

### Example

Before:

```java
void first(String value) {
    String text = value.trim();
    text = text.toLowerCase();
    System.out.println(text);
}

void second(String input) {
    String text = input.trim();
    text = text.toLowerCase();
    System.out.println(text);
}
```

After:

```java
void first(String value) {
    extractedSequence(value);
}

private void extractedSequence(String value) {
    String text = value.trim();
    text = text.toLowerCase();
    System.out.println(text);
}

void second(String input) {
    extractedSequence(input);
}
```

## Current boundary

- Duplicate replacement follows JDT's established same-type, same-compilation-unit Extract Method contract.
- One best independent extraction is performed per compilation unit and Cleanup run; JDT replaces all valid occurrences of that selected sequence.
- Running Cleanup again can extract another independent repeated group.
- Anonymous-class and cross-type common-method placement are not automatic.
- Candidate length and validation attempts are bounded to keep interactive Cleanup responsive.
- The structural extraction mode is disabled for save actions and requires explicit Cleanup preview.

## Usage

1. Open **Java → Code Style → Clean Up** and edit a profile.
2. Enable **Extract repeated code sequences into a shared method**.
3. Choose the minimum repeated sequence length.
4. Run **Source → Clean Up...** on the relevant class or source selection.
5. Review the extracted method, parameters, return handling, exceptions, and every replacement in the LTK preview.
6. Compile and run the affected tests.

The installed Eclipse Help contains the same contract and examples.

## Testing

Focused transformation tests are in `sandbox_method_reuse_test` and cover:

- extraction of one repeated sequence and replacement of all JDT-valid occurrences;
- configurable minimum sequence length;
- non-repeated negative cases;
- the existing-method reuse mode;
- truthful Cleanup preview text.

The read-only Eclipse Help merge gate in `sandbox_usage_view_test` additionally drives the real **Source → Clean Up...** workflow, verifies the genuine LTK diff, applies the extraction, and proves byte-exact Undo.

Run the module through the normal Maven/Tycho reactor, for example:

```bash
mvn -pl sandbox_method_reuse_test -am verify
```

## License

Eclipse Public License 2.0 (EPL-2.0)
