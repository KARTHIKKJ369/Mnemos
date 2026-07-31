# Contributing

## Philosophy

Code should be simple.

Readable beats clever.

Correct beats fast.

Production quality only.

---

## Before Coding

Understand:

- architecture
- existing packages
- public APIs

Do not duplicate logic.

---

## Code Style

Run:

go fmt ./...

go vet ./...

go test ./...

go test -race ./...

before committing.

---

## Commits

Examples:

feat(sync): implement sync diff

fix(upload): prevent duplicate blob race

refactor(storage): simplify blob manager

docs(api): update upload examples

---

## Pull Request Checklist

- [ ] Builds
- [ ] Tests pass
- [ ] Race detector passes
- [ ] Documentation updated
- [ ] No TODOs
- [ ] No dead code
- [ ] API unchanged unless intentional

---

## Performance Rules

No N+1 queries.

Prefer streaming.

Use indexes.

Use prepared statements.

Measure before optimizing.

---

## Security Rules

Never log:

- tokens
- passwords
- secrets

Validate every request.

Use constant-time comparisons.

Always sanitize inputs.

---

## Documentation

Every endpoint must include:

- request
- response
- curl example
- error responses

---

## AI Rules

AI assistants should:

- preserve architecture
- avoid unnecessary refactoring
- explain tradeoffs
- maintain backwards compatibility
- update tests
- update documentation

Never introduce placeholder implementations.

Never silently rewrite major subsystems.