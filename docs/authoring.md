# Documentation authoring

Conventions for writing and maintaining Galáticos docs for **human readers** and **LLM/RAG consumption** (GEO). See also [llms.txt](../llms.txt) at the repository root.

## Summary

Use clear hierarchy, one term per concept, fenced code blocks, and a short **Summary** at the top of every file under `docs/reference/`. Update [docs/README.md](README.md) and `llms.txt` when adding reference pages.

## Language

- **English** for `docs/reference/` and `docs/backlog/`.
- **`docs/archive/`** may retain legacy Portuguese with an `Archived` note in the title.

## Structure

- One `#` H1 per file (page title).
- Do not skip heading levels: H1 → H2 → H3.
- Prefer short sections; one main idea per section (helps chunking).
- Add a **Summary** block (2–4 sentences) immediately after the H1 on reference pages.

## Terminology

- Use [concepts.md](concepts.md) for canonical definitions.
- Do not rename rule IDs (`RN-MATCH-09`) or code namespaces when translating.
- **Metrics:** define semantics only in [metrics-catalog.md](reference/analytics/metrics-catalog.md); other docs link there instead of redefining KPIs.

## Code and links

- Fence code with language tags: ` ```clojure `, ` ```bash `.
- Use relative links between docs (e.g. `[business rules](reference/domain/business-rules.md)`).
- After moving or renaming a file, run `rg 'docs/reference|docs/backlog'` in links and update [docs/README.md](README.md) and [llms.txt](../llms.txt) if needed.

## Where to put new content

| Type | Location |
|------|----------|
| Stable reference (API, domain, ops) | `docs/reference/<topic>/` |
| Planned work | `docs/backlog/` |
| Historical / superseded | `docs/archive/` |
| Pending implementation work | `docs/backlog/` |

When backlog work is done, move the doc to `reference/` (or delete the backlog entry) and update [docs/README.md](README.md).

## Human readability

Write for a human who needs an answer in under two minutes.

- **Summary:** State what the page covers, who should read it, and prerequisites (tools, access, prior docs).
- **Task-oriented headings:** Use "Run tests locally" instead of internal IDs or plan numbers.
- **Second person:** On procedural pages, write "you" and active voice ("Run `./bin/galaticos test`" not "Tests should be run").
- **Front-load essentials:** Put the most common path first; move edge cases and history lower.
- **Scannable layout:** Short sections, tables for structured data, numbered steps for procedures.
- **Table of contents:** Add `## Contents` with anchor links on pages longer than ~80 lines.
- **Plain language:** Define acronyms on first use; link to [concepts.md](concepts.md) instead of repeating definitions.
- **Finished work:** Delete or archive execution checklists once shipped — keep only stable reference.

## AI index

- Primary agent index: [llms.txt](../llms.txt) (repository root).
- Do not list `docs/archive/` or `docs/perf-output/` in `llms.txt`.
