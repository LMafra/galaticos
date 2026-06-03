const { test, expect } = require('@playwright/test');
const { getAdminToken, saveCoverage, apiJson, activateChampionshipSeason } = require('./_helpers');

test.describe('Referential integrity - delete protection', { tag: '@integrity' }, () => {
  // API setup (championship + team + player + match) is sensitive to parallel DB load.
  test.describe.configure({ mode: 'serial' });
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/dashboard');
  });

  test('deleting team with players shows error message', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
      const rows = page.locator('table tbody tr');
      await rows.first().waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
      const rowCount = await rows.count();
      if (rowCount === 0) {
        test.skip(true, 'No teams in list - seed data required');
        return;
      }
      const galaticosRow = page.locator('table tbody tr').filter({ hasText: 'Galáticos' }).first();
      const teamRow = (await galaticosRow.count()) > 0 ? galaticosRow : rows.first();
      await teamRow.click();
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
      page.on('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Deletar' }).click();
      const errorShown = page.getByText(/Erro ao deletar time/);
      const backToList = page.getByRole('heading', { name: 'Times', level: 1 });
      await expect(errorShown.or(backToList)).toBeVisible({ timeout: 8000 });
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
      page.on('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expect(page.getByText(/Erro ao deletar campeonato/)).toBeVisible({ timeout: 5000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
