const { test, expect } = require('@playwright/test');
const { saveCoverage, pageHeading, waitSkeletonHidden } = require('./_helpers');

test.describe('UX mobile shell', { tag: ['@ux', '@ux-mobile'] }, () => {
  test('bottom tab bar shows main routes', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      const nav = page.getByLabel('Navegação principal');
      await expect(nav).toBeVisible();
      await expect(nav.getByLabel('Início')).toBeVisible();
      await expect(nav.getByLabel('Partidas')).toBeVisible();
      await expect(nav.getByLabel('Jogadores')).toBeVisible();
      await expect(nav.getByLabel('Campeonatos')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('Mais drawer opens extra links', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await waitSkeletonHidden(page);
      await page.getByLabel('Mais opções').click({ force: true });
      await expect(page.locator('#mobile-drawer-nav')).toBeVisible({ timeout: 5000 });
      await expect(page.getByRole('link', { name: 'Estatísticas' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('tab navigation reaches players', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await waitSkeletonHidden(page);
      await page.getByLabel('Navegação principal').getByLabel('Jogadores').click({ force: true });
      await expect(page).toHaveURL(/\/#\/players/);
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});

test.describe('UX tablet sidebar', { tag: ['@ux', '@ux-mobile'] }, () => {
  test.use({ viewport: { width: 800, height: 900 } });

  test('sidebar visible at tablet width', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.locator('aside').first()).toBeVisible();
      await expect(page.getByLabel('Navegação principal')).toBeHidden();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
