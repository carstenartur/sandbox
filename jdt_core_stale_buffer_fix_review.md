# Review: Fix for Stale Buffer Reuse (eclipse.jdt.ui#736)

## PR Under Review

- **PR**: [carstenartur/eclipse.jdt.core#14](https://github.com/carstenartur/eclipse.jdt.core/pull/14)
- **Issue**: [eclipse-jdt/eclipse.jdt.ui#736](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/736)
- **Related**: [eclipse-jdt/eclipse.jdt.core#2596](https://github.com/eclipse-jdt/eclipse.jdt.core/pull/2596) (thread-safety fix), [Bug 550180](https://bugs.eclipse.org/bugs/show_bug.cgi?id=550180)

## Summary

The `AnnotateAssistTest1d8` tests fail intermittently with `source=null` errors when jar files are deleted and recreated with the same path between tests. The proposed fix adds stale buffer validation in `ClassFile.openBuffer()` and `ModularClassFile.openBuffer()`.

## Analysis

### The fix is conceptually correct but incomplete

The race condition is:

1. **Thread A** (test cleanup): `Buffer.close()` sets `contents = null` and `flags |= F_IS_CLOSED` (synchronized)
2. **Thread A** (continues): fires `notifyChanged()` → `bufferChanged()` → `removeBuffer()` (asynchronous to close)
3. **Thread B** (new test): `getBuffer()` → finds buffer in cache (before `removeBuffer()` completes)
4. **Thread B**: Gets stale buffer with null contents → `source=null`

### Critical gap: The fix is in the wrong method

The PR adds stale buffer detection in `ClassFile.openBuffer()` and `ModularClassFile.openBuffer()`. However, **these methods are only called when no buffer is found in the cache**. The actual flow is:

```java
// Openable.getBuffer() - the CALLER of openBuffer()
public IBuffer getBuffer() throws JavaModelException {
    if (hasBuffer()) {
        IElementInfo info = getElementInfo();
        IBuffer buffer = getBufferManager().getBuffer(this);  // (1) Cache lookup
        if (buffer == null) {
            buffer = openBuffer(null, info);  // (2) Only called if cache returns null
        }
        if (buffer instanceof NullBuffer) {
            return null;
        }
        return buffer;  // (3) Returns potentially stale buffer from step (1)!
    }
    return null;
}
```

**For top-level class files** (which is the failing case in `AnnotateAssistTest1d8`):
- `getBufferManager().getBuffer(this)` in step (1) finds the stale buffer
- Since it's non-null, `openBuffer()` is never called
- The stale buffer is returned directly at step (3)
- The fix in `ClassFile.openBuffer()` is never reached!

**For inner class files** (e.g., `Foo$Bar.class`):
- `getBufferManager().getBuffer(this)` returns null (buffer stored under outer class key)
- `openBuffer()` IS called, which looks up the outer class's buffer
- The PR's fix would catch the stale buffer here ✓

### The test doesn't reproduce the bug

As [noted by @stephan-herrmann](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/736#issuecomment-2613803367), the test passes without the fix. This is because:
- `removeLibrary()` + `addLibrary()` run synchronously in the same thread
- The buffer cleanup completes before the new jar is created
- No concurrent threads create the race condition

### ModularClassFile change is over-scoped

The PR adds a cache lookup to `ModularClassFile.openBuffer()` that wasn't there before. This is unnecessary because `Openable.getBuffer()` already checks the cache before calling `openBuffer()`. The added cache lookup changes behavior without clear benefit.

## Recommended Solution

### Fix 1: `Openable.getBuffer()` (CRITICAL - addresses the root cause)

This is the primary fix. It must be in `Openable.getBuffer()` where the cache lookup happens:

```java
// In org.eclipse.jdt.internal.core.Openable
@Override
public IBuffer getBuffer() throws JavaModelException {
    if (hasBuffer()) {
        IElementInfo info = getElementInfo();
        IBuffer buffer = getBufferManager().getBuffer(this);
        // Validate the cached buffer is still valid (not stale from a deleted/recreated jar).
        // Race condition: Buffer.close() sets contents=null before bufferChanged() calls
        // removeBuffer(), so a closed buffer can briefly remain in the cache.
        if (buffer != null && !(buffer instanceof NullBuffer) && buffer.isClosed()) {
            getBufferManager().removeBuffer(buffer);
            buffer = null;
        }
        if (buffer == null) {
            buffer = openBuffer(null, info);
        }
        if (buffer instanceof NullBuffer) {
            return null;
        }
        return buffer;
    } else {
        return null;
    }
}
```

### Fix 2: `ClassFile.openBuffer()` (for inner classes)

Keep the PR's fix in `ClassFile.openBuffer()` but use `isClosed()` instead of `getCharacters() == null` for clarity:

```java
// In org.eclipse.jdt.internal.core.ClassFile
@Override
protected IBuffer openBuffer(IProgressMonitor pm, IElementInfo info) throws JavaModelException {
    IType outerMostEnclosingType = getOuterMostEnclosingType();
    IBuffer buffer = getBufferManager().getBuffer(outerMostEnclosingType.getClassFile());

    // Validate the cached buffer is still valid (not stale from a deleted/recreated jar)
    if (buffer != null) {
        if (buffer instanceof NullBuffer) {
            return null;
        }
        if (buffer.isClosed()) {
            getBufferManager().removeBuffer(buffer);
            buffer = null;
        }
    }

    if (buffer == null) {
        SourceMapper mapper = getSourceMapper();
        IBinaryType typeInfo = info instanceof IBinaryType ? (IBinaryType) info : null;
        if (mapper != null) {
            buffer = mapSource(mapper, typeInfo, outerMostEnclosingType.getClassFile());
        }
    }
    return buffer;
}
```

### Fix 3: `ModularClassFile.openBuffer()` (minimal change)

Do NOT add a cache lookup. Just keep the original code. The stale buffer check in `Openable.getBuffer()` handles this case:

```java
// In org.eclipse.jdt.internal.core.ModularClassFile - NO CHANGE NEEDED
@Override
protected IBuffer openBuffer(IProgressMonitor pm, IElementInfo info) throws JavaModelException {
    SourceMapper mapper = getSourceMapper();
    if (mapper != null) {
        return mapSource(mapper);
    }
    return null;
}
```

### Why `isClosed()` instead of `getCharacters() == null`

In `Buffer.close()`, both `this.contents = null` and `this.flags |= F_IS_CLOSED` are set in the same `synchronized` block. From another thread, either both are visible or neither is. Using `isClosed()` is:
1. More semantically clear (we're checking for closed/stale buffers, not arbitrary null state)
2. Avoids false positives from freshly created buffers that haven't been filled yet
3. Consistent with the existing `isClosed()` check in `bufferChanged()`

### Test Improvement

The test in the PR doesn't reproduce the race condition. A proper test would need to:
1. Directly manipulate the buffer cache to simulate the stale state
2. Or use thread synchronization to create the race window

Example approach:
```java
public void testStaleClosedBufferEviction() throws CoreException {
    // Setup: create jar and get class file
    // ...
    
    // Get the buffer to populate the cache
    IBuffer buffer = classFile.getBuffer();
    assertNotNull("Buffer should exist", buffer);
    
    // Simulate the race condition: close the buffer but leave it in cache
    // (In the real bug, this happens when Buffer.close() runs but
    // bufferChanged()/removeBuffer() hasn't completed yet)
    buffer.close();
    // At this point, the buffer is closed but may still be in the BufferManager cache
    // due to the race between close() notification and cache removal
    
    // The fix should detect the closed buffer and recreate it
    IBuffer newBuffer = classFile.getBuffer();
    // newBuffer should either be null (no source) or a fresh valid buffer
    if (newBuffer != null) {
        assertNotNull("Buffer contents should not be null", newBuffer.getCharacters());
    }
}
```

## Conclusion

| Aspect | PR #14 Assessment |
|--------|-------------------|
| Root cause understanding | ✅ Correct - stale buffer reuse from LRU cache |
| `ClassFile.openBuffer()` fix | ⚠️ Partially correct - only covers inner class case |
| `ModularClassFile.openBuffer()` fix | ❌ Over-scoped - adds unnecessary cache lookup |
| Missing fix in `Openable.getBuffer()` | ❌ Critical gap - top-level class case not covered |
| Test | ❌ Doesn't reproduce the bug (passes without fix) |
| Overall | The fix direction is right but needs the primary fix in `Openable.getBuffer()` |

The **essential fix** is in `Openable.getBuffer()` where the stale closed buffer must be detected and evicted before it's returned to callers. The `ClassFile.openBuffer()` fix is a good secondary defense for the inner class path. The `ModularClassFile.openBuffer()` change should be reverted.
