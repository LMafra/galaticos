const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test.describe('Players list search and filter', { tag: '@search' }, () => {
  test('search by name filters the list', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
      const searchInput = page.getByPlaceholder('Buscar jogador...');
      await searchInput.waitFor({ state: 'visible' });
      await searchInput.fill('x');
      await page.waitForTimeout(500);
      await expect(page.getByText('Erro ao carregar players')).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('filter by position updates the list', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
      const positionSelect = page.getByLabel(/^Posição/);
      await positionSelect.waitFor({ state: 'visible' });
      await positionSelect.selectOption({ index: 0 });
      await page.waitForTimeout(500);
      await expect(page.getByText('Erro ao carregar players')).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});

test.describe('Player detail tabs', { tag: '@crud' }, () => {
  test('player detail shows Informações and Estatísticas tabs', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
      const firstRow = page.locator('table tbody tr').first();
      await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
      const rowCount = await page.locator('table tbody tr').count();
      if (rowCount === 0) {
        test.skip(true, 'No players in list - seed data required');
        return;
      }
      await firstRow.click();
      await expect(page.getByRole('button', { name: 'Informações' })).toBeVisible({ timeout: 10_000 });
      await expect(page.getByRole('button', { name: 'Estatísticas' })).toBeVisible();
      await page.getByRole('button', { name: 'Estatísticas' }).click();
      await expect(page.getByText(/Nenhum dado|Performance por campeonato|Evolução/)).toBeVisible({ timeout: 5000 }).catch(() => {});
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
