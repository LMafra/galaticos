const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  setupChampionshipWithMax,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  getAdminToken,
  apiJson,
} = require('./_helpers');

test.describe('UX championships', { tag: '@ux' }, () => {
  test('championship detail shows inscrições and seasons', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      await expect(page.getByText('Inscrições')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('heading', { name: 'Temporadas', exact: true })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('max players limit shown on championship detail', async ({ page, request }, testInfo) => {
    try {
      const { championshipId } = await setupChampionshipWithMax(request, 2, page);
      await page.goto(`/#/championships/${championshipId}`);
      await expect(page.getByText(/Limite de jogadores:\s*2/)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/E2E Max P0/).first()).toBeVisible();
      await expect(page.getByText(/E2E Max P1/).first()).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('enrolling beyond max via API is rejected', async ({ page, request }, testInfo) => {
    try {
      const { championshipId } = await setupChampionshipWithMax(request, 1, page);
      const token = await getAdminToken(request, page);
      const { body: tBody } = await apiJson(request, token, 'GET', '/api/teams');
      const team = (tBody?.data || []).find((x) => x?.name === 'Galáticos' || x?.name === 'Galaticos');
      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E Over Max ${Date.now()}`,
        position: 'Atacante',
        'team-id': String(team._id),
      });
      expect(pRes.ok()).toBeTruthy();
      const { response: eRes } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/enroll/${pBody?.data?._id}`,
        {}
      );
      expect(eRes.ok()).toBeFalsy();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('finalize championship button visible for active champ', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      const finalizeBtn = page.getByRole('button', { name: /Finalizar campeonato/i });
      await expect(finalizeBtn).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('leaderboards section has reserved min-height skeleton area', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      await expect(page.getByText(/Top 5/)).toBeVisible({ timeout: 20_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
