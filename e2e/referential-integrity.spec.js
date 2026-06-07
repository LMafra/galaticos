const { test, expect } = require('@playwright/test');
const {
  getAdminToken,
  saveCoverage,
  apiJson,
  activateChampionshipSeason,
  pageHeading,
  expectPageTitle,
  expectUndoToast,
  toastRegion,
} = require('./_helpers');

test.describe('Referential integrity - delete protection', { tag: '@integrity' }, () => {
  // API setup (championship + team + player + match) is sensitive to parallel DB load.
  test.describe.configure({ mode: 'serial' });
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/dashboard');
  });

  test('deleting team with players shows error message', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token, 'admin token (db:seed-smoke: admin/admin)').toBeTruthy();

      const unique = `${Date.now()}-${testInfo.parallelIndex}-${testInfo.retry}`;
      const { body: createdTeamBody, response: createdTeamResponse } = await apiJson(
        request,
        token,
        'POST',
        '/api/teams',
        { name: `Time Integridade E2E ${unique}` }
      );
      expect(createdTeamResponse.ok(), JSON.stringify(createdTeamBody)).toBeTruthy();
      const teamId = createdTeamBody?.data?._id;
      expect(teamId).toBeTruthy();

      const { response: createdPlayerResponse } = await apiJson(request, token, 'POST', '/api/players', {
        name: `Jogador Integridade E2E ${unique}`,
        position: 'Atacante',
        'team-id': String(teamId),
      });
      expect(createdPlayerResponse.status()).toBe(201);

      await page.goto(`/#/teams/${teamId}`);
      await expect(page.getByRole('heading', { name: new RegExp(`Time Integridade E2E ${unique}`) })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
      const deleteResponse = page.waitForResponse(
        (res) => res.request().method() === 'DELETE' && res.url().includes(`/api/teams/${teamId}`),
        { timeout: 20_000 }
      );
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expectUndoToast(page, /Time removido/i);
      const response = await deleteResponse;
      expect(response.status(), 'team with roster must not delete').toBe(409);
      await expect(
        toastRegion(page).getByText(/Não foi possível concluir a remoção|associated players|Cannot delete team/i)
      ).toBeVisible({ timeout: 10_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('deleting championship with matches shows error message', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(
        token,
        'admin token (storageState from auth.setup, or db:seed-smoke admin/admin via POST /api/auth/login)'
      ).toBeTruthy();

      const unique = `${Date.now()}-${testInfo.parallelIndex}-${testInfo.retry}`;
      const { body: createdChampionshipBody, response: createdChampionshipResponse } = await apiJson(
        request,
        token,
        'POST',
        '/api/championships',
        { name: `Campeonato Integridade E2E ${unique}`, season: '2026', 'titles-count': 0, status: 'active' }
      );
      expect(createdChampionshipResponse.ok(), JSON.stringify(createdChampionshipBody)).toBeTruthy();
      const championshipId = createdChampionshipBody?.data?._id;
      expect(championshipId).toBeTruthy();
      const seasonId = await activateChampionshipSeason(request, token, championshipId);
      expect(seasonId, 'active season required for match create').toBeTruthy();

      const { body: createdTeamBody, response: createdTeamResponse } = await apiJson(
        request,
        token,
        'POST',
        '/api/teams',
        { name: `Time Integridade E2E ${unique}` }
      );
      expect(createdTeamResponse.ok(), JSON.stringify(createdTeamBody)).toBeTruthy();
      const teamId = createdTeamBody?.data?._id;
      expect(teamId).toBeTruthy();

      const { body: createdPlayerBody, response: createdPlayerResponse } = await apiJson(
        request,
        token,
        'POST',
        '/api/players',
        {
          name: `Jogador Integridade E2E ${unique}`,
          position: 'Atacante',
          'team-id': String(teamId),
        }
      );
      expect(createdPlayerResponse.status(), JSON.stringify(createdPlayerBody)).toBe(201);
      const playerId = createdPlayerBody?.data?._id;
      expect(playerId).toBeTruthy();

      const { response: enrollResponse } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/enroll/${playerId}`,
        {}
      );
      expect(enrollResponse.ok()).toBeTruthy();

      const { response: createMatchResponse } = await apiJson(
        request,
        token,
        'POST',
        '/api/matches',
        {
          'championship-id': String(championshipId),
          'home-team-id': String(teamId),
          date: '2026-03-26',
          opponent: 'Adversário Integridade E2E',
          'player-statistics': [
            { 'player-id': String(playerId), 'team-id': String(teamId), goals: 0 },
          ],
        }
      );
      expect(createMatchResponse.ok()).toBeTruthy();

      await page.goto(`/#/championships/${championshipId}`);
      await expect(page.getByRole('heading', { name: new RegExp(`Campeonato Integridade E2E ${unique}`) })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
      const deleteResponse = page.waitForResponse(
        (res) => res.request().method() === 'DELETE' && /\/api\/championships\//.test(res.url()),
        { timeout: 20_000 }
      );
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expectUndoToast(page, /Campeonato removido/i);
      await deleteResponse;
      await expect(
        toastRegion(page).getByText(/Não foi possível concluir a remoção|associated matches|Cannot delete championship/i)
      ).toBeVisible({ timeout: 10_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
