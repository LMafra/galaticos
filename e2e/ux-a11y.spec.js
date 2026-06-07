const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  setupTwoPlayersForMerge,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
} = require('./_helpers');

test.describe('UX accessibility pragmatics', { tag: ['@ux', '@ux-a11y'] }, () => {
  test('mobile nav items have aria-label', async ({ page }, testInfo) => {
    try {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/#/dashboard');
      const nav = page.getByLabel('Navegação principal');
      await expect(nav.getByLabel('Partidas')).toHaveAttribute('aria-label', 'Partidas');
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('merge modal Esc closes and Cancel is initial focus', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await page.getByRole('button', { name: 'Mesclar jogadores' }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog.locator('.modal-cancel').first()).toBeFocused();
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('login submit button is visible', async ({ browser }, testInfo) => {
    const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    const page = await context.newPage();
    try {
      await page.goto('/#/login');
      await expect(page.getByRole('heading', { name: 'Login - Galáticos' })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
      await context.close();
    }
  });

  test('match form table has scoped column headers', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      const headers = page.locator('table thead th[scope="col"]');
      await expect(headers.first()).toBeVisible({ timeout: 15_000 });
      expect(await headers.count()).toBeGreaterThan(3);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
