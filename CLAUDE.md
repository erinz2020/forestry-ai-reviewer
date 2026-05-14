# Forestry AI Reviewer

AI-assisted document review system for forestry and environmental reports.

## Project Overview

This system helps human reviewers review environmental impact reports, biodiversity reports, forestry project feasibility reports, and project approval documents. AI identifies issues, risks, contradictions, missing evidence, unclear statements, and regulatory concerns. AI never makes final approval decisions.

Every AI finding must be evidence-based and cite its source when possible.

## Architecture

```
forestry-ai-reviewer/
├── frontend/          # React + TypeScript (Vite)
├── backend/           # Java Spring Boot
├── CLAUDE.md
└── README.md
```

## Tech Stack

- **Frontend:** React, TypeScript, Vite
- **Backend:** Java 17+, Spring Boot 3.x
- **Database:** PostgreSQL 15+
- **Later:** pgvector for RAG-based knowledge retrieval

## Development Rules

### Scope

- Work in small, testable tasks
- Do not over-engineer
- Do not implement multi-agent systems or web search in v1
- Start with a mock AI client, replace with real API later

### Code Style

- Follow existing patterns in each module
- Keep code clean and production-oriented
- No speculative abstractions

### Commits

After every meaningful change:
- Explain which files were changed and why
- Describe how to test the change

## MVP Scope (v1)

1. Upload one document (PDF or DOCX)
2. Extract text from the document
3. Run AI review using a simple prompt
4. Return structured findings (issue type, severity, location, evidence, suggestion)
5. Display findings in React UI
6. Human reviewer can mark each finding as: Confirmed / Ignored / Needs Follow-up

### Out of Scope for v1

- Multi-agent orchestration
- Web search for regulations
- RAG / pgvector
- Multi-document comparison
- User authentication
- Batch processing

## Finding Structure

```json
{
  "id": "uuid",
  "documentId": "uuid",
  "type": "CONTRADICTION | MISSING_EVIDENCE | UNCLEAR_STATEMENT | REGULATORY_CONCERN | RISK",
  "severity": "HIGH | MEDIUM | LOW",
  "location": "Section 3.2, paragraph 4",
  "quote": "Original text from the document",
  "description": "What the issue is",
  "suggestion": "How to address it",
  "evidence": "Why this is flagged, with source citation",
  "status": "PENDING | CONFIRMED | IGNORED | NEEDS_FOLLOW_UP",
  "createdAt": "ISO-8601"
}
```

## Knowledge Sources (for later phases)

1. Historical draft + reviewed document pairs
2. Human reviewer checklists and guidelines
3. Uploaded regulations and government guidance documents
4. Optional web search for current public regulations

## Common Commands

```bash
# Backend
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw test

# Frontend
cd frontend && npm install
cd frontend && npm run dev
cd frontend && npm run build
cd frontend && npm run test
```
