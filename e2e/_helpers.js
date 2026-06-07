/**
 * Shared Playwright helpers for Galáticos E2E tests.
 */

const fs = require('fs/promises');
const path = require('path');
const { expect } = require('@playwright/test');

const COVERAGE_DIR = 'playwright-coverage';

function mainContent(page) {
  return page.locator('#main-content');
}

function pageHeading(page, name) {
  // Banner h1 mounts with layout; main h2 may lag (auth gate, hub pages). .first() avoids strict mode.
  return page.getByRole('heading', { name }).first();
}

function bannerHeading(page, name) {
  return page.getByRole('banner').getByRole('heading', { name, level: 1 });
}

/** Wait for layout chrome, then route title (banner or main). */
async function expectPageTitle(page, title, { timeout = 15_000 } = {}) {
  await expect(page.getByRole('banner')).toBeVisible({ timeout });
  await expect(pageHeading(page, title)).toBeVisible({ timeout });
}

function formFieldAlert(page, text) {
  return mainContent(page).locator('p[role="alert"]', { hasText: text });
}

function toastRegion(page) {
  return page.getByLabel('Notificações');
}

async function expectUndoToast(page, subjectPattern = /removido/i) {
  const region = toastRegion(page);
  await expect(region).toBeVisible({ timeout: 10_000 });
  await expect(region.getByText(subjectPattern)).toBeVisible();
  await expect(region.getByText(/Desfazer nos próximos 10 segundos/i)).toBeVisible();
}

async function clickUndo(page) {
  await toastRegion(page).getByRole('button', { name: 'Desfazer' }).click();
}

async function expectBreadcrumb(page, labels) {
  const nav = page.getByRole('navigation', { name: 'Breadcrumb' });
  await expect(nav).toBeVisible();
  for (const label of labels) {
    await expect(nav.getByText(label, { exact: false })).toBeVisible();
  }
}

async function waitSkeletonHidden(page, { timeout = 15_000 } = {}) {
  const skeleton = page.locator('.animate-pulse').first();
  if (await skeleton.isVisible().catch(() => false)) {
    await expect(skeleton).toBeHidden({ timeout });
  }
}

async function loginAsAdmin(page) {
  await page.goto('/#/login');

  // Note: our inputs are not associated with <label for=...>, so use placeholders.
  await page.getByPlaceholder('Digite seu usuário').fill('admin');
  await page.getByPlaceholder('Digite sua senha').fill('admin');
  await mainContent(page).getByRole('button', { name: 'Entrar' }).click();

  try {
    await page.waitForURL(/\/#\/dashboard/, { timeout: 30_000 });
  } catch {
    if (await page.getByText(/Não foi possível entrar|não reconhecidos/i).isVisible()) {
      throw new Error(
        'Login failed (admin/admin). Seed E2E data: ./bin/galaticos db:seed-smoke — then rerun E2E.'
      );
    }
    throw new Error('Login did not reach /#/dashboard within 30s. Check API and E2E_BASE_URL.');
  }

  await expect(page.getByRole('banner')).toBeVisible({ timeout: 15_000 });
  await expect(pageHeading(page, 'Dashboard')).toBeVisible();
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
 * JWT from the authenticated browser session (storageState from auth.setup).
 * Prefer this over POST /api/auth/login when the test already has a logged-in page.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string | null>}
 */
async function getAdminTokenFromPage(page) {
  if (!page) return null;
  try {
    return await getStoredToken(page);
  } catch {
    return null;
  }
}

/**
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {import('@playwright/test').Page} [page] optional — reuse token from storageState
 * @returns {Promise<string | null>}
 */
async function getAdminToken(request, page = null) {
  const fromPage = await getAdminTokenFromPage(page);
  if (fromPage) return fromPage;

  const maxAttempts = 3;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const r = await request.post('/api/auth/login', {
      headers: { 'Content-Type': 'application/json' },
      data: { username: 'admin', password: 'admin' },
    });
    if (r.ok()) {
      const j = await r.json().catch(() => ({}));
      const token = j?.data?.token ?? null;
      if (token) return token;
    }
    if (attempt < maxAttempts - 1) {
      await new Promise((res) => setTimeout(res, 250 * (attempt + 1)));
    }
  }
  return null;
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

async function getGalaticosTeamId(request, token) {
  const { response, body } = await apiJson(request, token, 'GET', '/api/teams');
  expect(response.ok()).toBeTruthy();
  const teams = Array.isArray(body?.data) ? body.data : [];
  const galaticos = teams.find((t) => t?.name === 'Galáticos' || t?.name === 'Galaticos');
  expect(galaticos?._id, 'Need Galáticos team (db:seed-smoke)').toBeTruthy();
  return String(galaticos._id);
}

/**
 * Championship with active season + Galáticos player enrolled (required for new matches).
 * @returns {Promise<string>} championship id
 */
async function setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page = null) {
  const token = await getAdminToken(request, page);
  expect(token, 'admin token (db:seed-smoke: admin/admin)').toBeTruthy();

  const unique = Date.now();
  const { response: cRes, body: cBody } = await apiJson(request, token, 'POST', '/api/championships', {
    name: `E2E Champ ${unique}`,
    season: '2026',
    'titles-count': 0,
    status: 'active',
  });
  expect(cRes.ok(), JSON.stringify(cBody)).toBeTruthy();
  const championshipId = cBody?.data?._id;
  expect(championshipId).toBeTruthy();
  const seasonId = await activateChampionshipSeason(request, token, championshipId);
  expect(seasonId, 'active season for match create').toBeTruthy();

  const teamId = await getGalaticosTeamId(request, token);

  const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
    name: `E2E Match Roster ${unique}`,
    position: 'Atacante',
    'team-id': teamId,
  });
  expect(pRes.ok(), JSON.stringify(pBody)).toBeTruthy();
  const playerId = pBody?.data?._id;
  expect(playerId).toBeTruthy();

  const { response: eRes, body: eBody } = await apiJson(
    request,
    token,
    'POST',
    `/api/championships/${championshipId}/enroll/${playerId}`,
    {}
  );
  expect(eRes.ok(), JSON.stringify(eBody)).toBeTruthy();

  return String(championshipId);
}

/**
 * @returns {Promise<{ championshipId: string, playerIds: string[] }>}
 */
async function setupChampionshipWithMax(request, maxPlayers, page = null) {
  const token = await getAdminToken(request, page);
  expect(token).toBeTruthy();
  const unique = Date.now();
  const { response, body } = await apiJson(request, token, 'POST', '/api/championships', {
    name: `E2E Max ${unique}`,
    season: '2026',
    'titles-count': 0,
    status: 'active',
    'max-players': maxPlayers,
  });
  expect(response.ok(), JSON.stringify(body)).toBeTruthy();
  const championshipId = String(body?.data?._id);
  await activateChampionshipSeason(request, token, championshipId);
  const teamId = await getGalaticosTeamId(request, token);
  const playerIds = [];
  for (let i = 0; i < maxPlayers; i++) {
    const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
      name: `E2E Max P${i} ${unique}`,
      position: 'Atacante',
      'team-id': teamId,
    });
    expect(pRes.ok()).toBeTruthy();
    const pid = pBody?.data?._id;
    playerIds.push(String(pid));
    const { response: eRes } = await apiJson(
      request,
      token,
      'POST',
      `/api/championships/${championshipId}/enroll/${pid}`,
      {}
    );
    expect(eRes.ok()).toBeTruthy();
  }
  return { championshipId, playerIds };
}

/**
 * @returns {Promise<{ playerA: string, playerB: string, names: string[] }>}
 */
async function setupTwoPlayersForMerge(request, page = null) {
  const token = await getAdminToken(request, page);
  expect(token).toBeTruthy();
  const unique = Date.now();
  const baseName = `E2E Merge Silva ${unique}`;
  const teamId = await getGalaticosTeamId(request, token);
  const { body: aBody, response: aRes } = await apiJson(request, token, 'POST', '/api/players', {
    name: baseName,
    position: 'Atacante',
    'team-id': teamId,
  });
  expect(aRes.ok()).toBeTruthy();
  const { body: bBody, response: bRes } = await apiJson(request, token, 'POST', '/api/players', {
    name: `${baseName} Jr`,
    position: 'Atacante',
    'team-id': teamId,
  });
  expect(bRes.ok()).toBeTruthy();
  return {
    playerA: String(aBody?.data?._id),
    playerB: String(bBody?.data?._id),
    names: [baseName, `${baseName} Jr`],
  };
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ opponent?: string, date?: string }} [opts]
 */
async function fillMatchMinimal(page, opts = {}) {
  const opponent = opts.opponent ?? 'Adversário E2E UX';
  const date = opts.date ?? '2025-06-01';
  await page.getByLabel(/^Data/).fill(date);
  await page.getByLabel(/^Adversário/).fill(opponent);
  await page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button', { name: '+' }).click();
}

/**
 * Poll localStorage until match draft opponent matches (debounced save + route key variants).
 * @param {import('@playwright/test').Page} page
 * @param {string} champId
 * @param {string} opponent
 * @param {number} [timeout]
 */
async function waitForMatchDraftOpponent(page, champId, opponent, timeout = 15_000) {
  await expect
    .poll(
      async () =>
        page.evaluate(
          ({ champId, opponent }) => {
            const preferred = [`galaticos.match-draft.new-${champId}`, 'galaticos.match-draft.new'];
            const keys = new Set(preferred);
            for (let i = 0; i < window.localStorage.length; i += 1) {
              const key = window.localStorage.key(i);
              if (key?.startsWith('galaticos.match-draft.')) keys.add(key);
            }
            for (const key of keys) {
              try {
                const raw = window.localStorage.getItem(key);
                if (!raw) continue;
                const parsed = JSON.parse(raw);
                if (parsed?.['form-data']?.opponent === opponent) return true;
              } catch {
                /* ignore */
              }
            }
            return false;
          },
          { champId, opponent }
        ),
      { timeout, intervals: [100, 200, 500] }
    )
    .toBe(true);
}

module.exports = {
  loginAsAdmin,
  mainContent,
  pageHeading,
  bannerHeading,
  expectPageTitle,
  formFieldAlert,
  toastRegion,
  expectUndoToast,
  clickUndo,
  expectBreadcrumb,
  waitSkeletonHidden,
  getStoredToken,
  getAdminTokenFromPage,
  saveCoverage,
  getAdminToken,
  apiJson,
  activateChampionshipSeason,
  getGalaticosTeamId,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  setupChampionshipWithMax,
  setupTwoPlayersForMerge,
  fillMatchMinimal,
  waitForMatchDraftOpponent,
};
