const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  expectPageTitle,
  expectUndoToast,
  clickUndo,
  toastRegion,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  setupChampionshipWithMax,
  apiJson,
  getAdminToken,
} = require('./_helpers');

test.describe('UX undo deletes', { tag: ['@ux', '@ux-slow'] }, () => {
  test('delete match with undo restores row', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const token = await getAdminToken(request, page);
      const teamId = await (async () => {
        const { body } = await apiJson(request, token, 'GET', '/api/teams');
        const t = (body?.data || []).find((x) => x?.name === 'Galáticos' || x?.name === 'Galaticos');
        return String(t._id);
      })();
      const { body: pBody } = await apiJson(request, token, 'GET', `/api/championships/${champId}/players`);
      const playerId = String(pBody?.data?.[0]?._id);
      const opponent = `UX Undo Match ${Date.now()}`;
      await apiJson(request, token, 'POST', '/api/matches', {
        'championship-id': champId,
        'home-team-id': teamId,
        date: '2026-04-01',
        opponent,
        'player-statistics': [{ 'player-id': playerId, 'team-id': teamId, goals: 1 }],
      });

      await page.goto(`/#/matches/championship/${champId}`);
      const row = page.getByText(opponent).first();
      await expect(row).toBeVisible({ timeout: 15_000 });
      const deleteBtn = page.getByRole('button', { name: 'Remover partida' }).first();
      await deleteBtn.click();
      await expectUndoToast(page, /Partida removido/i);
      await expect(page.getByText(opponent)).toHaveCount(0, { timeout: 5000 });
      await clickUndo(page);
      await expect(page.getByText(opponent)).toBeVisible({ timeout: 5000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('delete team with undo returns to detail', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      const unique = Date.now();
      const { body } = await apiJson(request, token, 'POST', '/api/teams', {
        name: `UX Undo Team ${unique}`,
      });
      const teamId = body?.data?._id;
      await page.goto(`/#/teams/${teamId}`);
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 10_000 });
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expectUndoToast(page, /Time removido/i);
      await expect(page).toHaveURL(/\/#\/teams$/);
      await clickUndo(page);
      await expect(page).toHaveURL(new RegExp(`/#/teams/${teamId}`));
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('delete player with undo', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      const unique = Date.now();
      const { body: tBody } = await apiJson(request, token, 'GET', '/api/teams');
      const team = (tBody?.data || []).find((x) => x?.name === 'Galáticos' || x?.name === 'Galaticos');
      const { body } = await apiJson(request, token, 'POST', '/api/players', {
        name: `UX Undo Player ${unique}`,
        position: 'Atacante',
        'team-id': String(team._id),
      });
      const playerId = body?.data?._id;
      await page.goto(`/#/players/${playerId}`);
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expectUndoToast(page, /Jogador removido/i);
      await expect(page).toHaveURL(/\/#\/players$/);
      await clickUndo(page);
      await expect(page).toHaveURL(new RegExp(`/#/players/${playerId}`));
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('delete championship with undo', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      const unique = Date.now();
      const { body } = await apiJson(request, token, 'POST', '/api/championships', {
        name: `UX Undo Champ ${unique}`,
        season: '2026',
        status: 'active',
        'titles-count': 0,
      });
      const champId = body?.data?._id;
      await page.goto(`/#/championships/${champId}`);
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expectUndoToast(page, /Campeonato removido/i);
      await expect(page).toHaveURL(/\/#\/championships$/);
      await clickUndo(page);
      await expect(page).toHaveURL(new RegExp(`/#/championships/${champId}`));
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('roster remove with undo restores player', async ({ page, request }, testInfo) => {
    try {
      const { championshipId, playerIds } = await setupChampionshipWithMax(request, 1, page);
      await page.goto(`/#/championships/${championshipId}`);
      const playerLine = page.getByText(/E2E Max P0/).first();
      await expect(playerLine).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: 'Remover' }).first().click();
      await expectUndoToast(page, /removido do elenco/i);
      await clickUndo(page);
      await expect(playerLine).toBeVisible({ timeout: 10_000 });
      expect(playerIds.length).toBeGreaterThan(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('delete commit failure shows persistent error toast', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token).toBeTruthy();
      const unique = Date.now();
      const { body, response } = await apiJson(request, token, 'POST', '/api/teams', {
        name: `UX Undo Fail ${unique}`,
      });
      expect(response.ok(), JSON.stringify(body)).toBeTruthy();
      const teamId = body?.data?._id;
      expect(teamId).toBeTruthy();
      const teamUrl = `/#/teams/${teamId}`;
      await page.goto(teamUrl);
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 10_000 });
      let deleteCalls = 0;
      await page.route('**/api/teams/**', async (route) => {
        if (route.request().method() === 'DELETE') {
          deleteCalls += 1;
          await route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' });
          return;
        }
        await route.continue();
      });
      const deleteResponse = page.waitForResponse(
        (res) => res.request().method() === 'DELETE' && res.url().includes(`/api/teams/${teamId}`),
        { timeout: 20_000 }
      );
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expectUndoToast(page, /Time removido/i);
      await deleteResponse;
      await expect(toastRegion(page).getByText(/Não foi possível concluir a remoção/i)).toBeVisible({
        timeout: 10_000,
      });
      expect(deleteCalls).toBeGreaterThan(0);
      await page.goto(teamUrl);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
