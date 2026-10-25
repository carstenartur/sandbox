# Application-owned optional embeddings

`EmbeddingEncoder`, `LocalEmbeddingEncoder` and `ApplicationVectorIndex` keep
Sandbox's optional semantic ranking separate from released Core/Search/Java
Analysis and from the copied persistence entities. This slice supplies the
components; runtime corpus/REST wiring follows after its prerequisites merge.

The application context will own one encoder and close it after active generation
readers finish. A vector index does not own or close that shared encoder. Encoder
construction, status inspection, blank input and disabled encoding never load a
model. Prediction and close are serialized. Closing before first use prevents
later initialization; known model-loading I/O, engine and native-linkage failures
mark the optional capability unavailable until a new encoder is created. A
single prediction I/O/translation failure skips that input and permits later
predictions; interrupted failures preserve the thread's interrupt status.
Programming errors and fatal VM errors are not hidden. Status inspection itself never waits
for model loading/prediction and is suitable for capability reporting.

Existing environment configuration is retained:

| Variable | Default/meaning |
| --- | --- |
| `JGIT_EMBEDDING_ENABLED` | Enabled unless `false`, as before; initialization remains lazy |
| `JGIT_EMBEDDING_MODEL_NAME` | `sentence-transformers/all-MiniLM-L6-v2` |
| `JGIT_EMBEDDING_MODEL_DIR` | DJL default cache when unset; otherwise the existing process-wide `DJL_CACHE_DIR` setting |

The existing DJL model locator and ONNX engine selection remain unchanged. A first
enabled semantic request can require a model download or native runtime; ordinary
startup and text queries must not call `embed`. Model initialization/downloads
are not validated by the deterministic tests in this slice.

Input text retains the original class/documentation/method/package formatting
and 2,048-character approximation before tokenizer processing. Truncation avoids
splitting a surrogate pair. Vectors have 384 dimensions. The immutable vector
index copies and normalizes valid vectors, exposes the number rejected for
invalid dimensions/nonfinite values/zero norm, and uses double arithmetic.
It has no SQL schema and can be regenerated from a historical corpus.

Cosine search scans the repository's vectors with a bounded top-K heap; memory
is proportional to retained vectors, and query work is linear in their count.
Ties sort by document identity. Similarity search excludes the seed before
selection and needs no available encoder once its vectors exist. Negative
similarities remain eligible. RRF uses `1/(60 + rank)` with one-based ranks and
semantic-first ties; callers must supply rankings from the same repository with
unique stable blob identities within each ranking. All result lists are immutable.

Integration must build vectors only for an explicit semantic request, keep
generation invalidation aligned with Git/configuration changes, preserve ordinary
full-text fallback when encoding is unavailable, and expose disabled/unavailable
capability without triggering model loading. Archived copied vectors remain
operator-owned legacy data; this adapter neither reads nor deletes them.
