# Transporter Assignment API

A Spring Boot REST service that assigns every shipping lane to one transporter, using at most `maxTransporters`
transporters, at the lowest total cost it can prove. The minimum is guaranteed whenever the exact search completes
within its configurable budget; otherwise the service returns a clearly labelled heuristic result (see
[Solver](#solver)).

- **Live API:** https://transporter-assignment-api-8bha.onrender.com (the root redirects to Swagger UI; the free instance sleeps when idle, so the first request can take up to a minute)
- **Swagger UI:** https://transporter-assignment-api-8bha.onrender.com/swagger-ui.html
- **OpenAPI JSON:** https://transporter-assignment-api-8bha.onrender.com/v3/api-docs

Java 17 · Spring Boot 3.2 · Maven · springdoc-openapi 2.5 · JUnit 5 + MockMvc. In-memory storage, no database.

## Contents

- [How to run](#how-to-run)
- [API](#api)
- [Errors](#errors)
- [Assumptions](#assumptions)
- [Solver](#solver)
- [Design notes](#design-notes)
- [Tests](#tests)
- [Deployment](#deployment)
- [Notes on the sample data](#notes-on-the-sample-data)
- [Demo script for the Loom](#demo-script-for-the-loom)

## How to run

Requirements: Java 17+ and Maven 3.9+, or only Docker.

```bash
mvn spring-boot:run          # starts on http://localhost:8080
mvn test                     # runs all unit and API tests
mvn -B package               # builds target/transporter-assignment-1.0.0.jar
java -jar target/transporter-assignment-1.0.0.jar
```

With Docker:

```bash
docker build -t transporter-assignment-api .
docker run --rm -p 8080:8080 transporter-assignment-api
```

Without a local Maven, the tests also run in the official Maven image:

```bash
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-17 mvn -B test
```

Then open http://localhost:8080/swagger-ui.html (the bare http://localhost:8080/ redirects there). Both endpoints have ready-made examples ("Test Case 1", "Test Case 2")
under **Try it out**.

The only environment variable is `PORT` (default `8080`). Solver budgets can be changed as Spring properties, for
example `--solver.max-search-nodes=20000000`:

| Property | Default | Meaning |
|---|---|---|
| `solver.max-search-nodes` | `5000000` | Most branch-and-bound nodes the exact search may evaluate |
| `solver.max-time-ms` | `10000` | Most time the exact search may take |
| `solver.heuristic-time-ms` | `3000` | Time budget for the local search, used only after the exact search stops at a budget |

`solver.max-combinations` from earlier versions is gone: branch-and-bound is limited by work done, not by C(N, k).

## API

Request bodies for both test cases are in [`samples/`](samples).

### `POST /api/v1/transporters/input`

Saves lanes and quotes. Each call replaces the previous input; a rejected call leaves the previous input in place.

```bash
curl -s -X POST localhost:8080/api/v1/transporters/input \
     -H 'Content-Type: application/json' -d @samples/case1-input.json
```

```json
{
  "lanes": [ {"id": 1, "origin": "Mumbai", "destination": "Delhi"}, ... ],
  "transporters": [
    {"id": 1, "name": "Transporter T1",
     "laneQuotes": [ {"laneId": 1, "quote": 20835}, {"laneId": 2, "quote": 10512}, ... ]},
    ...
  ]
}
```

Response `200`:

```json
{ "status": "success", "message": "Input data saved successfully." }
```

### `POST /api/v1/transporters/assignment`

```bash
curl -s -X POST localhost:8080/api/v1/transporters/assignment \
     -H 'Content-Type: application/json' -d '{"maxTransporters": 3}'
```

Response `200` for Test Case 1:

```json
{
  "status": "success",
  "totalCost": 60139,
  "assignments": [
    {"laneId": 1, "transporterId": 5},
    {"laneId": 2, "transporterId": 1},
    {"laneId": 3, "transporterId": 4},
    {"laneId": 4, "transporterId": 4},
    {"laneId": 5, "transporterId": 4}
  ],
  "selectedTransporters": [1, 4, 5],
  "solver": "EXACT"
}
```

- `assignments` has one entry per lane, ordered by `laneId`.
- `selectedTransporters` lists the transporters that received at least one lane, ordered by id.
- `solver` and `note` are small additions to the specified response, so the result is honest about how it was
  produced. `solver` is `EXACT` (the exact search completed: proven minimum cost) or `HEURISTIC` (the exact search
  ran out of budget and local search answered: the cost may be higher than the minimum; see [Solver](#solver)).
  `note` appears only when there is something to say, for example when `maxTransporters` was clamped or the
  heuristic answered (it then reports the gap to a proven lower bound).

## Errors

Every error has the same body:

```json
{
  "status": "error",
  "code": 400,
  "error": "Bad Request",
  "message": "Input data is inconsistent.",
  "details": ["Duplicate lane id 1"]
}
```

`details` is present only when there is more than one thing to report or a field to point at.

| Case | HTTP |
|---|---|
| Malformed JSON, wrong value types (for example a fractional quote), missing required fields, empty `lanes` or `transporters` | 400 |
| `quote` not greater than 0, or above 10^12 (keeps totals far from overflow) | 400 |
| Duplicate lane id; duplicate transporter id; quote for a lane id not in `lanes`; the same lane quoted twice by one transporter | 400 |
| `maxTransporters` missing, 0 or negative | 400 |
| A lane that no transporter quoted (checked at `/input`; the message lists the lane ids) | 422 |
| No selection of at most `maxTransporters` transporters covers every lane, **proven** by a completed exact search (the message suggests raising `maxTransporters`) | 422 |
| **Search inconclusive:** the exact search ran out of budget and local search found no cover; one may still exist (the message starts with "Search inconclusive" and names the budgets to raise; never reported as infeasible) | 422 |
| `/assignment` called before any `/input` | 409 |
| `maxTransporters` greater than the number of transporters | 200, clamped to N, with a `note` |
| Unknown path / wrong method / wrong content type | 404 / 405 / 415 |
| Any other exception | 500 with a generic message; details go to the server log only |

## Assumptions

The brief lists three objectives: minimize total cost, maximize transporter usage up to the given limit, and cover
every lane. It does not say which wins when the first two conflict. **The priority below is our documented
interpretation of that list, not something the brief states.**

1. Every lane must be covered.
2. A lane may only be assigned to a transporter that quoted it.
3. **Cost first:** the minimum total cost over all choices of at most k transporters.
4. **Then usage:** among all minimum-cost assignments, use as many transporters as possible, up to k. A
   transporter is never added if it would raise the cost.
5. **Then ids:** remaining ties are broken deterministically in favour of lower transporter ids, so equal inputs
   always give equal outputs.

In practice, each lane pays the cheapest quote within the chosen set. When several transporters tie for a lane's
cheapest quote, lanes are spread across them by a maximum matching (see [Solver](#solver)), so equal quotes never
pile onto one transporter while another could take a lane at the same price.

## Solver

The solver strategies (`AssignmentSolver`) run as a pipeline (`SolverPipeline`): the exact branch-and-bound search
first, within its budget; only if a budget runs out, the coverage-first local search.

### What is guaranteed, and what is not

Choosing at most k transporters to minimize the sum of each lane's cheapest quote is a k-median-style selection
problem and is **NP-hard**. No known algorithm solves every large input exactly in reasonable time, and no fast
heuristic is guaranteed to find a covering selection even when one exists. Branch-and-bound extends the exact
reach well beyond plain enumeration on typical inputs, but it is still bounded by `solver.max-search-nodes` and
`solver.max-time-ms`. The service therefore makes a deliberate, documented tradeoff:

| Outcome | `solver` | What the result means |
|---|---|---|
| Exact search completed within budget | `EXACT` | The proven minimum cost, with usage maximized among minimum-cost answers. A 422 from a completed search proves no selection of at most k transporters covers every lane. |
| Exact search ran out of budget | `HEURISTIC` | The best answer local search found within `solver.heuristic-time-ms`. Its cost **may be higher than the minimum**. The `note` says so and reports the gap to a proven lower bound. A 422 here ("Search inconclusive") means only that no cover was found; **one may still exist**. |

**The minimum-cost guarantee holds only when the exact search completes.** When it runs out of budget, the brief's
cost-minimization objective is **not** met in general: the service returns a labelled best effort, which is an
open tradeoff, not a solved requirement. Raising the budgets extends the exact range at the price of response time.

**The lower-bound gap in the note.** LB is the sum of each lane's globally cheapest quote, ignoring k. No selection
can cost less, so LB is a proven lower bound on the optimum, and `cost - LB` is an **upper bound** on how far the
answer is from the optimum, not the actual distance. The note says, for example, "Total cost 12345 is within 4.4% of
the theoretical lower bound 11834" (rounded up), or "matches the theoretical lower bound" when they are equal.
A small gap is evidence about the answer; it is not a claim that the answer is close to optimal.

**Determinism.** Every step uses a fixed order and a fixed random seed, so equal inputs give equal outputs, unless
a time budget (`solver.max-time-ms` or `solver.heuristic-time-ms`) runs out, which depends on machine load.

### Exact: `ExactSubsetSolver` (branch-and-bound)

Contract: among all sets of exactly `min(k, N)` transporters, the minimum cost; among those, the highest matched
usage; among those, the lexicographically first set by transporter id. "Infeasible" is returned only when the
search completes and no such set covers every lane.

1. **Upper bound first.** The greedy solver's set, filled to `min(k, N)`, gives an initial cost to beat. It is only
   a bound: the answer is always a set the search itself reaches, which keeps the tie rules exact.
2. **Branch** on transporters in ascending id order, include-first. This visits complete sets in lexicographic
   order, the same order as plain enumeration. Each node keeps the per-lane minimum quote of the included
   transporters, updated incrementally.
3. **Bound** (O(lanes) per node): the cost of the included transporters together with every not-yet-decided one,
   using precomputed per-lane suffix minima. Prune when some lane can no longer be covered, when the bound exceeds
   the best cost, or when it equals the best cost and the best set already uses `min(k, lanes)` transporters (sets
   found later are lexicographically larger, so they can win neither on usage nor on ids).
4. **Complete sets:** a lower cost replaces the best; an equal cost replaces it only with strictly higher matched
   usage, computed lazily.
5. **Budget:** nodes are counted and the clock is checked every 4,096 nodes. When `solver.max-search-nodes` or
   `solver.max-time-ms` runs out, the search stops, is never reported as `EXACT`, and hands its best feasible set to
   the local search. A budget stop is never turned into a 422 while a feasible set is known.

**Matched assignment (`Assigner`, shared by every solver).** For the chosen set, each lane's "cheapest edges" are
the transporters offering its cheapest in-set quote. A maximum bipartite matching between lanes and their cheapest
edges (Kuhn's augmenting paths; lanes in ascending id, candidates in ascending transporter id, so the result is
deterministic) gives matched lanes to their matched transporter. Unmatched lanes go to their cheapest transporter
with the lowest id. Every lane still pays its cheapest in-set quote, so the cost does not change. The matching size
is exactly the largest usage any minimum-cost assignment of that set can reach: at minimum cost, each used
transporter must be cheapest for at least one lane of its own.

**Why sets of exactly `min(k, N)` are enough.** For cost: adding a transporter never raises it, because each lane
keeps its cheapest quote. For usage: any minimum-cost assignment that uses a set U still fits, at the same cost,
inside every larger set that contains U, so that set's matched usage is at least |U|.

**Complexity.** The worst case is still exponential, O(C(N, k) × lanes) nodes, which is why the budgets exist. In
practice the bound prunes whole subtrees once a good set is known, so structured inputs far beyond the old
200,000-set enumeration cap complete exactly; a test solves a C(40, 5) = 658,008-set instance exactly while
evaluating under a tenth of that many nodes. The matching (O(lanes² × k)) runs only for complete sets whose cost
ties the best.

### Heuristic fallback: `LocalSearchSolver` (coverage-first local search)

Runs only when the exact search stops at a budget. Objective, compared lexicographically: **first the number of
uncovered lanes, then total cost.** Treating coverage as primary lets it repair sets that strand lanes, which
greedy set cover cannot do; on sparse quote data (each transporter quotes only some lanes, normal in freight) the
old greedy cover often found no cover at all even though one existed.

1. **Starting sets**, in a fixed order: the exact search's best feasible set, the greedy solver's set, the union of
   each lane's globally cheapest transporter, and a few pseudo-random sets from a fixed seed. Each is resized to
   exactly `min(k, N)` by adding (or, for the union, removing) the transporter that best improves the objective.
2. **Moves:** swap one chosen transporter for one unchosen one, scanning in ascending id order and applying the
   first improving swap, until no swap improves the objective.
3. **Best across starts.** Every starting set is scored first, so the answer is never worse than the best starting
   set, and in particular never costs more than the exact search's incumbent.
4. **Balance:** for a covering result, transporters that receive no lane are dropped (cost unchanged) and the
   greedy solver's equal-cost balance step adds transporters that take a lane at no extra cost.
5. **Time budget:** `solver.heuristic-time-ms`, checked between improvement passes, so the set in hand is always
   complete and valid.

### Helper: `GreedySolver`

Cover (lowest average quote over still-uncovered lanes), improve (add the transporter that lowers the cost most)
and balance (add equal-cost transporters that raise usage). Its answers are never returned directly: it supplies
the exact search's first bound and one local-search starting set.

## Design notes

```
controller/   TransporterController       HTTP only: validation trigger, delegation, Swagger annotations
service/      InputService                validate -> map -> save
              InputValidator              cross-field rules (duplicates, unknown lanes, unquoted lanes)
              DatasetMapper               DTO -> immutable domain model
              AssignmentService           runs the pipeline, builds notes, maps outcomes to responses/errors
solver/       AssignmentSolver            Strategy interface
              ExactSubsetSolver           branch-and-bound exact search (ExactSearch result, SearchBudget limits)
              LocalSearchSolver           coverage-first local search, the budget fallback
              GreedySolver                greedy helper: first bound and a starting set
              SolverPipeline              runs exact, then local search if a budget ran out; reports the outcome
              Assigner                    one place for the final rule: cheapest quote, matched for usage
              QuoteMatrix, Combinatorics  dense quote table, lower bound; saturating C(n, k)
repository/   DatasetRepository           Repository interface
              InMemoryDatasetRepository   AtomicReference holding the latest immutable Dataset
dto/          request/response records with Bean Validation and @Schema examples
model/        Lane, Transporter, Dataset, Solution (immutable records)
exception/    ApiException hierarchy + GlobalExceptionHandler (@RestControllerAdvice)
config/       OpenAPI info, Swagger examples
```

- **Patterns used where they help:** Strategy (solvers), a pipeline that chains them (`SolverPipeline`),
  Repository (`DatasetRepository`), DTOs separate from the domain model.
- **Thread safety:** `Dataset` is immutable and stored in an `AtomicReference`, so a concurrent `/assignment` sees
  either the old input or the new one, never a mix. Solvers keep no state.
- **Validation in two layers:** Bean Validation on the DTO records for field rules; `InputValidator` for rules that
  span fields. Input is validated completely before it replaces the stored data.
- **No business logic in the controller**, and no stack traces in responses.
- **Determinism:** transporters and lanes are sorted by id once and every search uses a fixed order and seed, so
  equal inputs give equal outputs unless a time budget runs out.

## Tests

`mvn test` runs 84 tests:

- `ExactSubsetSolverTest`: Test Case 1 (k = 1, 2, 3, 7, k > N), the sample's set {3, 4, 6} and its listed
  assignment (69,001), Test Case 2 (k = 2, 3, 4), tie-breaks, and the matched assignment (equal quotes use both
  transporters, cost still beats usage, usage is bounded by the lane count, matching beats a naive per-lane rule).
- `BranchAndBoundOracleTest`: on 900 seeded random instances (dense, tie-heavy and sparse, including unquoted lanes
  and infeasible k) the branch-and-bound search returns exactly what the old plain enumeration returns, kept in the
  test tree as `EnumerationOracle`: same cost, usage, transporter ids and lane mapping. Also: an instance beyond the
  old cap solved exactly with far fewer nodes, and budget stops that keep the incumbent.
- `SolverPropertyTest`: exact cost = brute-force minimum, exact ≤ greedy, and exact usage = the brute-force maximum
  usage among minimum-cost assignments.
- `LocalSearchSolverTest`: never worse than the best starting set; a prior seed is never lost; deterministic;
  seeded sparse instances with a planted cover where the greedy cover phase fails and local search finds a cover;
  no cover gives no solution; balance still spreads equal quotes.
- `SolverPipelineTest`: exact, proven-infeasible and inconclusive outcomes; with exhausted budgets the heuristic
  answer never costs more than the exact search's incumbent.
- `GreedySolverTest`, `CombinatoricsTest`, `LowerBoundGapTest` (percent rounded up, equal bound, zero bound).
- `TransporterApiTest`: every row of the error table, both test cases over HTTP (Test Case 1 for k = 1, 2, 3, 7
  and k > N), clamping, replacement of input, OpenAPI docs. The tests post the files in `samples/`.
- `HeuristicFallbackApiTest` (exact budget of 2 nodes): `HEURISTIC` with the budget note and lower-bound gap and a
  cost no higher than the incumbent; a cover the greedy phase misses is found; the inconclusive 422 is distinct
  from the proven-infeasible one; byte-identical responses for the same input.
- `UnexpectedErrorApiTest`: an unexpected exception becomes a generic 500.

## Deployment

The service is a single stateless container (state is in memory) and runs on any Docker-capable or Java 17 host.
The only environment variable it needs is `PORT`, which most platforms set automatically.

- **Docker host (Render, Railway, Fly.io, Koyeb, Cloud Run, ...):** point the platform at this repository; it builds
  the `Dockerfile`. `render.yaml` is included for a Render Blueprint (free plan, health check on `/v3/api-docs`).
- **Java host:** `mvn -B -DskipTests package` and run `java -jar target/transporter-assignment-1.0.0.jar`.
- Swagger UI is public at `/swagger-ui.html`; no login is needed.
- `server.forward-headers-strategy=framework` makes Swagger UI call the API over https behind the platform's proxy.

**Free-tier sleep:** free plans stop idle services (Render after about 15 minutes). The first request after that
wakes the service and can take up to a minute, but it is answered normally. Because data is kept in memory, a
restart also clears the saved input: call `/input` again (an `/assignment` before that returns 409).

## Notes on the sample data

These are observations made while building the tests. The Test Case 1 figures were checked with a brute-force
script and are asserted by `ExactSubsetSolverTest`.

1. Test Case 1 shows `totalCost` 75000 with `selectedTransporters` [4, 6, 3]. On the quotes given, the
   assignments listed in that sample add up to 69,001 and use transporter 2, which is not in the selected list.
   The cheapest way to cover all five lanes using only transporters 3, 4 and 6 is 64,677. The minimum with any
   3 transporters is 60,139 (T1, T4, T5).
2. The first example in the PDF (10 lanes, 7 transporters, `maxTransporters` 3) assigns lanes 7 to 10 to
   transporters that did not quote them. Every transporter there quotes only two lanes, so covering 10 lanes needs
   at least 5 transporters and 3 cannot work. This service returns 422 for that case.
3. "Minimize cost" and "use up to k transporters" can conflict, so the priority order is stated in
   [Assumptions](#assumptions).
4. The specification does not say what to return when coverage is impossible. This service answers 422 with a
   clear message.

For reference, the minimum cost of Test Case 1 by k: k = 1: 96,940 (T4); k = 2: 62,938; k = 3: 60,139;
k ≥ 4: 59,896.

## Demo script for the Loom

2 to 5 minutes, screen and face visible throughout.

1. **Approach (30 s).** Lanes and quotes are stored in memory. For k transporters, the service tries every set of
   k transporters, gives each lane to the cheapest quote in the set (spreading equal quotes to use more
   transporters), and keeps the cheapest set, using branch-and-bound to skip sets that cannot win. The problem is
   NP-hard, so the exact search has a budget; if it runs out, a coverage-first local search answers, the response
   says `HEURISTIC`, and the note reports the gap to a proven lower bound instead of claiming a minimum.
2. **API demo on the deployed Swagger UI.** Open https://transporter-assignment-api-8bha.onrender.com/swagger-ui.html a minute before recording, so a
   sleeping free-tier instance has woken up (waking also clears the in-memory data).
   - `POST /assignment` with `{"maxTransporters": 3}` first: **409**, no input saved yet.
   - `POST /input` with the "Test Case 1" example, then `POST /assignment` with `{"maxTransporters": 3}`:
     `totalCost` 60139, transporters [1, 4, 5], `solver` EXACT.
3. **Errors.** `POST /input` with "Test Case 2", then `/assignment` with `{"maxTransporters": 2}`: **422**, no two
   transporters cover all five lanes. Optionally `{"maxTransporters": 0}` for a 400.
4. **Two technical decisions.**
   - Branch-and-bound with an explicit priority (cost, then usage via a maximum matching, then ids), checked
     against the old enumeration on hundreds of random cases, because an exact answer is checkable.
   - An honest `solver` field, a budgeted exact search and a coverage-first fallback. Large inputs get a
     best-effort answer instead of an unbounded wait, labelled `HEURISTIC` with a lower-bound gap, and "no cover
     found" is never reported as "infeasible". This is a stated tradeoff: minimum cost is guaranteed only when the
     exact search completes.
5. **One challenge.** The sample output in the specification is inconsistent (75000 listed, while its own
   assignments add up to 69,001 and use a transporter outside the selected set). The tests were therefore written
   against a brute-force-verified optimum (60,139), and a randomized test compares the solver with brute force.
