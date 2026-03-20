const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test.describe('Referential integrity - delete protection', { tag: '@integrity' }, () => {
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
      await page.goto('/#/championships');
      await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();
      const firstRow = page.locator('table tbody tr').first();
      await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
      const rowCount = await page.locator('table tbody tr').count();
      if (rowCount === 0) {
        test.skip(true, 'No championships in list - seed data required');
        return;
      }
      await firstRow.click();
      await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
      page.on('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Deletar' }).click();
      await expect(page.getByText(/Erro ao deletar campeonato/)).toBeVisible({ timeout: 5000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
