const { test, expect } = require('@playwright/test');
const { loginAsAdmin, saveCoverage } = require('./_helpers');

test('navigate between main pages via header', async ({ page }, testInfo) => {
  try {
    await loginAsAdmin(page);

    await page.getByRole('link', { name: 'Jogadores' }).click();
    await expect(page.getByRole('heading', { name: 'Jogadores' })).toBeVisible();

    await page.getByRole('link', { name: 'Partidas' }).click();
    await expect(page.getByRole('heading', { name: 'Partidas' })).toBeVisible();

    await page.getByRole('link', { name: 'Campeonatos' }).click();
    await expect(page.getByRole('heading', { name: 'Campeonatos' })).toBeVisible();

    await page.getByRole('link', { name: 'Dashboard' }).click();
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});


