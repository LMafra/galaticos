const { test, expect } = require('@playwright/test');
const { loginAsAdmin, saveCoverage } = require('./_helpers');

test('dashboard loads and shows overview stats', async ({ page }, testInfo) => {
  try {
    await loginAsAdmin(page);

    await expect(page.getByText('Visão geral')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    const body = page.locator('body');
    await expect(body).toContainText('Dashboard');

    const hasStats =
      (await body.getByText('Jogadores').count()) > 0 ||
      (await body.getByText('Partidas').count()) > 0 ||
      (await body.getByText('Gols').count()) > 0 ||
      (await body.getByText('Campeonatos').count()) > 0;
    expect(hasStats).toBeTruthy();
  } finally {
    await saveCoverage(page, testInfo);
  }
});
