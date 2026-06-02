/**
 * Shared Playwright helpers for Galáticos E2E tests.
 */

const fs = require('fs/promises');
const path = require('path');

const COVERAGE_DIR = 'playwright-coverage';

async function loginAsAdmin(page) {
  await page.goto('/#/login');

  // Note: our inputs are not associated with <label for=...>, so use placeholders.
  await page.getByPlaceholder('Digite seu usuário').fill('admin');
  await page.getByPlaceholder('Digite sua senha').fill('admin');
  await page.getByRole('button', { name: 'Entrar' }).click();

  // Redirect happens to dashboard; wait for dashboard heading (h1 - avoid strict mode with multiple headings).
  await page.getByRole('heading', { name: 'Dashboard', level: 1 }).waitFor();
}

async function getStoredToken(page) {
  return await page.evaluate(() => window.localStorage.getItem('galaticos.auth.token'));
}

function coverageEnabled() {
  return process.env.COVERAGE === 'true';
}

function sanitizeCoverageName(testInfo) {
  if (!testInfo || typeof testInfo.titlePath !== 'function') {
    return `coverage-${Date.now()}`;
  }
  const name = testInfo.titlePath().join(' ');
  return name.replace(/[^a-z0-9-_]+/gi, '-').toLowerCase();
}

async function saveCoverage(page, testInfo) {
  if (!coverageEnabled() || !page || typeof page.evaluate !== 'function') {
    return;
  }
  let coverage;
  try {
    coverage = await page.evaluate(() => window.__coverage__ || null);
  } catch {
    // Page/context may already be closed after a timeout or failure.
    return;
  }
  if (!coverage) {
    return;
  }
  await fs.mkdir(COVERAGE_DIR, { recursive: true });
  const filename = path.join(COVERAGE_DIR, `${sanitizeCoverageName(testInfo)}.json`);
  await fs.writeFile(filename, JSON.stringify(coverage));
}

/**
 * @param {import('@playwright/test').APIRequestContext} request
 * @returns {Promise<string | null>}
 */
async function getAdminToken(request) {
  const r = await request.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin' },
  });
  if (!r.ok()) return null;
  const j = await r.json();
  return j?.data?.token ?? null;
}

/**
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} token
 * @param {string} method
 * @param {string} path
 * @param {Record<string, unknown>} [data]
 */
async function apiJson(request, token, method, path, data = undefined) {
  const opts = {
    method,
    headers: { Authorization: `Bearer ${token}` },
  };
  if (data !== undefined && method !== 'GET') {
    opts.headers['Content-Type'] = 'application/json';
    opts.data = data;
  }
  const response = await request.fetch(path, opts);
  const body = await response.json().catch(() => ({}));
  return { response, body };
}

/**
 * Activate the first season of a championship (required for POST /api/matches).
 * @returns {Promise<string|null>} season id when active
 */
async function activateChampionshipSeason(request, token, championshipId) {
  const { response, body } = await apiJson(
    request,
    token,
    'GET',
    `/api/championships/${championshipId}/seasons`
  );
  if (!response.ok()) return null;
  const seasons = Array.isArray(body?.data) ? body.data : [];
  const existing = seasons.find((s) => s?.status === 'active');
  if (existing?._id) return String(existing._id);
  const seasonId = seasons[0]?._id;
  if (!seasonId) return null;
  const { response: actRes } = await apiJson(
    request,
    token,
    'POST',
    `/api/seasons/${seasonId}/activate`,
    {}
  );
  return actRes.ok() ? String(seasonId) : null;
}

module.exports = {
  loginAsAdmin,
  getStoredToken,
  saveCoverage,
  getAdminToken,
  apiJson,
  activateChampionshipSeason,
};


