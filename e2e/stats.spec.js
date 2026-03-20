const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test('stats page loads and shows content', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/stats');
    await expect(page.getByRole('heading', { name: 'Estatísticas', level: 1 })).toBeVisible();
    await expect(page.getByText('Erro ao carregar')).toHaveCount(0);
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test.describe('Stats page tabs and search', { tag: '@search' }, () => {
  test('Top Jogadores tab shows Buscar button', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(page.getByRole('heading', { name: 'Estatísticas', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Top Jogadores' }).click();
      await expect(page.getByRole('button', { name: 'Buscar' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('Comparação de Campeonatos tab switches content', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(page.getByRole('heading', { name: 'Estatísticas', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Comparação de Campeonatos' }).click();
      await expect(page.getByRole('button', { name: 'Comparação de Campeonatos' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('Por Campeonato tab switches content', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(page.getByRole('heading', { name: 'Estatísticas', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Por Campeonato' }).click();
      await expect(page.getByRole('button', { name: 'Por Campeonato' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
