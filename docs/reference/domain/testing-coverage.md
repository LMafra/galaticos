# Test coverage — Galáticos

**Summary:** How to run, interpret, and maintain backend (Cloverage) and E2E coverage, CI gates, and analytics testing strategy. Read this when you change tests, coverage thresholds, or metric formulas. Thresholds live in `deps.edn` alias `:coverage` (currently **70** minimum of % lines and % forms).

## Coverage requirements (backend — Cloverage)

The backend uses [Cloverage](https://github.com/cloverage/cloverage) with `--fail-threshold` in `deps.edn` (alias `:coverage`; currently **70**).

In the version in use, the failure gate compares the threshold to the **minimum** of:

- **% Lines covered** (instrumented lines executed)
- **% Forms covered** (instrumented Clojure forms covered)

So **both numbers matter**: low forms fail the build even when lines are high. This is **not** the same as Istanbul/Playwright “branch coverage”; the HTML report uses yellow/red for partial branches, but CI uses lines + forms as above.

Thresholds are enforced in CI/CD and block merges that do not meet the requirement.

## Testing strategy for sports data analytics

Beyond code coverage, the analytics track requires ongoing validation of data quality and consistency.

### 1) Data contract tests

- Validate minimum shape of `matches.player-statistics`.
- Validate structure of `players.aggregated-stats`.
- Cover missing fields, invalid types, and negative values.

### 2) Reconciliation tests

- Ensure `aggregated-stats` is reproducible from `matches`.
- Test full recalculation and per-match partial recalculation.
- Ensure divergences are detected and reported.

### 3) Metric regression tests

- Use deterministic fixtures and validate key metrics:
  - `games`
  - `goals`
  - `assists`
  - `goals-per-game`
  - `assists-per-game`
- Protect formulas against accidental changes.

### 4) Acceptance criteria for analytics changes

Any change to metric calculation or data contracts must:

1. Update `docs/reference/analytics/metrics-catalog.md` and/or `docs/reference/analytics/data-contracts.md`.
2. Include a corresponding regression test.
3. Validate reconciliation with no critical divergences.

### 5) Mandatory change checklist (metric/contract)

- [ ] Metrics catalog updated (`docs/reference/analytics/metrics-catalog.md`).
- [ ] Data contracts updated (`docs/reference/analytics/data-contracts.md`) when schema changes.
- [ ] Contract and regression tests added/updated.
- [ ] Reconciliation evidence recorded in the operational runbook.

## Running coverage locally

### Backend coverage (Clojure)

Run backend coverage with:

```bash
./bin/galaticos coverage
```

This command:

- Runs all backend tests
- Collects coverage with Cloverage
- Validates `--fail-threshold` from alias `:coverage` in `deps.edn` (minimum of % lines and % forms)
- Generates HTML at `target/coverage/index.html`
- **Fails** if `min(lines, forms)` is below the threshold

**View report:**

```bash
open target/coverage/index.html  # macOS
xdg-open target/coverage/index.html  # Linux
start target/coverage/index.html  # Windows
```

### E2E coverage (Playwright)

Run E2E coverage with:

```bash
./bin/galaticos coverage:e2e
```

**Note:** E2E coverage requires:

1. Application running at `http://localhost:3000`
2. Code instrumented with istanbul/nyc (optional; see advanced setup)

Full workflow:

```bash
# Terminal 1: Start application
./bin/galaticos docker:dev start

# Terminal 2: Run E2E tests
./bin/galaticos coverage:e2e
```

### Full coverage (backend + E2E)

Run everything at once:

```bash
./bin/galaticos coverage:all
```

This command:

- Runs backend coverage
- Runs E2E coverage
- Generates consolidated report at `target/coverage-report/index.html`
- Validates thresholds
- **Fails** if backend does not meet thresholds

**View consolidated report:**

```bash
open target/coverage-report/index.html
```

## Interpreting reports

### Backend report (Cloverage)

The HTML report shows:

- **Green**: Lines covered by tests
- **Red**: Uncovered lines
- **Yellow**: Partially covered branches

#### Important metrics

- **Lines**: Percentage of instrumented lines executed
- **Forms**: Percentage of instrumented forms covered (used in the gate with lines — compare **minimum** vs `--fail-threshold`)
- **Branches** (in HTML): partial branch indication; not the sole “70% branches” gate used in other stacks

#### Example

```
Lines: 82.3%                 ✅
Forms: 70.6%                 ✅ (if threshold is 70, gate uses min of these two)
ALL FILES (Cloverage summary) ✅ if min(lines, forms) ≥ threshold
```

### E2E report

When available, the E2E report shows coverage of compiled JavaScript exercised during end-to-end tests.

**Interpretation:**

- Complements backend coverage
- Verifies full flows are tested
- Surfaces frontend code not exercised

## When coverage is low

### 1. Find uncovered areas

Open the HTML report and look for:

- Files with low **lines** or **forms** (below configured threshold)
- Completely untested functions (0%)
- Partial or uncovered branches (`if`/`else`, `cond`, etc.)

### 2. Prioritize by importance

Focus tests on:

**High priority:**

- Critical business logic
- API handlers
- Data validation
- Calculations and transformations

**Medium priority:**

- Utilities and helpers
- Formatters
- Parsers

**Low priority (may be excluded):**

- Setup/configuration code
- Main/entry points
- Purely declarative code

### 3. Write focused tests

To raise coverage effectively:

```clojure
;; ❌ Avoid: generic tests that do not exercise branches
(deftest test-handler
  (is (= 200 (:status (handler {})))))

;; ✅ Better: tests for different cases
(deftest test-handler-success
  (is (= 200 (:status (handler {:valid true})))))

(deftest test-handler-validation-error
  (is (= 400 (:status (handler {:invalid true})))))

(deftest test-handler-not-found
  (is (= 404 (:status (handler {:id "nonexistent"})))))
```

### 4. Run coverage incrementally

After adding tests:

```bash
# Check progress only
./bin/galaticos coverage

# Open report to see improvement
open target/coverage/index.html
```

## Coverage exclusions

Some files are excluded from analysis by design.

### Automatically excluded files

- `galaticos.core`: application entry point
- Files under `dev/`: development code
- Configuration and setup code

### How to exclude a file

Edit `deps.edn` if you need to exclude a specific namespace:

```clojure
:coverage {:extra-paths ["test"]
           :extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
           :main-opts ["-m" "cloverage.coverage"
                       ;; ... other options ...
                       "--ns-exclude-regex" "galaticos.core|galaticos.setup|galaticos.dev.*"]}
```

**Exclusion regex:**

- Separate namespaces with `|` (pipe)
- Use `.*` to exclude all sub-namespaces
- Example: `galaticos.dev.*` excludes `galaticos.dev.fixtures`, `galaticos.dev.utils`, etc.

### When to exclude

✅ **Valid to exclude:**

- Entry points (main functions)
- Pure configuration code
- Complex test fixtures
- Development/debugging code

❌ **Do not exclude:**

- Business logic
- Validations
- Calculations
- Any code that runs in production

## CI/CD and enforcement

### How CI works

1. **Pull request opened**
2. **GitHub Actions** runs workflow `test-coverage.yml`
3. **Backend coverage validated** (Cloverage: min(% lines, % forms) vs `--fail-threshold`)
4. **E2E tests run**
5. **Reports generated** and saved as artifacts
6. **Check passes or fails** based on thresholds

### If the check fails

1. Read the GitHub Actions log
2. Download artifacts (coverage reports)
3. Identify gaps
4. Add tests
5. Push changes

### On GitHub

- ✅ Green check: coverage OK, merge allowed
- ❌ Red check: below threshold, merge blocked
- 🟡 Yellow check: optional E2E failed but backend passed

### Available artifacts

After each CI run:

- `backend-coverage-report`: full backend HTML report
- `e2e-coverage-report`: E2E coverage data (when available)
- `playwright-report`: E2E execution report

## Advanced setup: full E2E coverage

Basic E2E coverage is configured; for full JavaScript coverage:

### 1. Install additional tools

```bash
npm install --save-dev nyc istanbul-lib-coverage babel-plugin-istanbul
```

### 2. Configure NYC

Create `.nycrc.json`:

```json
{
  "all": true,
  "include": ["resources/public/js/compiled/**/*.js"],
  "exclude": [
    "**/*.test.js",
    "**/node_modules/**"
  ],
  "reporter": ["html", "text", "json"],
  "report-dir": "playwright-coverage/report",
  "temp-dir": "playwright-coverage",
  "check-coverage": false,
  "lines": 70,
  "statements": 70,
  "functions": 70,
  "branches": 60
}
```

### 3. Instrument ClojureScript

In `shadow-cljs.edn`, for test builds:

```clojure
:app {:target :browser
      :output-dir "resources/public/js/compiled"
      ;; For tests:
      :compiler-options {:instrument true}
      ;; ... rest of config
      }
```

### 4. Collect coverage in Playwright

Create helper in `e2e/helpers/coverage.js`:

```javascript
export async function startCoverage(page) {
  await page.coverage.startJSCoverage();
}

export async function saveCoverage(page, testName) {
  const coverage = await page.coverage.stopJSCoverage();
  // Save under playwright-coverage/
  await fs.writeFile(
    `playwright-coverage/coverage-${testName}.json`,
    JSON.stringify(coverage)
  );
}
```

Use in tests:

```javascript
import { startCoverage, saveCoverage } from './helpers/coverage';

test('user login flow', async ({ page }) => {
  await startCoverage(page);
  
  // ... your test ...
  
  await saveCoverage(page, 'login-flow');
});
```

## Best practices

### Do

- Run coverage **before** opening PRs
- Add tests for **each new file**
- Keep coverage **steady** (do not let it drop)
- Focus on **test quality**, not numbers alone
- Review reports **periodically**

### Do not

- Write empty tests only to raise coverage
- Exclude important code from coverage
- Ignore uncovered branches
- Rely only on E2E for backend coverage

## Troubleshooting

### "Coverage failed to meet thresholds"

**Cause:** Code is not tested enough.

**Fix:**

1. Run: `./bin/galaticos coverage`
2. Open: `target/coverage/index.html`
3. Find red areas
4. Add tests
5. Repeat until `min(% lines, % forms)` ≥ threshold in `deps.edn`

### "No coverage data found"

**Cause:** Tests did not run or failed before collection.

**Fix:**

1. Run: `./bin/galaticos test` (without coverage)
2. Ensure tests pass
3. Then run: `./bin/galaticos coverage`

### "E2E coverage not collected"

**Cause:** Code not instrumented or app not running.

**Fix:**

1. Ensure the app is running
2. Confirm `http://localhost:3000` is reachable
3. For full E2E coverage, see “Advanced setup” above

### "Cloverage timeout"

**Cause:** Test suite too slow.

**Fix:**

1. Optimize slow tests
2. Remove unnecessary sleeps/delays
3. Use fixtures instead of repeated setup
4. Consider parallelization (carefully)

## Trend monitoring

### Monthly check

```bash
# Run full coverage
./bin/galaticos coverage:all

# Compare with previous month
# Look for:
# - Overall coverage: up or down?
# - New files: well covered?
# - Critical areas: still >90%?
```

### Optional integrations

- **Codecov**: coverage badge in README
- **Coveralls**: trend tracking
- **SonarQube**: quality + coverage analysis

## UX E2E matrix (Playwright)

Automated UI regression tests live under `e2e/ux-*.spec.js`, tagged `@ux` (plus `@ux-mobile`, `@ux-a11y`, `@ux-slow`). UX rules: [ui-decisions.md](../ui/ui-decisions.md).

| Area | Spec file | Notes |
|------|-----------|--------|
| Design system, UI kit, perceived perf | `e2e/ux-foundation.spec.js` | tabular-nums, ui-lab, badges |
| Navigation, scroll, match-return | `e2e/ux-navigation.spec.js` | breadcrumbs; PT hash routes skipped until phase 1 |
| Undo deletes | `e2e/ux-undo.spec.js` | undo toasts, commit failure, roster undo |
| Auth and session | `e2e/auth.spec.js` | login/session |
| Forms and lists | `e2e/ux-forms-lists.spec.js` | 400 retention, loading label |
| Match form | `e2e/ux-matches.spec.js` | steppers, skeleton, draft, mobile FAB |
| Players and merge | `e2e/ux-players-merge.spec.js` | merge 3-step, undo |
| Championships and seasons | `e2e/ux-championships.spec.js` | enrollment, max players, finalize |
| Teams and dashboard | `e2e/ux-teams-dashboard.spec.js` | teams, dashboard export |
| Accessibility | `e2e/ux-a11y.spec.js` | keyboard, aria, scope |
| UI copy guard | `scripts/check-ui-copy.js` | no `js/confirm` in components |
| Mobile shell | `e2e/ux-mobile-shell.spec.js` | bottom tab (Pixel 5 project) |

**Run locally:**

```bash
./bin/galaticos docker:dev start
until curl -sf http://localhost:3000/health; do sleep 2; done
./bin/galaticos db:seed-smoke
npm ci --no-fund --no-audit
npx playwright install --with-deps chromium
./bin/galaticos e2e http://localhost:3000 -- --grep @ux
./bin/galaticos e2e http://localhost:3000 -- --project=chromium-mobile
node scripts/check-ui-copy.js
```

Copy guard runs in CI before Playwright (see `.github/workflows/ci.yml`).

## Additional resources

- [Cloverage Documentation](https://github.com/cloverage/cloverage)
- [Playwright Coverage](https://playwright.dev/docs/api/class-coverage)
- [NYC (Istanbul) Documentation](https://github.com/istanbuljs/nyc)
- [Contributing guide](../../../CONTRIBUTING.md)

## Support

If you have questions about test coverage:

1. Read this document first
2. See examples in `test/` and `test-cljs/`
3. Ask on the team development channel
4. Open an issue to improve this documentation
