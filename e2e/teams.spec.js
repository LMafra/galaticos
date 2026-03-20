const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test('teams list page renders', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/teams');
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
    await expect(page.getByText('Erro ao carregar')).toHaveCount(0);
  } finally {
    await saveCoverage(page, testInfo);
  }
});
