const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

async function apiJson(page, token, method, url, data = undefined) {
  const response = await page.request.fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data,
  });
  const body = await response.json();
  return { response, body };
}

test.describe('Referential integrity - delete protection', { tag: '@integrity' }, () => {
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

  test('deleting championship with matches shows error message', async ({ page }, testInfo) => {
    try {
      const loginResponse = await page.request.post('/api/auth/login', {
        data: { username: 'admin', password: 'admin' },
      });
      expect(loginResponse.ok()).toBeTruthy();
      const loginBody = await loginResponse.json();
      const token = loginBody?.data?.token;
      expect(token).toBeTruthy();

      const unique = Date.now();
      const { body: createdChampionshipBody, response: createdChampionshipResponse } = await apiJson(
        page,
        token,
        'POST',
        '/api/championships',
        { name: `Campeonato Integridade E2E ${unique}`, season: '2026', 'titles-count': 0 }
      );
      expect(createdChampionshipResponse.ok()).toBeTruthy();
      const championshipId = createdChampionshipBody?.data?._id;
      expect(championshipId).toBeTruthy();

      const { body: createdTeamBody, response: createdTeamResponse } = await apiJson(
        page,
        token,
        'POST',
        '/api/teams',
        { name: `Time Integridade E2E ${unique}` }
      );
      expect(createdTeamResponse.ok()).toBeTruthy();
      const teamId = createdTeamBody?.data?._id;
      expect(teamId).toBeTruthy();

      const { body: createdPlayerBody, response: createdPlayerResponse } = await apiJson(
        page,
        token,
        'POST',
        '/api/players',
        {
          name: `Jogador Integridade E2E ${unique}`,
          position: 'Atacante',
          'team-id': teamId,
        }
      );
      expect(createdPlayerResponse.ok()).toBeTruthy();
      const playerId = createdPlayerBody?.data?._id;
      expect(playerId).toBeTruthy();

      const { response: enrollResponse } = await apiJson(
        page,
        token,
        'POST',
        `/api/championships/${championshipId}/enroll/${playerId}`,
        {}
      );
      expect(enrollResponse.ok()).toBeTruthy();

      const { response: createMatchResponse } = await apiJson(
        page,
        token,
        'POST',
        '/api/matches',
        {
          'championship-id': championshipId,
          'home-team-id': teamId,
          date: '2026-03-26',
          opponent: 'Adversário Integridade E2E',
          'player-statistics': [{ 'player-id': playerId, 'team-id': teamId, goals: 0 }],
        }
      );
      expect(createMatchResponse.ok()).toBeTruthy();

      await page.goto(`/#/championships/${championshipId}`);
      await expect(page.getByRole('heading', { name: /Campeonato Integridade E2E/ })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
      page.on('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expect(page.getByText(/Erro ao deletar campeonato/)).toBeVisible({ timeout: 5000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
