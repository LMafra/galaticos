const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test.describe('UX theme', { tag: '@ux' }, () => {
  test('theme toggle adds dark class and survives reload', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.getByRole('banner')).toBeVisible({ timeout: 15_000 });

      await page.evaluate(() => {
        window.localStorage.setItem('galaticos.theme', 'light');
        document.documentElement.classList.remove('dark');
      });
      await page.reload();
      await expect(page.getByRole('banner')).toBeVisible({ timeout: 15_000 });

      const toggle = page.getByRole('button', { name: 'Alternar tema claro/escuro' });
      await expect(toggle).toBeVisible();
      await toggle.click();

      await expect
        .poll(async () => page.evaluate(() => document.documentElement.classList.contains('dark')))
        .toBe(true);
      await expect
        .poll(async () => page.evaluate(() => window.localStorage.getItem('galaticos.theme')))
        .toBe('dark');

      await page.reload();
      await expect(page.getByRole('banner')).toBeVisible({ timeout: 15_000 });
      await expect
        .poll(async () => page.evaluate(() => document.documentElement.classList.contains('dark')))
        .toBe(true);
      await expect
        .poll(async () => page.evaluate(() => window.localStorage.getItem('galaticos.theme')))
        .toBe('dark');
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
