# Frozen fresh synthetic heldout selection
Selected 2026-10-24 for sandbox issue1664/P12-P13. Selection time and SHA-256 values are recorded in selection-receipt.txt before optimizer execution.
Repository inspected: 16cec8649bb601b5990611325f4ad2727a905af2.
The selector read repository policies, P12/P13, QA harness and test sources, but no optimizer rule implementation.
These are independently authored synthetic Java computations. They are not new upstream code or evidence of unseen real-corpus transfer.
The three formula families and all nine variants are fixed before any optimizer execution. Negative/no-improvement results must be retained. No tuning or reselection is authorized.
Every Calculation.java is UTF-8 with LF and one final newline. Original/renamed/reduced controls are separate complete compilation units.

## Stencil family: INT
The original computes the odd and even centered differences of a square:
R=(c+h)^2; L=(c-h)^2; O=R-L; E=R+L-2c^2.
Hand expansion gives O=4ch and E=2h^2 in any commutative ring.
For Java int, interpret the ring as Z/(2^32), so the BigInteger oracle is 4*c*h and 2*h*h, each converted with intValue().
This proof includes overflowing original intermediates; inputs cover every int in the algebraic argument.
Original and renamed source are the same computation with only identifiers changed.
The reduced source is an independently hand-reduced stability control; an optimizer change is a reported observation, not permission to modify the fixture.

## Bernstein family: BIG_INTEGER
The original computes quadratic Bernstein value and derivative, allowing every long for a,b,c,t:
V=a*(1-t)^2+2*b*(1-t)*t+c*t^2.
D=2*((b-a)*(1-t)+(c-b)*t).
Hand collection of powers gives q=a-2b+c, l=2b-2a, V=a+l*t+q*t^2 and D=l+2*q*t.
Independent JUnit oracle should evaluate coefficients with BigInteger and pow(2), not copy the original chained expression.
long inputs are first converted by BigInteger.valueOf, so no primitive products occur before arbitrary-precision arithmetic.
All inputs/temporaries are exact base-class factory results, non-null and bounded to less than 200 magnitude bits (far below BigInteger's supported range).
String results expose complete numerical values without an object-identity observation.
Original and renamed source differ only in identifiers. Reduced source uses Horner form and one shared q*t.
No cryptographic or performance interpretation is intended.

## Full-adder family: LONG
For each bit, OR = XOR + AND as an unsigned sum with no overlapping set bits; x+y = (x XOR y)+2*(x AND y) modulo 2^64.
Therefore the original yields (x+y, x XOR y) as 64-bit two's-complement values.
The independent oracle should compute the first output using BigInteger.valueOf(x).add(BigInteger.valueOf(y)).longValue().
For the second output, use per-bit parity over bit positions 0..63 in the oracle rather than the source OR-minus-AND expression.
Reduced source is the hand-reduced stability control.

## DOUBLE negative control
This is the unchanged stencil structure with double types. The integer ring proof does not apply to its rounded evaluation.
At c=2^27 and h=1, the original E is +0.0 while the polynomial substitution 2*h*h is 2.0, despite all intermediates being finite.
At c=Double.MAX_VALUE and h=1, original O is NaN while 4*c*h is positive infinity.
The test must preserve the original IEEE result (including signed zero and infinities; canonical NaN comparison is sufficient).
A deliberate polynomial replacement must fail the independent execution oracle at the fixed witness.
Do not infer that all possible safe transformations are forbidden; report actual unchanged/changed/rejected status and verify any changed code.

## Execution policy frozen before first run
Production optimizer and SDK remain unchanged.
Use PRESERVE_JAVA and LOWER_ESTIMATED_RUNTIME, target Java 17, work budget 1000000, max states 20000, mathematicalOptIn=false.
Select exactly INT, BIG_INTEGER, LONG or DOUBLE for each corresponding fixture.
Compile originals/generated units with javac --release 17 and no optimizer dependency at fixture runtime.
Fixed samples: stencil/adder edges {0,1,-1,MIN_VALUE,MAX_VALUE,2^30 (INT) or 2^62 (LONG), alternating-bit words}, Cartesian pairs, then 256 seed-1664 pseudorandom pairs.
Bernstein edges {0,1,-1,MIN_VALUE,MAX_VALUE} Cartesian quadruples, then 256 seed-1664 pseudorandom quadruples.
DOUBLE edges include signed zero, ±1, ±2^27, MIN_VALUE, MIN_NORMAL, MAX_VALUE, ±Infinity, NaN, plus the explicit finite witness.
For each fixture capture input SHA, diagnostics, accepted replacements/evidence, generated SHA, compile and oracle outcomes, and exact Undo.
Check a second unchanged-optimizer pass on emitted source for stability, retaining any non-stable observation.
Finite execution is corroboration, not the symbolic proof. Production evidence remains required for accepted replacements.
No timing, allocation or performance claim is authorized by this selection.
