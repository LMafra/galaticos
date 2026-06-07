const { test, expect } = require('@playwright/test');
const { saveCoverage, pageHeading, expectPageTitle } = require('./_helpers');

test('navigate between main pages via header', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/dashboard');

    await page.getByRole('link', { name: 'Jogadores' }).click();
    await expect(pageHeading(page, 'Jogadores')).toBeVisible();

    await page.getByRole('link', { name: 'Partidas' }).click();
    await expect(pageHeading(page, 'Partidas')).toBeVisible();

    await page.getByRole('link', { name: 'Campeonatos' }).click();
    await expect(pageHeading(page, 'Campeonatos')).toBeVisible();

    await page.getByRole('link', { name: 'Dashboard' }).click();
    await expect(pageHeading(page, 'Dashboard')).toBeVisible();
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('navigate to Estatísticas (Stats)', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/dashboard');
    await page.getByRole('link', { name: 'Estatísticas' }).click();
    await expect(page).toHaveURL(/\/#\/stats/);
    await expectPageTitle(page, 'Estatísticas');
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('navigate to Times (Teams)', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/dashboard');
    await page.getByRole('link', { name: 'Times' }).click();
    await expect(page).toHaveURL(/\/#\/teams/);
    await expectPageTitle(page, 'Times');
  } finally {
    await saveCoverage(page, testInfo);
  }
});


