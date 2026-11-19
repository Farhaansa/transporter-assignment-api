# Transporter Assignment API

A Spring Boot REST service that assigns every shipping lane to one transporter so that the total cost is minimal,
using at most `maxTransporters` transporters.

- **Live API:** `<deployed-url>`
- **Swagger UI:** `<deployed-url>/swagger-ui.html`
- **OpenAPI JSON:** `<deployed-url>/v3/api-docs`

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

The only environment variable is `PORT` (default `8080`). The exact-search limit can be changed with
`solver.max-combinations` (default `200000`), for example `--solver.max-combinations=1000000`.

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
  produced. `solver` is `EXACT` (proven minimum) or `GREEDY` (heuristic, see [Solver](#solver)). `note` appears
  only when there is something to say, for example when `maxTransporters` was clamped or the greedy fallback ran.

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
| No selection of at most `maxTransporters` transporters covers every lane (the message suggests raising `maxTransporters`) | 422 |
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

The solver is a **Strategy** (`AssignmentSolver`) with two implementations; `SolverSelector` picks one per request.

### Exact: `ExactSubsetSolver`

1. Sort transporters by id and clamp k to N.
2. Enumerate every set of exactly `min(k, N)` transporters in lexicographic order (iterative combination
   generation, no recursion or streams in the hot loop).
3. For each set, cost = sum over lanes of the cheapest quote in the set; skip sets that leave a lane uncovered.
4. Keep the best by lowest cost, then higher **matched usage**, then the first set found.
5. Build the assignment for the best set with the shared `Assigner` (below).

**Matched assignment (`Assigner`, shared by both solvers).** For the chosen set, each lane's "cheapest edges" are
the transporters offering its cheapest in-set quote. A maximum bipartite matching between lanes and their cheapest
edges (Kuhn's augmenting paths; lanes in ascending id, candidates in ascending transporter id, so the result is
deterministic) gives matched lanes to their matched transporter. Unmatched lanes go to their cheapest transporter
with the lowest id. Every lane still pays its cheapest in-set quote, so the cost does not change. The matching size
is exactly the largest usage any minimum-cost assignment of that set can reach: at minimum cost, each used
transporter must be cheapest for at least one lane of its own.

**Why sets of exactly `min(k, N)` are enough.** For cost: adding a transporter never raises it, because each lane
keeps its cheapest quote. For usage: any minimum-cost assignment that uses a set U still fits, at the same cost,
inside every larger set that contains U, so that set's matched usage is at least |U|. A randomized test compares
both cost and usage with a brute force over every possible assignment.

**Complexity.** The cost scan is **O(C(N, k) × lanes × k)** time and O(N × lanes) memory; for 7 transporters and
k = 3 that is 35 sets. The matching (O(lanes² × k)) runs only for a set whose cost ties the best so far, and not at
all once the best set already uses `min(k, lanes)` transporters. Inputs with many exactly equal quotes therefore
cost more than the scan alone; inputs without ties cost nothing extra.

### Greedy fallback: `GreedySolver`

Used only when C(N, k) exceeds `solver.max-combinations` (default **200,000**; C(N, k) is computed with saturation,
so it cannot overflow).

1. **Cover:** repeatedly add the transporter with the lowest average quote over the lanes it can still cover,
   until every lane is covered. Fail if a lane cannot be covered or more than k transporters are needed.
2. **Improve:** while fewer than k are chosen, add the transporter that lowers the total cost most.

The greedy result is **not guaranteed optimal** (and it may miss a feasible selection that exists), so the response
says `"solver": "GREEDY"` and adds a `note`. A seeded randomized test checks that the exact cost is never higher than
the greedy cost, and that the exact cost matches a brute-force search over every subset.

## Design notes

```
controller/   TransporterController       HTTP only: validation trigger, delegation, Swagger annotations
service/      InputService                validate -> map -> save
              InputValidator              cross-field rules (duplicates, unknown lanes, unquoted lanes)
              DatasetMapper               DTO -> immutable domain model
              AssignmentService           runs the chosen solver, builds notes and the response
solver/       AssignmentSolver            Strategy interface
              ExactSubsetSolver           exhaustive search
              GreedySolver                fallback heuristic
              SolverSelector              picks the strategy from the search-space size (simple factory)
              Assigner                    one place for the final rule: cheapest quote, matched for usage
              QuoteMatrix, Combinatorics  dense quote table; saturating C(n, k)
repository/   DatasetRepository           Repository interface
              InMemoryDatasetRepository   AtomicReference holding the latest immutable Dataset
dto/          request/response records with Bean Validation and @Schema examples
model/        Lane, Transporter, Dataset, Solution (immutable records)
exception/    ApiException hierarchy + GlobalExceptionHandler (@RestControllerAdvice)
config/       OpenAPI info, Swagger examples
```

- **Patterns used where they help:** Strategy (solvers), a small factory/selector (`SolverSelector`), Repository
  (`DatasetRepository`), DTOs separate from the domain model.
- **Thread safety:** `Dataset` is immutable and stored in an `AtomicReference`, so a concurrent `/assignment` sees
  either the old input or the new one, never a mix. Solvers keep no state.
- **Validation in two layers:** Bean Validation on the DTO records for field rules; `InputValidator` for rules that
  span fields. Input is validated completely before it replaces the stored data.
- **No business logic in the controller**, and no stack traces in responses.
- **Determinism:** transporters and lanes are sorted by id once, so equal inputs always give equal outputs.

## Tests

`mvn test` runs 61 tests:

- `ExactSubsetSolverTest`: Test Case 1 (k = 1, 2, 3, 7, k > N), the sample's set {3, 4, 6} and its listed
  assignment (69,001), Test Case 2 (k = 2, 3, 4), tie-breaks, and the matched assignment (equal quotes use both
  transporters, cost still beats usage, usage is bounded by the lane count, matching beats a naive per-lane rule).
- `GreedySolverTest`: covers every lane, respects k, returns empty when infeasible.
- `SolverPropertyTest`: seeded random datasets; exact cost = brute-force minimum, exact ≤ greedy, and exact usage =
  the brute-force maximum usage among minimum-cost assignments (brute force over every possible assignment).
- `CombinatoricsTest`: C(7,3) = 35, C(5,5) = 1, the largest exact value, saturation.
- `TransporterApiTest`: every row of the error table, both test cases over HTTP, clamping, replacement of input,
  OpenAPI docs. The tests post the files in `samples/`, so the published samples are tested too.
- `GreedyFallbackApiTest`: with `solver.max-combinations=5` the response says `GREEDY` with a note.
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
   k transporters, gives each lane to the cheapest quote in the set, and keeps the cheapest set. Above 200,000
   combinations it falls back to a greedy heuristic and says so in the response.
2. **API demo on the deployed Swagger UI.** Open `<deployed-url>/swagger-ui.html` a minute before recording, so a
   sleeping free-tier instance has woken up (waking also clears the in-memory data).
   - `POST /assignment` with `{"maxTransporters": 3}` first: **409**, no input saved yet.
   - `POST /input` with the "Test Case 1" example, then `POST /assignment` with `{"maxTransporters": 3}`:
     `totalCost` 60139, transporters [1, 4, 5], `solver` EXACT.
3. **Errors.** `POST /input` with "Test Case 2", then `/assignment` with `{"maxTransporters": 2}`: **422**, no two
   transporters cover all five lanes. Optionally `{"maxTransporters": 0}` for a 400.
4. **Two technical decisions.**
   - Exact enumeration with an explicit, deterministic tie-break, because the inputs are small and an exact answer
     is checkable.
   - An honest `solver` field and a greedy fallback with a configurable limit, so a large input still gets an answer
     and the caller knows whether it is proven optimal.
5. **One challenge.** The sample output in the specification is inconsistent (75000 listed, while its own
   assignments add up to 69,001 and use a transporter outside the selected set). The tests were therefore written
   against a brute-force-verified optimum (60,139), and a randomized test compares the solver with brute force.
