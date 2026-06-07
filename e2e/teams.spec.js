const { test, expect } = require('@playwright/test');
const { saveCoverage, expectPageTitle } = require('./_helpers');

test('teams list page renders', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/teams');
    await expect(page).toHaveURL(/\/#\/teams/);
    await expectPageTitle(page, 'Times');
    await expect(page.getByText('Erro ao carregar')).toHaveCount(0);
  } finally {
    await saveCoverage(page, testInfo);
  }
});
