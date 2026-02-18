const { test, expect } = require('@playwright/test');
const { loginAsAdmin, saveCoverage } = require('./_helpers');

test('dashboard loads stats endpoint', async ({ page }, testInfo) => {
  try {
    // Wait for the dashboard stats request after login.
    const statsPromise = page.waitForResponse((r) => r.url().includes('/api/aggregations/stats') && r.status() === 200);
    await loginAsAdmin(page);
    await statsPromise;

    // If stats are present, at least one of these sections should render.
    await expect(page.getByText('Resumo de Campeonatos')).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('players/matches/championships list pages render without errors', async ({ page }, testInfo) => {
  try {
    await loginAsAdmin(page);

    // Players
    await page.goto('/#/players');
    await expect(page.getByRole('heading', { name: 'Jogadores' })).toBeVisible();
    await expect(page.getByText('Erro ao carregar players')).toHaveCount(0);

    // Matches
    await page.goto('/#/matches');
    await expect(page.getByRole('heading', { name: 'Partidas' })).toBeVisible();
    await expect(page.getByText('Erro ao carregar matches')).toHaveCount(0);

    // Championships
    await page.goto('/#/championships');
    await expect(page.getByRole('heading', { name: 'Campeonatos' })).toBeVisible();
    await expect(page.getByText('Erro ao carregar championships')).toHaveCount(0);
  } finally {
    await saveCoverage(page, testInfo);
  }
});


