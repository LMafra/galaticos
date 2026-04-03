#!/bin/bash
# Script to run complete test coverage for the Galáticos project
# Combines backend (Clojure) and E2E (Playwright) coverage

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Change to project root
cd_project_root

log_info "Running complete test coverage for Galáticos..."
echo ""
log_info "Backend: Cloverage min(% lines, % forms) ≥ threshold in deps.edn (:coverage --fail-threshold)"
echo ""

# Track overall success
OVERALL_SUCCESS=true
BACKEND_COVERAGE_MET=false
E2E_RAN=false

# Create coverage report directory
mkdir -p target/coverage-report

# =============================================================================
# 1. Run Backend Coverage (Clojure)
# =============================================================================

log_header "1/2: Backend Coverage (Clojure)"
echo ""

if [[ -d "test" ]]; then
    if "$SCRIPT_DIR/coverage.sh"; then
        log_success "✅ Backend coverage passed (Cloverage thresholds met)"
        BACKEND_COVERAGE_MET=true
        
        # Copy backend coverage report
        if [[ -d "target/coverage" ]]; then
            log_info "Copying backend coverage report..."
            cp -r target/coverage target/coverage-report/backend
        fi
    else
        log_error "❌ Backend coverage failed to meet thresholds"
        OVERALL_SUCCESS=false
    fi
else
    log_warning "No backend tests found (test/ directory missing)"
fi

echo ""
echo "=============================================================================="
echo ""

# =============================================================================
# 2. Run E2E Coverage (Playwright)
# =============================================================================

log_header "2/2: E2E Coverage (Playwright)"
echo ""

if [[ -d "e2e" ]] && command_exists node; then
    if "$SCRIPT_DIR/../e2e/coverage.sh"; then
        log_success "✅ E2E tests completed"
        E2E_RAN=true
        
        # Copy E2E coverage report if available
        if [[ -d "playwright-coverage/report" ]]; then
            log_info "Copying E2E coverage report..."
            cp -r playwright-coverage/report target/coverage-report/e2e
        fi
    else
        log_warning "⚠️  E2E coverage collection encountered issues (non-critical)"
        # E2E coverage is optional, don't fail overall build
        E2E_RAN=true
    fi
else
    if [[ ! -d "e2e" ]]; then
        log_warning "No E2E tests found (e2e/ directory missing)"
    elif ! command_exists node; then
        log_warning "Node.js not found; skipping E2E coverage"
    fi
fi

echo ""
echo "=============================================================================="
echo ""

# =============================================================================
# Generate Consolidated Report
# =============================================================================

log_header "Coverage Report Summary"
echo ""

# Create index.html for consolidated report
cat > target/coverage-report/index.html <<'EOF'
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Galáticos - Coverage Report</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            background: #f5f5f5;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
        }
        h1 { margin: 0 0 10px 0; }
        .subtitle { opacity: 0.9; }
        .cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .card {
            background: white;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            transition: transform 0.2s;
        }
        .card:hover { transform: translateY(-5px); }
        .card h2 {
            margin: 0 0 15px 0;
            color: #333;
            font-size: 1.3em;
        }
        .card p { color: #666; margin: 10px 0; }
        .card a {
            display: inline-block;
            margin-top: 15px;
            padding: 10px 20px;
            background: #667eea;
            color: white;
            text-decoration: none;
            border-radius: 5px;
            transition: background 0.2s;
        }
        .card a:hover { background: #5568d3; }
        .requirements {
            background: white;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .requirements h2 { color: #333; }
        .requirement {
            padding: 10px;
            margin: 10px 0;
            background: #f8f9fa;
            border-left: 4px solid #667eea;
            border-radius: 4px;
        }
        .requirement strong { color: #667eea; }
        .footer {
            margin-top: 30px;
            text-align: center;
            color: #999;
            font-size: 0.9em;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🚀 Galáticos - Coverage Report</h1>
        <p class="subtitle">Test Coverage Dashboard</p>
    </div>

    <div class="cards">
        <div class="card">
            <h2>🔧 Backend (Clojure)</h2>
            <p>Unit and integration tests for backend services</p>
            <p><strong>Tool:</strong> Cloverage</p>
            <a href="backend/index.html">View Backend Coverage →</a>
        </div>

        <div class="card">
            <h2>🌐 E2E (Playwright)</h2>
            <p>End-to-end tests covering user workflows</p>
            <p><strong>Tool:</strong> Playwright + NYC</p>
            <a href="e2e/index.html">View E2E Coverage →</a>
        </div>
    </div>

    <div class="requirements">
        <h2>📊 Coverage Requirements</h2>
        <div class="requirement">
            <strong>Backend (Cloverage):</strong> min(% lines, % forms) ≥ <code>--fail-threshold</code> in <code>deps.edn</code> (<code>:coverage</code>)
        </div>
        <div class="requirement">
            <strong>Enforcement:</strong> CI/CD blocks PRs when Cloverage fails the configured threshold
        </div>
    </div>

    <div class="footer">
        <p>Generated by Galáticos Coverage Tools</p>
    </div>
</body>
</html>
EOF

log_success "📊 Consolidated coverage report created"
echo ""

# Print summary
if $BACKEND_COVERAGE_MET; then
    echo "  ✅ Backend: Coverage thresholds met (Cloverage)"
else
    echo "  ❌ Backend: Coverage thresholds NOT met"
fi

if $E2E_RAN; then
    echo "  ℹ️  E2E: Tests executed (coverage collection optional)"
else
    echo "  ⚠️  E2E: Not executed"
fi

echo ""
log_info "📁 Full coverage report available at:"
log_info "   file://$(pwd)/target/coverage-report/index.html"
echo ""

# Exit with appropriate code
if $OVERALL_SUCCESS; then
    log_success "🎉 Coverage validation PASSED!"
    exit 0
else
    log_error "❌ Coverage validation FAILED!"
    log_info "Run './bin/galaticos coverage' for detailed backend coverage report"
    exit 1
fi

