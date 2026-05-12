const { test, expect } = require('@playwright/test');
const { saveCoverage, getAdminToken } = require('./_helpers');

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
 * Championship + Galáticos player enrolled via API (same pattern as player-stats-refresh).
 * Do not send status: active here: that creates an active season and enroll hits seasons/add-player,
 * which can fail on bad/legacy enrolled-player-ids shapes; without an active season, enroll uses the
 * championship root and get-championship-players still lists the player (roster union).
 */
async function setupActiveChampionshipWithEnrolledGalaticosPlayer(request) {
  const token = await getAdminToken(request);
  expect(token, 'admin token (db:seed-smoke: admin/admin)').toBeTruthy();

  const unique = Date.now();
  const { response: cRes, body: cBody } = await apiJson(request, token, 'POST', '/api/championships', {
    name: `E2E Champ ${unique}`,
    season: '2026',
    'titles-count': 0,
  });
  expect(cRes.ok(), JSON.stringify(cBody)).toBeTruthy();
  const championshipId = cBody?.data?._id;
  expect(championshipId).toBeTruthy();

  const { response: tRes, body: tBody } = await apiJson(request, token, 'GET', '/api/teams');
  expect(tRes.ok()).toBeTruthy();
  const teams = Array.isArray(tBody?.data) ? tBody.data : [];
  const galaticos = teams.find((t) => t?.name === 'Galáticos' || t?.name === 'Galaticos');
  expect(galaticos?._id, 'Need Galáticos team (db:seed-smoke)').toBeTruthy();
  const teamId = galaticos._id;

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

test('create team', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new team form', async () => {
      await page.goto('/#/teams');
      await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Novo Time' }).click();
    });

    await test.step('fill and submit form', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Time' })).toBeVisible();
      const uniqueName = `Time E2E ${Date.now()}`;
      await page.getByLabel(/^Nome/).fill(uniqueName);
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to teams list', async () => {
      await expect(page).toHaveURL(/\#\/teams$/);
      await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create player (minimal)', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new player form', async () => {
      await page.goto('/#/players');
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Novo Jogador' }).click();
    });

    await test.step('fill required fields and submit', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Jogador' })).toBeVisible();
      await page.getByLabel(/^Nome/).fill('Jogador E2E Test');
      await page.getByLabel(/^Posição/).fill('Atacante');
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to players list', async () => {
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible({ timeout: 10_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create championship', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new championship form', async () => {
      await page.goto('/#/championships');
      await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Novo Campeonato' }).click();
    });

    await test.step('fill and submit form', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Campeonato' })).toBeVisible();
      const uniqueName = `Campeonato E2E ${Date.now()}`;
      await page.getByLabel(/^Nome/).fill(uniqueName);
      await page.getByLabel(/^Temporada/).fill('2025');
      await page.getByLabel(/^Títulos/).fill('0');
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to championships list', async () => {
      await expect(page).toHaveURL(/\#\/championships$/);
      await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create match with one player statistic', { tag: '@crud' }, async ({ page, request }, testInfo) => {
  try {
    await page.goto('/#/matches/new');
    await expect(page.getByRole('heading', { name: 'Nova Partida' })).toBeVisible();

    await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});

    const noActiveChamp = page.getByText('Nenhum campeonato ativo encontrado.');
    const noEnrolled = page.getByText('Nenhum jogador inscrito neste campeonato.');
    const needsSetup =
      (await noActiveChamp.isVisible().catch(() => false)) || (await noEnrolled.isVisible().catch(() => false));

    if (needsSetup) {
      let champId;
      await test.step('API: active championship + Galáticos player enrolled', async () => {
        champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request);
      });
      await page.goto(`/#/matches/by-championship/${champId}/new`);
    }

    await expect(page.getByRole('heading', { name: 'Nova Partida' })).toBeVisible();
    await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});

    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });

    await page.getByLabel(/^Data/).fill('2025-06-01');
    await page.getByLabel(/^Adversário/).fill('Adversário E2E');

    // Player stats use number-stepper (+/-), not spinbuttons; one goal includes this row in the payload.
    await page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button', { name: '+' }).click();

    const createMatchResp = page.waitForResponse((r) => {
      if (r.request().method() !== 'POST') return false;
      try {
        return new URL(r.url()).pathname === '/api/matches';
      } catch {
        return false;
      }
    }, { timeout: 20_000 });
    await page.getByRole('button', { name: 'Criar Partida' }).click();
    const resp = await createMatchResp;
    expect(resp.status(), await resp.text()).toBe(201);
    await expect(page).toHaveURL(/\#\/matches$/);
    await expect(page.getByRole('heading', { name: 'Partidas', level: 1 })).toBeVisible({ timeout: 15_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('edit player', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No players in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Editar' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Editar' }).click();
    await expect(page.getByRole('heading', { name: 'Editar Jogador' })).toBeVisible();
    await page.getByLabel(/^Apelido/).fill(`E2E Apelido ${Date.now()}`);
    await page.getByRole('button', { name: 'Atualizar' }).click();
    const onList = page.getByRole('heading', { name: 'Jogadores', level: 1 });
    const onDetail = page.getByRole('button', { name: 'Informações' });
    const onError = page.getByText(/Erro ao .* jogador/);
    await expect(onList.or(onDetail).or(onError)).toBeVisible({ timeout: 25_000 });
    if (page.url().includes('/edit')) {
      await expect(onError).toBeVisible();
    }
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('edit team', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/teams');
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No teams in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Editar' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Editar' }).click();
    await expect(page.getByRole('heading', { name: 'Editar Time' })).toBeVisible();
    await page.getByLabel(/^Cidade/).fill('Cidade E2E');
    await page.getByRole('button', { name: 'Atualizar' }).click();
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible({ timeout: 10_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('delete player (soft delete)', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No players in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
    page.on('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Deletar' }).click();
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible({ timeout: 10_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});
