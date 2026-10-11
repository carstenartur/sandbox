# Pinned Regelsuche optimization SDK

Source revision: `6a29154e217ea8748257cfda0340eb463ff157b2`.
Standalone JAR SHA-256: `d1efa15f487872ca46fab9b4d8640495f8df19ea9e354ee52d08f885e59afea7`.

The repository-owned SDK release profile and distribution contract passed two
clean builds with 2,378 tests per build, including 129 SDK tests, 333 mathematics
algorithm tests and 21 modular-domain tests. Two freshly compiled consumers
verified optimization, selected-path replay and checked overflow. All ten
distribution files matched byte for byte. The exact qualification and
reproducibility receipts are in qualification.json and reproducibility.json.

This revision adds bounded same-modulus inner-reduction proposals through
addition, subtraction and multiplication, with independent ordered residue
proof and retained canonical outputs. Ordinary modular arithmetic now uses the
same static operation-work estimates through both public optimizer entry points.
Selected search-path explanations, modular transport and proof composition,
bounded literal-power candidates and generator revision binding remain available.

This is a source-bound development distribution, not a public Maven release.
Generated Java needs no SDK runtime. Existing license notices remain applicable.
