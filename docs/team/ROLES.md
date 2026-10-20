# Team Roles

Single capable model available in this environment (`opencode/beam-free`).
Per master prompt section 3(c), when only one capable model exists we run
sequentially instead of in parallel. Roles below are all executed by the same
agent (LEAD), one at a time, never concurrently editing the same files.

| Role | Model | Notes |
|------|-------|-------|
| ARCHITECT/INTEGRATOR | opencode/beam-free | recon docs, contracts, merge order, final review |
| IMPLEMENTER-A | opencode/beam-free | sequential |
| IMPLEMENTER-B | opencode/beam-free | sequential |
| TEST/CI ENGINEER | opencode/beam-free | tests, lint, workflow, CI triage |
| REVIEWER | opencode/beam-free | self-review via /ponytail-review (limitation: same model as author) |

Capability probe: only one model exposed by the environment, so no comparative
scoring was possible. All tasks run sequentially on the LEAD session.