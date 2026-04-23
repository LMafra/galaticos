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

module.exports = { loginAsAdmin, getStoredToken, saveCoverage, getAdminToken };


