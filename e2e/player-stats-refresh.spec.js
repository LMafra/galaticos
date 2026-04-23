/**
 * E2E: player aggregate refresh after match CRUD (async job) and POST /api/aggregations/reconcile.
 * Run with app up + (recommended) db:seed-smoke; uses API to avoid flakiness from UI timing.
 */
const { test, expect } = require('@playwright/test');
const { getAdminToken, saveCoverage } = require('./_helpers');

async function apiJson(request, token, method, path, data = undefined) {
  const response = await request.fetch(path, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data,
  });
  const body = await response.json().catch(() => ({}));
  return { response, body };
}

/**
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} token
 * @param {string} playerId
 * @param {number} [timeoutMs]
 */
/** Reconcile is POST; wrap-json-body requires `application/json` when a body is present. */
function postReconcile(request, extraHeaders = {}) {
  return request.post('/api/aggregations/reconcile', {
    headers: { 'Content-Type': 'application/json', ...extraHeaders },
    data: {},
  });
}

async function waitForPlayerAggregatedStats(request, token, playerId, timeoutMs = 20_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const r = await request.get(`/api/players/${playerId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (r.ok()) {
      const j = await r.json();
      const p = j?.data;
      const raw = p?.['aggregated-stats'] ?? p?.aggregated_stats;
      if (raw && typeof raw === 'object') {
        return raw;
      }
    }
    await new Promise((res) => setTimeout(res, 300));
  }
  return null;
}

test.describe('Player stats refresh (async + reconcile API)', { tag: '@analytics' }, () => {
  test('POST /api/aggregations/reconcile without token (401, or 200 if server uses DISABLE_AUTH)', async ({ request }, testInfo) => {
    try {
      const r = await postReconcile(request);
      const st = r.status();
      if (st === 401) {
        expect(st).toBe(401);
      } else {
        // wrap-auth lets request through when DISABLE_AUTH (dev)
        expect(st).toBe(200);
        const b = await r.json();
        expect(b.success).toBe(true);
      }
    } finally {
      await saveCoverage(null, testInfo);
    }
  });

  test('POST /api/aggregations/reconcile succeeds with admin token', async ({ request }, testInfo) => {
    try {
      const token = await getAdminToken(request);
      expect(token, 'admin token (run db:seed-smoke and check admin/admin)').toBeTruthy();
      const r = await postReconcile(request, { Authorization: `Bearer ${token}` });
      expect(r.status(), await r.text()).toBe(200);
      const body = await r.json();
      expect(body.success).toBe(true);
      expect(typeof body.data?.updated).toBe('number');
    } finally {
      await saveCoverage(null, testInfo);
    }
  });

  test('after creating a match, player eventually gets aggregated-stats (background job)', async ({ request }, testInfo) => {
    try {
      const token = await getAdminToken(request);
      expect(token).toBeTruthy();

      const unique = Date.now();
      const { body: cBody, response: cRes } = await apiJson(request, token, 'POST', '/api/championships', {
        name: `E2E Stats ${unique}`,
        season: '2026',
        'titles-count': 0,
      });
      expect(cRes.ok(), JSON.stringify(cBody)).toBeTruthy();
      const championshipId = cBody?.data?._id;
      expect(championshipId).toBeTruthy();

      const { body: tBody, response: tRes } = await apiJson(request, token, 'POST', '/api/teams', {
        name: `E2E Team Stats ${unique}`,
      });
      expect(tRes.ok()).toBeTruthy();
      const teamId = tBody?.data?._id;
      expect(teamId).toBeTruthy();

      const { body: pBody, response: pRes } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E PStats ${unique}`,
        position: 'Atacante',
        'team-id': teamId,
      });
      expect(pRes.ok()).toBeTruthy();
      const playerId = pBody?.data?._id;
      expect(playerId).toBeTruthy();

      const { response: eRes } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/enroll/${playerId}`,
        {}
      );
      expect(eRes.ok()).toBeTruthy();

      const { response: mRes } = await apiJson(request, token, 'POST', '/api/matches', {
        'championship-id': championshipId,
        'home-team-id': teamId,
        date: '2026-04-20',
        opponent: 'E2E Opp',
        'player-statistics': [
          { 'player-id': playerId, 'team-id': teamId, goals: 1, assists: 0, 'yellow-cards': 0, 'red-cards': 0, 'minutes-played': 90 },
        ],
      });
      expect(mRes.status(), await mRes.text()).toBe(201);

      const agg = await waitForPlayerAggregatedStats(request, token, playerId, 20_000);
      expect(agg, 'aggregated-stats should appear after async update-all-player-stats (≤20s)').not.toBeNull();
    } finally {
      await saveCoverage(null, testInfo);
    }
  });
});
