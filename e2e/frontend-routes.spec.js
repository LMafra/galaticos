/**
 * Inventory smoke: visit every SPA hash route once (@routes).
 * Uses chromium storageState (auth.setup). Requires seed data for param routes.
 */
const { test, expect } = require('@playwright/test');
const {
  getAdminToken,
  apiJson,
  saveCoverage,
  collectPageFaults,
  pageHeading,
  getGalaticosTeamId,
} = require('./_helpers');
const {
  SPA_STATIC_ROUTES,
  SPA_PARAM_ROUTES,
  SPA_PT_STATIC,
  SPA_PT_PARAM,
} = require('./_route-inventory');

/** @type {Record<string, string> | null} */
let ids = null;

async function resolveSpaIds(request) {
  const token = await getAdminToken(request);
  expect(token, 'admin token (db:seed-smoke)').toBeTruthy();

  const { response: pRes, body: pBody } = await apiJson(request, token, 'GET', '/api/players');
  expect(pRes.ok()).toBeTruthy();
  const players = Array.isArray(pBody?.data) ? pBody.data : [];
  expect(players.length).toBeGreaterThan(0);
  const player = players[0];
  const playerId = String(player._id);
  const playerName = player.name || player.nickname || 'Jogador';

  const { response: cRes, body: cBody } = await apiJson(request, token, 'GET', '/api/championships');
  expect(cRes.ok()).toBeTruthy();
  const championships = Array.isArray(cBody?.data) ? cBody.data : [];
  expect(championships.length).toBeGreaterThan(0);

  let championshipId = null;
  let championshipName = null;
  let seasonId = null;
  let seasonHeading = null;
  for (const champ of championships) {
    const cid = String(champ._id);
    const { response: sRes, body: sBody } = await apiJson(
      request,
      token,
      'GET',
      `/api/championships/${cid}/seasons`
    );
    if (!sRes.ok()) continue;
    const seasons = Array.isArray(sBody?.data) ? sBody.data : [];
    if (seasons.length === 0) continue;
    championshipId = cid;
    championshipName = champ.name || 'Campeonato';
    seasonId = String(seasons[0]._id);
    const seasonLabel = seasons[0].season || seasons[0].name || 'Temporada';
    seasonHeading = `${championshipName} · ${seasonLabel}`;
    break;
  }
  expect(championshipId, 'Need a championship with seasons').toBeTruthy();

  const { response: mRes, body: mBody } = await apiJson(request, token, 'GET', '/api/matches');
  expect(mRes.ok()).toBeTruthy();
  const matches = Array.isArray(mBody?.data) ? mBody.data : [];
  expect(matches.length).toBeGreaterThan(0);
  const match = matches[0];
  const matchId = String(match._id);
  const matchHeading = match.opponent || 'Detalhe da partida';

  const teamId = await getGalaticosTeamId(request, token);
  const { response: tRes, body: tBody } = await apiJson(request, token, 'GET', `/api/teams/${teamId}`);
  expect(tRes.ok()).toBeTruthy();
  const teamName = tBody?.data?.name || 'Galáticos';

  return {
    playerId,
    playerName,
    championshipId,
    championshipName,
    seasonId,
    seasonHeading,
    matchId,
    matchHeading,
    teamId,
    teamName,
  };
}

function headingFor(route) {
  if (route.heading) return route.heading;
  if (route.headingFrom && ids) return ids[route.headingFrom];
  return null;
}

async function expectAnyHeading(page, heading, { timeout = 15_000 } = {}) {
  const names = Array.isArray(heading) ? heading : [heading];
  // Prefer an exact heading match; fall back to visible text (eyebrow "Temporada").
  const combined = new RegExp(
    names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')
  );
  const headingLoc = page.getByRole('heading', { name: combined }).first();
  if (await headingLoc.isVisible().catch(() => false)) {
    await expect(headingLoc).toBeVisible({ timeout });
    return;
  }
  // Season detail: eyebrow "Temporada" may not be a heading role.
  const textLoc = page.locator('#main-content').getByText(combined).first();
  await expect(textLoc).toBeVisible({ timeout });
}

async function visitRoute(page, hashPath, heading, testInfo) {
  const collector = collectPageFaults(page);
  try {
    await page.goto(`/#${hashPath}`);
    // Wait for SPA mount
    await expect(page.locator('#app')).toBeVisible({ timeout: 15_000 });
    if (heading) {
      await expectAnyHeading(page, heading);
    }
    // Brief settle for late console/network errors
    await page.waitForTimeout(300);
    expect(collector.faults, collector.faults.join('\n')).toEqual([]);
  } finally {
    collector.dispose();
    await saveCoverage(page, testInfo);
  }
}

test.describe('SPA route inventory', { tag: '@routes' }, () => {
  test.beforeAll(async ({ request }) => {
    ids = await resolveSpaIds(request);
  });

  for (const route of SPA_STATIC_ROUTES) {
    test(`EN ${route.path}`, async ({ page }, testInfo) => {
      await visitRoute(page, route.path, route.heading, testInfo);
    });
  }

  for (const route of SPA_PARAM_ROUTES) {
    test(`EN ${route.name}`, async ({ page }, testInfo) => {
      expect(ids).toBeTruthy();
      const path = route.resolve(ids);
      const heading = headingFor(route);
      await visitRoute(page, path, heading, testInfo);
    });
  }

  for (const route of SPA_PT_STATIC) {
    test(`PT ${route.path}`, async ({ page }, testInfo) => {
      await visitRoute(page, route.path, route.heading, testInfo);
    });
  }

  for (const route of SPA_PT_PARAM) {
    test(`PT ${route.name}`, async ({ page }, testInfo) => {
      expect(ids).toBeTruthy();
      const path = route.resolve(ids);
      const heading = headingFor(route);
      await visitRoute(page, path, heading, testInfo);
    });
  }
});
