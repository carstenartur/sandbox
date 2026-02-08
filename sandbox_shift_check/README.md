# Shift Out of Range Cleanup

Eclipse JDT cleanup that detects shift operations with out-of-range shift amounts and replaces them with the effective masked value.

## What it does

In Java, shift amounts are automatically masked:
- For `int` (and `byte`, `short`, `char`): only the low 5 bits are used (range 0-31)
- For `long`: only the low 6 bits are used (range 0-63)

This cleanup detects constant shift amounts that are outside these ranges and replaces them with the effective masked value, making the actual Java behavior explicit.

## Examples

| Before | After | Explanation |
|--------|-------|-------------|
| `x << 32` | `x << 0` | For int: 32 & 31 = 0 |
| `x >> 33` | `x >> 1` | For int: 33 & 31 = 1 |
| `x << -1` | `x << 31` | For int: -1 & 31 = 31 |
| `y << 64L` | `y << 0` | For long: 64 & 63 = 0 |

## Inspired by

[NetBeans ShiftOutOfRange hint](https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/ShiftOutOfRange.java)
