# Mathematics cleanup source adapter

## Computations across source helper calls

The runtime source adapter can expand statically resolved private static helpers in the same declaring type. Supported helper bodies contain integral parameters/locals (`byte`, `short`, `char`, `int`, `long`), straight-line initialized local declarations and assignments, and a final value return. Nested and overloaded calls use JDT bindings and isolated value frames. No helper name, class name, formula or requested mathematical target selects applicability: the existing Regelsuche SDK searches the resulting original typed graph.

Every argument is translated before the callee body, including unused arguments. Dead callee operations remain in the original evaluation trace. Parameter conversions, compound-assignment narrowing and return conversions use the same decoder as ordinary source statements. Helper definitions and their comments remain unchanged when only the caller is selected. Purely unfolding a method is not itself a measured performance improvement.

State/field access, synchronization, native or dynamic dispatch, other declaring classes, recursive cycles, unsupported control flow and non-integral helper expressions are rejected. Source expansion is bounded to 16 nested frames, 64 statements per helper and 256 expanded statements per source region, in addition to the existing expression-depth bound. These are Java translation limits, not a catalog of mathematical problems. Deliberately authored computed constants inside helpers are protected by the same source policy; calls cannot reopen constant-folding-only demos.

The original Bouncy Castle constructor's general loop/method/object contracts are not inferred by this helper adapter. Its removed dedicated recognizer remains removed. Floating-point and BigInteger helper bodies, arbitrary loops and external library contracts require additional general semantic support.
