/**
 * Inventory smoke: hit every API route once (@routes).
 * Requires app on :3000 + db:seed-smoke (admin/admin).
 */
const { test, expect } = require('@playwright/test');
const {
  getAdminToken,
  apiJson,
  saveCoverage,
  activateChampionshipSeason,
  getGalaticosTeamId,
  setupTwoPlayersForMerge,
} = require('./_helpers');
const {
  API_PUBLIC_GETS,
  API_PUBLIC_PARAM_GETS,
  API_AUTH_GETS,
} = require('./_route-inventory');

/** @type {{ playerId: string, championshipId: string, seasonId: string, matchId: string, teamId: string } | null} */
let ids = null;
/** @type {string | null} */
let token = null;

async function findChampionshipWithSeason(request, authToken) {
  const { response: cRes, body: cBody } = await apiJson(request, authToken, 'GET', '/api/championships');
  expect(cRes.ok()).toBeTruthy();
  const championships = Array.isArray(cBody?.data) ? cBody.data : [];
  for (const champ of championships) {
    const cid = String(champ._id);
    const { response: sRes, body: sBody } = await apiJson(
      request,
      authToken,
      'GET',
      `/api/championships/${cid}/seasons`
    );
    if (!sRes.ok()) continue;
    const seasons = Array.isArray(sBody?.data) ? sBody.data : [];
    if (seasons.length > 0) {
      return { championshipId: cid, seasonId: String(seasons[0]._id) };
    }
  }
  // Smoke champ may lack seasons collection docs — create one for inventory.
  const unique = Date.now();
  const { response: createRes, body: createBody } = await apiJson(
    request,
    authToken,
    'POST',
    '/api/championships',
    { name: `E2E Routes Seed Champ ${unique}`, season: '2026', 'titles-count': 0, status: 'active' }
  );
  expect(createRes.ok(), JSON.stringify(createBody)).toBeTruthy();
  const championshipId = String(createBody?.data?._id);
  const seasonId = await activateChampionshipSeason(request, authToken, championshipId);
  expect(seasonId, 'created championship should have a season').toBeTruthy();
  return { championshipId, seasonId: String(seasonId) };
}

async function resolveSeedIds(request, authToken) {
  const { response: pRes, body: pBody } = await apiJson(request, authToken, 'GET', '/api/players');
  expect(pRes.ok(), JSON.stringify(pBody)).toBeTruthy();
  const players = Array.isArray(pBody?.data) ? pBody.data : [];
  expect(players.length, 'Need seeded players (db:seed-smoke)').toBeGreaterThan(0);
  const playerId = String(players[0]._id);

  const { championshipId, seasonId } = await findChampionshipWithSeason(request, authToken);

  const { response: mRes, body: mBody } = await apiJson(request, authToken, 'GET', '/api/matches');
  expect(mRes.ok()).toBeTruthy();
  const matches = Array.isArray(mBody?.data) ? mBody.data : [];
  expect(matches.length, 'Need seeded matches').toBeGreaterThan(0);
  const matchId = String(matches[0]._id);

  const teamId = await getGalaticosTeamId(request, authToken);

  return { playerId, championshipId, seasonId, matchId, teamId };
}

async function assertJsonOk(response, body, expectedStatus = 200) {
  expect(response.status(), typeof body === 'object' ? JSON.stringify(body) : await response.text()).toBe(
    expectedStatus
  );
  expect(response.status()).toBeLessThan(500);
  expect(body.success).toBe(true);
  expect(body).toHaveProperty('data');
}

function expectAuthOrBypass(status) {
  // wrap-auth returns 401 unless DISABLE_AUTH lets the request through.
  expect([200, 201, 202, 401]).toContain(status);
  expect(status).toBeLessThan(500);
}

test.describe('API route inventory', { tag: '@routes' }, () => {
  test.beforeAll(async ({ request }) => {
    token = await getAdminToken(request);
    expect(token, 'admin token (run db:seed-smoke)').toBeTruthy();
    ids = await resolveSeedIds(request, token);
  });

  test('POST /api/auth/login', async ({ request }, testInfo) => {
    try {
      const response = await request.post('/api/auth/login', {
        headers: { 'Content-Type': 'application/json' },
        data: { username: 'admin', password: 'admin' },
      });
      const body = await response.json();
      await assertJsonOk(response, body, 200);
      expect(body.data?.token || body.data?.['token']).toBeTruthy();
    } finally {
      await saveCoverage(null, testInfo);
    }
  });

  test('POST /api/auth/logout', async ({ request }, testInfo) => {
    try {
      const response = await request.post('/api/auth/logout', {
        headers: { 'Content-Type': 'application/json' },
        data: {},
      });
      const body = await response.json().catch(() => ({}));
      expect(response.status()).toBe(200);
      expect(response.status()).toBeLessThan(500);
      if (body && typeof body.success === 'boolean') {
        expect(body.success).toBe(true);
      }
    } finally {
      await saveCoverage(null, testInfo);
    }
  });

  for (const route of API_PUBLIC_GETS) {
    test(`${route.method} ${route.path}`, async ({ request }, testInfo) => {
      try {
        const response = await request.fetch(route.path, { method: route.method });
        expect(response.status()).toBeLessThan(500);
        if (route.kind === 'health') {
          expect(response.status()).toBe(200);
          const body = await response.json();
          expect(body.status).toBe('ok');
        } else {
          const body = await response.json();
          await assertJsonOk(response, body, route.success ?? 200);
        }
      } finally {
        await saveCoverage(null, testInfo);
      }
    });
  }

  for (const route of API_PUBLIC_PARAM_GETS) {
    test(route.name, async ({ request }, testInfo) => {
      try {
        expect(ids).toBeTruthy();
        const path = route.resolve(ids);
        const response = await request.get(path);
        const body = await response.json().catch(() => ({}));
        await assertJsonOk(response, body, 200);
      } finally {
        await saveCoverage(null, testInfo);
      }
    });
  }

  for (const route of API_AUTH_GETS) {
    const label = route.name || `${route.method || 'GET'} ${route.path}`;
    test(`${label} (auth)`, async ({ request }, testInfo) => {
      try {
        expect(ids).toBeTruthy();
        expect(token).toBeTruthy();
        const path = route.resolve ? route.resolve(ids) : route.path;
        const response = await request.get(path, {
          headers: { Authorization: `Bearer ${token}` },
        });
        expect(response.status()).toBeLessThan(500);
        expect(response.status()).toBe(200);
        if (route.kind === 'csv') {
          const ct = (response.headers()['content-type'] || '').toLowerCase();
          const text = await response.text();
          expect(ct.includes('csv') || ct.includes('text') || text.length > 0).toBeTruthy();
        } else {
          const body = await response.json();
          expect(body.success).toBe(true);
          expect(body).toHaveProperty('data');
        }
      } finally {
        await saveCoverage(null, testInfo);
      }
    });

    test(`${label} (no token)`, async ({ request }, testInfo) => {
      try {
        expect(ids).toBeTruthy();
        const path = route.resolve ? route.resolve(ids) : route.path;
        const response = await request.get(path);
        expectAuthOrBypass(response.status());
        if (response.status() === 401) {
          const body = await response.json().catch(() => ({}));
          expect(body.success === false || body.error).toBeTruthy();
        }
      } finally {
        await saveCoverage(null, testInfo);
      }
    });
  }

  test('writes: players CRUD + merge', async ({ request }, testInfo) => {
    const unique = Date.now();
    let playerId = null;
    try {
      expect(token).toBeTruthy();
      const teamId = await getGalaticosTeamId(request, token);

      const { response: cRes, body: cBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E Routes Player ${unique}`,
        position: 'Meia',
        'team-id': teamId,
      });
      expect(cRes.status()).toBe(201);
      expect(cBody.success).toBe(true);
      playerId = cBody?.data?._id;
      expect(playerId).toBeTruthy();

      const { response: uRes, body: uBody } = await apiJson(
        request,
        token,
        'PUT',
        `/api/players/${playerId}`,
        { nickname: `routes-${unique}` }
      );
      expect(uRes.status()).toBe(200);
      expect(uBody.success).toBe(true);

      const { response: mcRes, body: mcBody } = await apiJson(
        request,
        token,
        'GET',
        `/api/players/${playerId}/merge-candidates`
      );
      expect(mcRes.status()).toBe(200);
      expect(mcBody.success).toBe(true);

      const merge = await setupTwoPlayersForMerge(request);
      const { response: mergeRes, body: mergeBody } = await apiJson(
        request,
        token,
        'POST',
        '/api/players/merge',
        {
          'master-id': merge.playerA,
          'merged-ids': [merge.playerB],
          'field-selections': { name: 'master' },
        }
      );
      expect(mergeRes.status()).toBeLessThan(500);
      // Merge may succeed (200) or reject validation (400); never 5xx.
      expect([200, 400]).toContain(mergeRes.status());
      if (mergeRes.status() === 200) {
        expect(mergeBody.success).toBe(true);
      }

      const { response: dRes, body: dBody } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/players/${playerId}`
      );
      expect(dRes.status()).toBe(200);
      expect(dBody.success).toBe(true);
      playerId = null;
    } finally {
      if (playerId && token) {
        await apiJson(request, token, 'DELETE', `/api/players/${playerId}`).catch(() => {});
      }
      await saveCoverage(null, testInfo);
    }
  });

  test('writes: championships + seasons lifecycle', async ({ request }, testInfo) => {
    const unique = Date.now();
    let championshipId = null;
    let seasonId = null;
    let playerId = null;
    try {
      expect(token).toBeTruthy();
      const teamId = await getGalaticosTeamId(request, token);

      const { response: cRes, body: cBody } = await apiJson(request, token, 'POST', '/api/championships', {
        name: `E2E Routes Champ ${unique}`,
        season: '2026',
        'titles-count': 0,
        status: 'active',
      });
      expect(cRes.status()).toBe(201);
      expect(cBody.success).toBe(true);
      championshipId = cBody?.data?._id;
      expect(championshipId).toBeTruthy();

      const { response: uRes, body: uBody } = await apiJson(
        request,
        token,
        'PUT',
        `/api/championships/${championshipId}`,
        { notes: `routes-${unique}` }
      );
      expect(uRes.status()).toBe(200);
      expect(uBody.success).toBe(true);

      seasonId = await activateChampionshipSeason(request, token, championshipId);
      expect(seasonId).toBeTruthy();

      const { response: sCreate, body: sCreateBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/seasons`,
        { season: `Extra-${unique}`, status: 'planned' }
      );
      expect(sCreate.status()).toBeLessThan(500);
      // Creating extra season may be 201; keep id if created for cleanup.
      let extraSeasonId = null;
      if (sCreate.status() === 201) {
        expect(sCreateBody.success).toBe(true);
        extraSeasonId = sCreateBody?.data?._id;
      }

      if (extraSeasonId) {
        const { response: sPut, body: sPutBody } = await apiJson(
          request,
          token,
          'PUT',
          `/api/seasons/${extraSeasonId}`,
          { notes: 'routes' }
        );
        expect(sPut.status()).toBeLessThan(500);
        if (sPut.status() === 200) expect(sPutBody.success).toBe(true);
      }

      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E Routes ChampP ${unique}`,
        position: 'Atacante',
        'team-id': teamId,
      });
      expect(pRes.status()).toBe(201);
      playerId = pBody?.data?._id;

      const { response: enRes, body: enBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/enroll/${playerId}`,
        {}
      );
      expect(enRes.status()).toBe(200);
      expect(enBody.success).toBe(true);

      const { response: senRes, body: senBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/seasons/${seasonId}/enroll/${playerId}`,
        {}
      );
      expect(senRes.status()).toBeLessThan(500);
      if (senRes.status() === 200) expect(senBody.success).toBe(true);

      const { response: sunRes } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/seasons/${seasonId}/unenroll/${playerId}`
      );
      expect(sunRes.status()).toBeLessThan(500);

      // Re-enroll for finalize paths
      await apiJson(request, token, 'POST', `/api/seasons/${seasonId}/enroll/${playerId}`, {});

      const { response: sfRes, body: sfBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/seasons/${seasonId}/finalize`,
        { 'winner-player-ids': [playerId], 'titles-award-count': 0 }
      );
      expect(sfRes.status()).toBeLessThan(500);
      if (sfRes.status() === 200) expect(sfBody.success).toBe(true);

      const { response: unRes, body: unBody } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/championships/${championshipId}/unenroll/${playerId}`
      );
      expect(unRes.status()).toBeLessThan(500);
      if (unRes.status() === 200) expect(unBody.success).toBe(true);

      await apiJson(request, token, 'POST', `/api/championships/${championshipId}/enroll/${playerId}`, {});

      const { response: cfRes, body: cfBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/finalize`,
        { 'winner-player-ids': [playerId], 'titles-award-count': 0 }
      );
      expect(cfRes.status()).toBeLessThan(500);
      if (cfRes.status() === 200) expect(cfBody.success).toBe(true);

      if (extraSeasonId) {
        const { response: sdRes } = await apiJson(
          request,
          token,
          'DELETE',
          `/api/seasons/${extraSeasonId}`
        );
        expect(sdRes.status()).toBeLessThan(500);
      }

      const { response: dRes, body: dBody } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/championships/${championshipId}`
      );
      // May be blocked if referential integrity (matches) — still not 5xx
      expect(dRes.status()).toBeLessThan(500);
      if (dRes.status() === 200) expect(dBody.success).toBe(true);
      if (dRes.status() === 200) championshipId = null;

      if (playerId) {
        await apiJson(request, token, 'DELETE', `/api/players/${playerId}`);
        playerId = null;
      }
    } finally {
      if (championshipId && token) {
        await apiJson(request, token, 'DELETE', `/api/championships/${championshipId}`).catch(() => {});
      }
      if (playerId && token) {
        await apiJson(request, token, 'DELETE', `/api/players/${playerId}`).catch(() => {});
      }
      await saveCoverage(null, testInfo);
    }
  });

  test('writes: matches CRUD', async ({ request }, testInfo) => {
    const unique = Date.now();
    let championshipId = null;
    let matchId = null;
    let playerId = null;
    let teamId = null;
    try {
      expect(token).toBeTruthy();

      const { response: cRes, body: cBody } = await apiJson(request, token, 'POST', '/api/championships', {
        name: `E2E Routes MatchChamp ${unique}`,
        season: '2026',
        'titles-count': 0,
        status: 'active',
      });
      expect(cRes.status()).toBe(201);
      championshipId = cBody?.data?._id;
      const seasonId = await activateChampionshipSeason(request, token, championshipId);
      expect(seasonId).toBeTruthy();

      teamId = await getGalaticosTeamId(request, token);
      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E Routes MatchP ${unique}`,
        position: 'Atacante',
        'team-id': teamId,
      });
      expect(pRes.status()).toBe(201);
      playerId = pBody?.data?._id;

      await apiJson(request, token, 'POST', `/api/championships/${championshipId}/enroll/${playerId}`, {});

      const { response: mRes, body: mBody } = await apiJson(request, token, 'POST', '/api/matches', {
        'championship-id': championshipId,
        'home-team-id': teamId,
        date: '2026-05-01',
        opponent: `Opp Routes ${unique}`,
        'player-statistics': [
          {
            'player-id': playerId,
            'team-id': teamId,
            goals: 1,
            assists: 0,
            'yellow-cards': 0,
            'red-cards': 0,
            'minutes-played': 90,
          },
        ],
      });
      expect(mRes.status()).toBe(201);
      expect(mBody.success).toBe(true);
      matchId = mBody?.data?._id;
      expect(matchId).toBeTruthy();

      const { response: uRes, body: uBody } = await apiJson(
        request,
        token,
        'PUT',
        `/api/matches/${matchId}`,
        { notes: `routes-${unique}` }
      );
      expect(uRes.status()).toBe(200);
      expect(uBody.success).toBe(true);

      const { response: dRes, body: dBody } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/matches/${matchId}`
      );
      expect(dRes.status()).toBe(200);
      expect(dBody.success).toBe(true);
      matchId = null;

      await apiJson(request, token, 'DELETE', `/api/championships/${championshipId}`);
      championshipId = null;
      await apiJson(request, token, 'DELETE', `/api/players/${playerId}`);
      playerId = null;
    } finally {
      if (matchId && token) {
        await apiJson(request, token, 'DELETE', `/api/matches/${matchId}`).catch(() => {});
      }
      if (championshipId && token) {
        await apiJson(request, token, 'DELETE', `/api/championships/${championshipId}`).catch(() => {});
      }
      if (playerId && token) {
        await apiJson(request, token, 'DELETE', `/api/players/${playerId}`).catch(() => {});
      }
      await saveCoverage(null, testInfo);
    }
  });

  test('writes: teams CRUD + roster', async ({ request }, testInfo) => {
    const unique = Date.now();
    let teamId = null;
    let playerId = null;
    try {
      expect(token).toBeTruthy();

      const { response: tRes, body: tBody } = await apiJson(request, token, 'POST', '/api/teams', {
        name: `E2E Routes Team ${unique}`,
        abbreviation: 'ERT',
      });
      expect(tRes.status()).toBe(201);
      expect(tBody.success).toBe(true);
      teamId = tBody?.data?._id;

      const { response: uRes, body: uBody } = await apiJson(
        request,
        token,
        'PUT',
        `/api/teams/${teamId}`,
        { notes: `routes-${unique}` }
      );
      expect(uRes.status()).toBe(200);
      expect(uBody.success).toBe(true);

      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E Routes TeamP ${unique}`,
        position: 'Goleiro',
      });
      expect(pRes.status()).toBe(201);
      playerId = pBody?.data?._id;

      const { response: addRes, body: addBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/teams/${teamId}/players/${playerId}`,
        {}
      );
      expect(addRes.status()).toBe(200);
      expect(addBody.success).toBe(true);

      const { response: remRes, body: remBody } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/teams/${teamId}/players/${playerId}`
      );
      expect(remRes.status()).toBe(200);
      expect(remBody.success).toBe(true);

      await apiJson(request, token, 'DELETE', `/api/players/${playerId}`);
      playerId = null;

      const { response: dRes, body: dBody } = await apiJson(
        request,
        token,
        'DELETE',
        `/api/teams/${teamId}`
      );
      expect(dRes.status()).toBe(200);
      expect(dBody.success).toBe(true);
      teamId = null;
    } finally {
      if (playerId && token) {
        await apiJson(request, token, 'DELETE', `/api/players/${playerId}`).catch(() => {});
      }
      if (teamId && token) {
        await apiJson(request, token, 'DELETE', `/api/teams/${teamId}`).catch(() => {});
      }
      await saveCoverage(null, testInfo);
    }
  });

  test('writes: aggregations reconcile + jobs', async ({ request }, testInfo) => {
    try {
      expect(token).toBeTruthy();
      expect(ids).toBeTruthy();

      const { response: rRes, body: rBody } = await apiJson(
        request,
        token,
        'POST',
        '/api/aggregations/reconcile',
        {}
      );
      expect([200, 202]).toContain(rRes.status());
      expect(rBody.success).toBe(true);

      const { response: prRes, body: prBody } = await apiJson(
        request,
        token,
        'POST',
        `/api/aggregations/players/${ids.playerId}/reconcile`,
        {}
      );
      expect(prRes.status()).toBe(200);
      expect(prBody.success).toBe(true);

      const { response: jRes, body: jBody } = await apiJson(
        request,
        token,
        'GET',
        '/api/aggregations/player-stats-jobs'
      );
      expect(jRes.status()).toBe(200);
      expect(jBody.success).toBe(true);
    } finally {
      await saveCoverage(null, testInfo);
    }
  });
});
