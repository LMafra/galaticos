const { test, expect } = require('@playwright/test');
const { saveCoverage, pageHeading } = require('./_helpers');

test.describe('UX foundation and UI kit', { tag: '@ux' }, () => {
  test('ui-lab route renders button variants', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/ui-lab');
      await expect(page.getByRole('heading', { name: 'UI Lab' })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: 'Primário', exact: true })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('stats tables use tabular-nums on numeric cells', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(page).toHaveURL(/\/#\/stats/);
      await expect(page.getByRole('button', { name: 'Buscar' })).toBeVisible({ timeout: 15_000 });
      await expect(page.locator('.tabular-nums').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('championship list shows status badge', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/championships');
      await expect(pageHeading(page, 'Campeonatos')).toBeVisible();
      await expect(
        page.locator('main span').filter({ hasText: /Ativo|Inativo|Finalizado/i }).first()
      ).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('dashboard uses min-height loading wrapper', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.locator('[class*="min-h"]').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
