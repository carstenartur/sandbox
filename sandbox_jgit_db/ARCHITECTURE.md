# Architecture — sandbox_jgit_db

## Purpose

Eclipse plugin that provides a **DB-backed Java analysis index** running alongside EGit.
Mirrors Git repositories into an embedded HSQLDB database and enables Java-specific
database queries that are impossible with the filesystem alone.

## Design: "Parallel DB Index"

```
Eclipse IDE
├── EGit (unchanged, installed)
│   └── FileRepository (.git on filesystem)
│       ↓ (Workspace events: commit, pull, push, branch)
│
├── sandbox_jgit_db (THIS PLUGIN)
│   ├── Listener: reacts to EGit events
│   │   └── reads FileRepository, indexes into DB
│   │
│   ├── HSQLDB (embedded, in workspace metadata)
│   │   └── Tables: git_commit_index, java_blob_index,
│   │       file_path_history, git_refs, git_objects
│   │
│   └── Eclipse UI
│       ├── View: "Git Database Search" (Commits, Types, Paths)
│       ├── View: "Java Type History" (Who changed class X?)
│       ├── View: "Commit Analytics" (Author stats, time ranges)
│       └── Command: "Index Repository" (manual or automatic)
│
└── sandbox_jgit_db_feature (Feature for installation)
```

## Data Flow

```
EGit commit/pull → IResourceChangeListener → IncrementalIndexer → HSQLDB
                                            → BlobIndexer       → HSQLDB
                                                                    ↓
Eclipse View ← GitDatabaseQueryService ← Hibernate Search ← HSQLDB
```

## Key Components

### Internal Layer (`org.sandbox.jgit.db.internal`)

| Class | Responsibility |
|---|---|
| `DbActivator` | Bundle lifecycle, starts/stops tracker |
| `HsqlDbManager` | HSQLDB instance in workspace metadata |
| `EGitRepositoryTracker` | IResourceChangeListener on .git changes |

### Service Layer (`org.sandbox.jgit.db.service`)

| Class | Responsibility |
|---|---|
| `RepositoryIndexService` | Orchestrates commit + blob indexing |
| `IncrementalIndexer` | RevWalk from last indexed commit to HEAD |

### UI Layer (`org.sandbox.jgit.db.ui`)

| Class | Responsibility |
|---|---|
| `GitSearchView` | Full-text search (commits, types, paths, annotations) |
| `JavaTypeHistoryView` | Type change history across commits |
| `CommitAnalyticsView` | Author statistics and commit analytics |
| `IndexRepositoryHandler` | Manual index command |
| `ReindexAllHandler` | Full reindex command |
| `JGitDbPreferencePage` | Settings (auto-index, max blob size) |

## Why Not Modify EGit

- EGit creates repositories via `RepositoryCache.open()` → always `FileRepository`
- No extension point for repository providers in EGit
- `HibernateRepository` extends `DfsRepository` — different class hierarchy
- **→ EGit cannot be made to replace FileRepositories with HibernateRepositories**

## Dependencies

- **Eclipse Platform**: `core.runtime`, `core.resources`, `core.jobs`, `ui`, `jface`
- **EGit**: `org.eclipse.egit.core` (RepositoryCache API)
- **JGit**: `org.eclipse.jgit` (Repository, RevWalk, etc.)
- **JDT Core**: `org.eclipse.jdt.core` (Java AST analysis)

## Dependency Bundling Strategy (Phase 1b)

| Dependency | Strategy |
|---|---|
| Hibernate ORM 6.6 | Embed in `lib/` |
| Hibernate Search 7.2 | Embed in `lib/` |
| Lucene Core | Embed in `lib/` |
| HSQLDB | Embed in `lib/` |
| JGit | **Not embedded** — re-export from EGit's JGit bundle |
| ECJ | **Not embedded** — re-export from Eclipse Platform |
| JDT Core | **Not embedded** — re-export from Eclipse Platform |

## Relationship to sandbox-jgit-storage-hibernate

The existing `sandbox-jgit-storage-hibernate` module contains the Hibernate-based
storage implementation (entities, services, indexers). This plugin will eventually
consume that module's services once the OSGi bundling is resolved (Phase 1b).

## Phase Plan

1. **Phase 1** (current): Module structure, HSQLDB lifecycle, EGit listener skeleton
2. **Phase 1b**: Bundle Hibernate + HSQLDB JARs, connect to storage-hibernate services
3. **Phase 2**: Full EGit integration with incremental indexing
4. **Phase 3**: Eclipse Views with live data from database
5. **Phase 4**: Java-specific queries (type history, annotation changes, etc.)
6. **Phase 5**: Mining integration, context menu extensions, advanced preferences
