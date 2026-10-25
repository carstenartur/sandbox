# Application-owned non-Java search facets

`ApplicationFileFormats` keeps Sandbox's existing file conventions independent of
the copied Hibernate backend. Java files must use released Java Analysis instead.
The result is an immutable value containing file type, FQN, type and field facets,
plus a source snippet. Repository history, blob limits, binary detection, index
ownership and REST pagination belong to the calling history adapter.

Filename selection precedes extension selection and uses locale-independent case
folding. The conventions deliberately retain their existing limitations:

| Format | FQN facet | Type/field facets |
| --- | --- | --- |
| `pom.xml` | distinct group IDs followed by artifact IDs, not combined coordinates | artifact IDs / modules |
| `MANIFEST.MF`, `.mf` | symbolic name, exports, imports | required bundles then bundle name |
| `plugin.xml`, `fragment.xml` | extension point attribute values | class attribute values |
| XML/XSD/EXSD/XSL | double-quoted namespace values | opening names followed by whitespace |
| Properties | Java-like names from decoded values | property keys as fields |
| Other text | Java-like names, including duplicates | none |

XML conventions are text extraction rules: they do not load documents, resolve
entities or fetch dependencies. Manifest list splitting retains the original
comma/directive behavior. Properties preserve Java `Properties` decoding and its
unspecified key enumeration order; malformed Unicode escapes remain errors.
An invalid manifest retains its source and has absent header facets. The generic
fallback's historical file type calculation (last dot anywhere in the path) is
preserved. Absent versus empty metadata retains the old format conventions.

Source snippets remain limited to 65,535 UTF-16 units, with one deliberate
correction: truncation does not split a surrogate pair. Generic FQN snippets use
the same bound. Other facet fields retain their historical full values.

The runtime corpus/search adapter uses these extracted facets and preserves
plain-text source for malformed properties files that cannot produce format facets.
