# Shift Out of Range Cleanup - Architecture

## Purpose
Detects shift operations (`<<`, `>>`, `>>>`) where the shift amount is a constant that is out of the valid range and replaces it with the effective masked value.

## Design
Follows the standard sandbox cleanup plugin pattern:
- `ShiftOutOfRangeFixCore` - Enum holding the transformation strategy
- `ShiftOutOfRangeHelper` - AST visitor that detects out-of-range shifts and creates rewrite operations
- `ShiftOutOfRangeCleanUpCore` - Core cleanup orchestration
- `ShiftOutOfRangeCleanUp` - UI wrapper

## How it works
In Java, shift amounts are masked:
- For `int`: `amount & 0x1f` (range 0-31)
- For `long`: `amount & 0x3f` (range 0-63)

The cleanup replaces out-of-range constants with their effective masked value.

## Inspired by
[NetBeans ShiftOutOfRange hint](https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/ShiftOutOfRange.java)
