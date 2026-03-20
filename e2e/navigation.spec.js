const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test('navigate between main pages via header', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/dashboard');

    await page.getByRole('link', { name: 'Jogadores' }).click();
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();

    await page.getByRole('link', { name: 'Partidas' }).click();
    await expect(page.getByRole('heading', { name: 'Partidas', level: 1 })).toBeVisible();

    await page.getByRole('link', { name: 'Campeonatos' }).click();
    await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();

    await page.getByRole('link', { name: 'Dashboard' }).click();
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('navigate to Estatísticas (Stats)', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/dashboard');
    await page.getByRole('link', { name: 'Estatísticas' }).click();
    await expect(page.getByRole('heading', { name: 'Estatísticas', level: 1 })).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('navigate to Times (Teams)', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/dashboard');
    await page.getByRole('link', { name: 'Times' }).click();
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});


