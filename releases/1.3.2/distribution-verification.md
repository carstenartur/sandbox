# Sandbox distribution verification

- Result: **PASS**
- Published feature IUs: **15**
- p2 metadata units: **51**
- p2 artifact files checked: **35**
- Integrity checks found in metadata: **35**
- Product plug-ins: **361**
- Singleton bundles: **192** (no duplicates)
- Product root: `/home/runner/work/sandbox/sandbox/sandbox_product/target/products/sandbox.bundle.producteclipse/linux/gtk/x86_64/client`

The runtime workflow additionally starts the materialized product and provisions every published feature into a fresh p2 destination before publication.

## Runtime smoke tests

- Materialized product started and listed installed roots: **PASS**
- Every published Sandbox feature provisioned into a fresh p2 destination: **PASS**
- Fresh installation started and reported all published roots: **PASS**
- Installed cleanup application imported a Java project and formatted one source file: **PASS**
- Transformed source still compiled with Java 21: **PASS**
