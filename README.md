# Forestry AI Reviewer

AI-assisted document review system for forestry and environmental reports. A human reviewer is always in the loop; the AI only surfaces possible issues.

For project intent, scope, and architecture see [CLAUDE.md](CLAUDE.md).
For the running progress log and current feature state see [PROGRESS.md](PROGRESS.md).

---

## Quick start

```bash
# Backend (port 8081) — mock AI by default, no external calls
cd backend
mvn spring-boot:run

# Frontend (port 5173) — separate terminal
cd frontend
npm install     # first time only
npm run dev
```

Open <http://localhost:5173>.

### Run backend tests

```bash
cd backend && mvn test
```

---

## Local persistence (H2 file mode)

The backend uses **H2 in file mode** for local development so that uploaded documents, extracted chunks, AI findings, and (later) review cases all survive a backend restart.

### Where the database file lives

```
backend/data/forestry-reviewer.mv.db
```

Configured in [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml):

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/forestry-reviewer
```

The path is relative to the backend working directory, so the actual file is `backend/data/forestry-reviewer.mv.db` when you run `mvn spring-boot:run` from inside `backend/`. The `backend/data/` directory is gitignored.

H2 may also create a temporary lock file (`forestry-reviewer.mv.db.lock`) while the backend is running. That file is removed automatically on a clean shutdown.

### How to reset local data

Stop the backend, then delete the data directory:

```bash
# from the repo root
rm -rf backend/data

# or, if you also want a clean uploads directory:
rm -rf backend/data backend/uploads
```

The next backend startup will recreate an empty database (`ddl-auto: update` lets Hibernate regenerate the schema).

### How to verify data persists after restart

```bash
# 1. Start the backend
cd backend && mvn spring-boot:run

# 2. In another terminal, upload a document
curl -X POST http://localhost:8081/api/documents/upload -F "file=@some.pdf"

# 3. List documents — note the id
curl http://localhost:8081/api/documents

# 4. Stop the backend (Ctrl+C in the spring-boot:run terminal)

# 5. Confirm the database file exists
ls -la backend/data/
# expected: forestry-reviewer.mv.db (a few hundred KB or more)

# 6. Restart the backend
cd backend && mvn spring-boot:run

# 7. List documents again — the document from step 3 must still be there
curl http://localhost:8081/api/documents
```

### Inspect the database in a browser

The H2 console is enabled in dev. While the backend is running, open:

<http://localhost:8081/h2-console>

Login fields:
- **JDBC URL:** `jdbc:h2:file:./data/forestry-reviewer`
- **User Name:** `sa`
- **Password:** *(leave blank)*

Tables of interest: `DOCUMENTS`, `DOCUMENT_CHUNKS`, `FINDINGS`.

### Tests use a separate in-memory database

Tests are configured to use H2 in-memory (`jdbc:h2:mem:testdb`) via [`backend/src/test/resources/application.yml`](backend/src/test/resources/application.yml), so they never touch `backend/data/`. The dev file and the test database are fully isolated — running tests will never wipe your local data, and uploading documents during dev will never affect tests.

### Switching to PostgreSQL later

PostgreSQL settings are present (but commented out) in `application.yml`. The intent is to swap the datasource block and enable Flyway migrations when the project moves beyond local dev. Until then, H2 file mode is the single source of truth for local persistence.

---

## AI review provider

Default is `mock` — no external API calls, deterministic findings. To activate a real LLM, see the LLM activation section in [PROGRESS.md](PROGRESS.md#current-state-of-the-llm-activation).
