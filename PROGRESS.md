# Forestry AI Reviewer — Project Status

> Single-source-of-truth snapshot for handoff to the next session.
> Read this top-to-bottom + `git log` before resuming work.

**Last updated:** 2026-06-06 evening
**Branch:** main (in sync with origin)
**Last committed:** `d007746` (document type taxonomy + filename classifier + profiles)
**Local uncommitted:** DocumentAnnotationExporter polish (whole-paragraph highlight, plain-Chinese comment body)

---

## 1. What This System Does

Helps a **human reviewer** review forestry/environmental review documents (生物多样性影响评价报告 / 国家储备林可行性研究报告 / 森林经营方案 / 等等). The AI surfaces problems; the human decides.

Two flows live in this codebase:

1. **历史案例摄取** — accumulate training data from past reviewed documents. Three intake modes:
   - **Pair upload**: 草稿 + 终稿 → chunk-aligned diff
   - **Annotated upload**: 单个 docx → extract Word 批注 + track changes (修订)
   - **Reviewer-notes upload**: 自由格式审核意见.docx → parse 一、二、三、 items

2. **新文档审核 + 批注导出** — upload a doc to review → findings table → human approves → export back as native Word .docx with comments by author `liujh`. **The LLM step currently runs on mock data; real LLM is configured but not activated (no API key).**

---

## 2. Architecture

```
forestry-ai-reviewer/
├── backend/                Spring Boot 3.3.6 on JDK 25 runtime, target Java 17
│   └── src/main/java/com/forestry/aireviewer/
│       ├── client/         LLM HTTP clients (Anthropic, OpenAI, mock)
│       ├── config/         CORS, LlmProperties (@ConfigurationProperties)
│       ├── controller/     REST endpoints
│       ├── dto/            BulkImportFinding, AIReviewResponse, etc.
│       ├── model/          JPA entities
│       ├── prompt/         Classpath-loaded review-prompt.txt
│       ├── repository/     Spring Data JPA
│       └── service/        Business logic (extractors, orchestrator, exporter, etc.)
├── frontend/               React 19 + TypeScript + Vite
│   └── src/
│       ├── components/     DocumentUpload, DocumentDetail, FindingRow, HistoricalReviewCases
│       ├── api.ts          fetch() wrappers
│       └── types.ts        TS interfaces
└── PROGRESS.md             ← THIS FILE
```

**Database:** PostgreSQL 17.10 (homebrew, port 5432) with pgvector 0.8.2 extension installed.
**Schema:** managed by Flyway. Migrations live at `backend/src/main/resources/db/migration/V*.sql`.
**JPA:** `ddl-auto: validate` — Hibernate cross-checks entities at boot but Flyway owns schema.

### Tech stack

| Layer | Tech |
|---|---|
| Frontend | React 19, TypeScript, Vite |
| Backend | Spring Boot 3.3.6, Java 17 target, JDK 25 runtime |
| Database | PostgreSQL 17 + pgvector 0.8.2 |
| Text extraction | Apache Tika 2.9.2 (handles .pdf, .docx, .doc via HWPF for text) |
| Word manipulation | Apache POI (XWPF for .docx read/write, comment writing for export) |
| PDF annotations | PDFBox 2.0.31 (transitive from Tika) |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| JSON | Jackson (bundled) |
| Migrations | Flyway |

No LLM SDK dependencies — Spring `RestClient` for everything.

---

## 3. Database State (current snapshot)

| Table | Rows | Notes |
|---|---|---|
| `documents` | 0 | Cleared during PG switch / dev resets |
| `document_chunks` | 0 | Cleared |
| `findings` | 0 | Cleared |
| `review_cases` | **4,914** | 4,600 have `document_type_id` set (93.6% from backfill) |
| `document_types` | **30** | Seeded in V2 migration |

### review_cases breakdown by source_type

| source_type | rows | meaning |
|---|---|---|
| TRACKED_REVISION | 4,678 | Word 修订（`<w:ins>`/`<w:del>`），相邻同作者合成 REPLACE |
| REVIEW_COMMENT | 137 | Word 批注 |
| TEXT_DIFF | 54 | Pair upload chunk-level diff |
| REVIEWER_NOTES | 38 | Free-form opinion document items |
| BOTH | 7 | TEXT_DIFF + matched comment |

### Active document types (11 of 30 have ≥1 row)

| Type | Rows |
|---|---|
| 生物多样性影响评价报告 | 2,503 |
| 初步设计 | 1,067 |
| 生态影响评价报告 | 334 |
| 国家储备林可行性研究报告 | 232 |
| 使用林地可行性报告 | 163 |
| 院审/评审材料 | 109 |
| 不可避让/使用林地论证报告 | 70 |
| 植被恢复方案 | 39 |
| 作业设计 | 34 |
| 审核意见 | 25 |
| 林业建设项目可行性研究报告 | 24 |

314 rows remain unclassified (mostly `chris.docx` test data + 2 ambiguous file names).

### Document types catalog (30 total, grouped into 9 categories)

A. 影响评价 (5): BIODIVERSITY_IMPACT_ASSESSMENT / ECOLOGICAL_IMPACT_ASSESSMENT / NATURE_RESERVE_PROJECT_ASSESSMENT / SPECIES_SPECIAL_TOPIC_ASSESSMENT / ENVIRONMENTAL_IMPACT_ASSESSMENT
B. 可行性 (3): FOREST_LAND_USE_FEASIBILITY / NATIONAL_RESERVE_FOREST_FEASIBILITY / FORESTRY_PROJECT_FEASIBILITY
C. 论证 (2): SITE_SELECTION_ARGUMENTATION / FOREST_LAND_USE_ARGUMENTATION
D. 方案/规划 (8): VEGETATION_RESTORATION_PLAN / COMPENSATION_BALANCE_PLAN / FOREST_MANAGEMENT_PLAN / FOREST_MANAGEMENT_REGIONAL_PLAN / FOREST_LAND_PROTECTION_PLAN / NATURE_RESERVE_MASTER_PLAN / NATIONAL_RESERVE_FOREST_PROGRAM / FIRE_PREVENTION_CONSTRUCTION_PLAN
E. 设计 (2): PRELIMINARY_DESIGN / OPERATION_DESIGN
F. 调查 (2): FORESTRY_SURVEY_ASSESSMENT / FOREST_LAND_STATUS_FORM
G. 验收/总结 (3): FOREST_HARVEST_ACCEPTANCE / PUBLIC_WELFARE_FOREST_INSPECTION / PROJECT_COMPLETION_SUMMARY
H. 审核 (2): REVIEW_OPINION / INSTITUTIONAL_REVIEW_MATERIAL
I. 标准/指南 (3): COMPILATION_GUIDELINE / PROJECT_COMPLETION_GUIDELINE / INDUSTRY_TECHNICAL_STANDARD

---

## 4. API Surface

### Documents (single-doc review pipeline)
- `POST /api/documents/upload` — multipart upload, Tika extracts text, DocumentChunker chunks
- `GET  /api/documents` — list
- `GET  /api/documents/{id}` — single
- `POST /api/documents/{id}/review` — trigger AI review (mock or LLM depending on `app.review.provider`)
- `GET  /api/documents/{id}/findings` — fetch findings
- `POST /api/documents/{id}/findings/bulk-import` — accept JSON list, status PENDING (used to insert my-as-LLM findings)
- `POST /api/documents/{id}/export-annotated?author=liujh` — stream a .docx with CONFIRMED findings as Word comments
- `PATCH /api/findings/{id}/status` — `{ "status": "CONFIRMED" | "IGNORED" | "NEEDS_FOLLOW_UP" | "PENDING" }`

### Review cases (training data ingest)
- `POST /api/review-cases/upload-pair` — `beforeFile + afterFile` → chunk-aligned diff + optional comments
- `POST /api/review-cases/upload-annotated` — single docx → comments + tracked revisions
- `POST /api/review-cases/upload-notes` — single docx/pdf/txt → parse 一、二、三、 items as REVIEWER_NOTES
- `GET  /api/review-cases` — list
- `GET  /api/review-cases/{id}` — single
- `POST /api/review-cases/backfill-types` — runs DocumentTypeClassifier on rows with `document_type_id IS NULL`

### Document types
- `GET  /api/document-types` — list 30 types, includes `profile` jsonb (currently null except BIODIVERSITY_IMPACT_ASSESSMENT)

### Other
- `GET  /api/health`

---

## 5. UI Pages

| Page | Purpose |
|---|---|
| Documents | List + upload + click into Detail |
| Document Detail | Run AI review, manage finding statuses, **export annotated .docx (author = liujh)** |
| Historical Review Cases | 3 tabs: Upload pair / Upload annotated / Upload reviewer notes |

Open at http://localhost:5173/ (Vite dev server).

---

## 6. How to Run

### One-time setup

```bash
# Install Postgres 17 + pgvector
brew install postgresql@17 pgvector
brew services start postgresql@17

# Create DB + user
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
createuser -s forestry
psql -d postgres -c "ALTER USER forestry WITH PASSWORD 'forestry';"
createdb -O forestry forestry_ai_reviewer
psql -d forestry_ai_reviewer -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### Daily run

```bash
# Backend (Flyway migrates automatically on boot)
cd backend && mvn spring-boot:run
# port 8081, logs at /tmp/forestry-backend.log when run with redirect

# Frontend
cd frontend && npm install && npm run dev
# http://localhost:5173

# Tests
cd backend && mvn test                        # 124+ tests, all green
cd frontend && npm run build                  # tsc + vite, clean
```

### To activate real LLM (when an API key arrives)

```bash
export ANTHROPIC_API_KEY=sk-ant-...
# or OPENAI_API_KEY=sk-...
# Edit backend/src/main/resources/application.yml:
#   app.review.provider: llm    (was: mock)
#   app.llm.provider: anthropic  (or openai)
#   app.llm.model: claude-sonnet-4-6
```

Startup fails fast if `provider=llm` and the matching env var is missing — that's intentional.

### Direct DB access (handy)

```bash
PGPASSWORD=forestry psql -h localhost -U forestry -d forestry_ai_reviewer
```

---

## 7. Workflows in Practice

### Ingest historical training data
1. Open Historical Review Cases page
2. Pick the right tab:
   - **Both versions exist** → Upload pair (draft + reviewed)
   - **One annotated docx with comments/track changes** → Upload annotated
   - **Free-form opinion document (审核意见.docx)** → Upload reviewer notes
3. Submit → rows go into `review_cases` table
4. Duplicate filenames are auto-skipped (file-name-level dedup)

### Review a new document
1. Documents → Upload (PDF or DOCX)
2. Click into Document Detail
3. Either:
   - Click "Start AI Review" (mock data while LLM is off), OR
   - This session does it manually + `POST /findings/bulk-import` with structured findings
4. Reviewer marks each finding Confirmed / Ignored / Needs Follow-up
5. Click "Export annotated .docx (N CONFIRMED)" — downloads .docx with comments by liujh

---

## 8. Recent Commits (newest first)

| SHA | Title |
|---|---|
| `5b7443b` | bulk-import findings + export annotated .docx |
| `bc44dd4` | ingest free-form reviewer notes documents |
| `7db7511` | filename-level dedup on review-case upload |
| `6af1b46` | switch dev datasource to Postgres + Flyway |
| `55cc4c8` | extract tracked revisions + surface annotated upload UI |
| `f070dde` | ingest single annotated documents as review cases |
| `6ff3f50` | add historical review case ingestion |
| `744e087` | implement forestry AI reviewer MVP |

### Uncommitted local work
- V2 Flyway migration (`db/migration/V2__document_types.sql`)
- DocumentType entity / repo / controller
- `documentTypeId` columns on Document and ReviewCase entities
- DocumentTypeClassifier + 29 unit tests
- ReviewCaseBackfillService + `POST /api/review-cases/backfill-types`
- BIODIVERSITY_IMPACT_ASSESSMENT profile (written directly into DB)

---

## 9. In-Progress Task: per-type profiles

**Goal**: for each `document_types` row, write a `profile` JSONB summarizing what that class of document looks like — required sections, common reviewer corrections, must-have evidence, typo patterns, review checklist, format conventions.

**Method** (proven on BIODIVERSITY_IMPACT_ASSESSMENT):
1. Query all `REVIEW_COMMENT` rows for that type — actual reviewer words
2. Aggregate most-inserted phrases in `TRACKED_REVISION` (what reviewers add → what's commonly missing)
3. Aggregate most-deleted phrases (what reviewers strip out → bloat / overclaim / boilerplate)
4. Synthesize into JSON profile of the shape used for BIODIVERSITY (see `/api/document-types` → `profile` field)
5. `UPDATE document_types SET profile = '...'::jsonb WHERE code = '...'`

**Status**:
- ✅ BIODIVERSITY_IMPACT_ASSESSMENT (2,503 rows analyzed)
- ⏳ Remaining 10 active types:
  - PRELIMINARY_DESIGN (1,067)
  - ECOLOGICAL_IMPACT_ASSESSMENT (334)
  - NATIONAL_RESERVE_FOREST_FEASIBILITY (232)
  - FOREST_LAND_USE_FEASIBILITY (163)
  - INSTITUTIONAL_REVIEW_MATERIAL (109)
  - FOREST_LAND_USE_ARGUMENTATION (70)
  - VEGETATION_RESTORATION_PLAN (39)
  - OPERATION_DESIGN (34)
  - REVIEW_OPINION (25)
  - FORESTRY_PROJECT_FEASIBILITY (24)

**Schema for profile JSON** (see BIODIVERSITY for the working example):
```json
{
  "summary": "...",
  "applicable_standards": ["LY/T XXXX-XXXX", ...],
  "required_sections": ["1. ...", "2. ...", ...],
  "common_issues": [{"pattern": "...", "evidence": "..."}, ...],
  "must_have_evidence": [...],
  "common_typos": [{"wrong": "...", "correct": "..."}],
  "review_checklist": ["[ ] ..."],
  "format_conventions": [...]
}
```

---

## 10. Backlog (prioritized)

### Immediate
1. **Finish profile generation** for the other 10 types
2. **Frontend: type dropdown on uploads** — Document upload + 3 review-case tabs each take an optional documentTypeCode; backend wires `documentTypeId` from it
3. **/document-types page** — list 11 active types with a readable rendering of each profile
4. **.doc support** —
   - Frontend `accept=` filters: add `.doc, application/msword` everywhere
   - New `DocCommentExtractor` (HWPF) for .doc Word comments — no author info (binary format limit)
   - Skip .doc track-changes (HWPF revisions are messy; v1 limitation)
   - Annotation export: detect .doc source → return 400 with "convert to .docx first"

### Short-term
5. Global exception handler so service errors return clean 4xx/5xx JSON instead of stack traces
6. Sanitize Tika output (control chars sometimes break Jackson on LLM responses)
7. Retry policy for transient LLM failures (429/503)
8. Parallel chunk processing (`CompletableFuture` + concurrency cap) — currently serial

### Once an API key arrives
9. Flip `app.review.provider: llm`, env var the matching key, smoke-test end-to-end real review
10. **RAG (the big one)** — pgvector is already installed and schema is ready (`CREATE EXTENSION vector` is in V1 migration). Plan when activated:
    - New `regulations` + `regulation_chunks` tables with `embedding vector(1536)` column
    - `EmbeddingClient` interface, OpenAI text-embedding-3-small to start (1536d)
    - Upload regulations page (国有林场森林经营方案编制指南.pdf etc.)
    - Retrieval injects top-K regulation chunks + same-type review_cases into review prompt
    - The 30 type profiles in this milestone become the first piece of structured RAG context

### Later
11. Reviewer free-text comments on findings
12. Filter / search findings in UI
13. Export findings to PDF / Excel
14. Docker Compose for the whole stack
15. Multi-document comparison
16. User authentication
17. CI/CD

---

## 11. Decisions & Conventions

1. **Filename-level dedup** for review-case uploads — coarse but enough to stop the common "I dragged it in twice" case. Renaming bypasses the check.
2. **Comments author defaults to `liujh`** when exporting annotated .docx — that's the in-house reviewer's name.
3. **Profile generation is the synthesis of corpus signals** (not free invention). Always include `evidence` strings citing what reviewers actually said/did.
4. **document_type_id is a plain UUID column on entities** (no @ManyToOne JoinColumn) — keeps the codebase consistent and avoids N+1 fetches. Frontend caches the 30 types once.
5. **Reusing `sourceDraftFileName` for "related document"** on REVIEWER_NOTES rows — the notes file isn't a draft per se, but the column is the closest existing field; avoids a schema migration.
6. **No LLM SDK dependencies** — Spring `RestClient` is enough. Swapping models or providers stays one yaml change.
7. **Tests use H2 in-memory** (separate `src/test/resources/application.yml`) so they stay fast and don't touch the dev Postgres.
8. **`'\0'` not `' '` for NUL char literals in Java sources** — the Unicode escape is preprocessed by the Java lexer (anywhere in source, even strings/comments) and Write tools strip it. Use the octal escape.

---

## 12. Gotchas

1. **Sometimes the wrong tab on Historical Review Cases** ingests a full document body as "REVIEWER_NOTES" (each paragraph becomes one row). User did this once with 3 PDFs producing 591 noise rows. **Always confirm the file you upload matches the tab semantics.**
2. **H2 ENUM column type bites adding enum values** — that's why `source_type` is now `VARCHAR(32)` instead of native ENUM. Don't reintroduce native ENUM columns.
3. **Flyway 9 warns "PostgreSQL 17 newer than supported"** — only a warning, migrations still work. Upgrade Flyway later if it ever stops.
4. **Backend restart pattern** when JPA detects schema drift or you change entities:
   ```bash
   kill $(lsof -ti TCP:8081 -sTCP:LISTEN); sleep 2
   cd backend && mvn spring-boot:run > /tmp/forestry-backend.log 2>&1 &
   ```
5. **Word comment anchor — now uses XmlCursor for whole-paragraph highlight.** Previously commentRangeStart was at the paragraph tail (zero-width range → reviewers literally couldn't see annotations even though they were technically present). Current behavior: append start at end, then move it to right after `<w:pPr>` (or to position 0 if no pPr) using `XmlCursor.moveXml`. Word now visibly highlights the full anchor paragraph. Precise quote-substring highlight is still future work — requires splitting the run that contains the quote.
6. **HWPF (legacy .doc) write is incomplete** — POI can read .doc but doesn't reliably write it back. Annotated export for .doc sources is therefore intentionally unsupported.

---

## 13. Where to start next session

**If picking up profile generation:**
- Re-read sections 9 + 11 above
- Run `/api/document-types` to see which profiles exist (look at the `profile` field)
- Pick next type from the ⏳ list in §9, repeat the method (query top-N revisions, summarize, write SQL UPDATE)

**If switching to .doc support:**
- §10 item 4 above lists everything
- New files to touch:
  - `frontend/src/components/DocumentUpload.tsx` (accept filter)
  - `frontend/src/components/HistoricalReviewCases.tsx` (accept filters x2)
  - `backend/src/main/java/.../service/DocCommentExtractor.java` (new, HWPF)
  - `backend/src/main/java/.../controller/ReviewController.java` (export rejects .doc with 400)

**If activating LLM + starting RAG:**
- §10 items 9 + 10
- `application.yml` flip + key
- Then design `regulations` schema as V3 migration

**Live state right now:**
- Postgres running, backend on 8081, frontend on 5173 (if dev servers are still up — check `lsof -ti :8081 :5173`)
- DB has 4,914 review_cases, 30 document_types (1 with profile)
