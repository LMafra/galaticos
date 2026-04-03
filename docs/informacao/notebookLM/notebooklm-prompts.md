# NotebookLM Prompts – Design & Database Improvements

Use these prompts in your **NotebookLM** notebook (*Software Engineering: Delivery, Design, and Clean Code*) to get advice grounded in that material. Paste one prompt at a time into the NotebookLM chat.

---

## 1. Design patterns (general)

**Prompt to paste in NotebookLM:**

```
Based on the sources in this notebook (Software Engineering: Delivery, Design, and Clean Code), what are the most important design patterns and architectural practices I should apply to a web API backend? I want to improve:

- Separation of concerns (handlers vs business logic vs data access)
- Validation and error handling
- Testability and dependency injection
- Keeping handlers thin and delegating to services or domain logic

Give concrete, actionable recommendations with short examples where relevant.
```

---

## 2. Database structure (MongoDB / NoSQL)

**Prompt to paste in NotebookLM:**

```
Using the ideas from this notebook about clean code and design: what are best practices for structuring a database layer and schema in a document DB (e.g. MongoDB)? I care about:

- When to embed vs reference documents
- How to keep the data layer testable and separate from HTTP/handlers
- Handling denormalization and cached aggregates (e.g. stats) without duplication bugs
- Naming and consistency (e.g. created-at, ids, references)

Give recommendations that would apply to a sports/roster app with championships, matches, players, and aggregated statistics.
```

---

## 3. Refactoring toward services

**Prompt to paste in NotebookLM:**

```
Our API has HTTP handlers that do validation, call the database, and build responses in one place. What’s the best way to refactor toward a cleaner design? I want:

- A clear “service” or “use case” layer between handlers and the database
- Handlers only parsing the request and returning the response
- Business rules (e.g. “can’t delete championship if it has matches”) in one place and easy to test

What patterns from clean code and delivery would you recommend, and in what order should we refactor?
```

---

## 4. Schema and consistency

**Prompt to paste in NotebookLM:**

```
What principles should I follow for schema design and data consistency in an application that has:

- Source-of-truth collections (e.g. matches with per-game stats)
- Derived/cached data (e.g. player aggregated stats by championship)
- References between entities (championships, players, teams)

How do I keep the schema clear, avoid subtle bugs when updating cached data, and make the system easy to reason about and change later?
```

---

*After you get answers from NotebookLM, you can cross-reference with the project-specific suggestions in the main docs (e.g. `docs/README.md` or this folder) and with `docs/informacao/dominio/mongodb-schema.md` and `docs/informacao/dominio/regras-de-negocio.md`.*
