const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test('dashboard loads stats endpoint', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    const statsPromise = page.waitForResponse((r) => r.url().includes('/api/aggregations/stats') && r.status() === 200);
    await page.goto('/#/dashboard');
    await statsPromise;

    await expect(page.getByText('Resumo de Campeonatos')).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('players/matches/championships list pages render without errors', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
    await expect(page.getByText('Erro ao carregar players')).toHaveCount(0);

    await page.goto('/#/matches');
    await expect(page.getByRole('heading', { name: 'Partidas', level: 1 })).toBeVisible();
    await expect(page.getByText('Erro ao carregar matches')).toHaveCount(0);

    await page.goto('/#/championships');
    await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();
    await expect(page.getByText('Erro ao carregar championships')).toHaveCount(0);
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('teams list page renders without error', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/teams');
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
    await expect(page.getByText('Erro ao carregar')).toHaveCount(0);
  } finally {
    await saveCoverage(page, testInfo);
  }
});


