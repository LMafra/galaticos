const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  expectPageTitle,
  pageHeading,
  toastRegion,
  getAdminToken,
  apiJson,
  mainContent,
} = require('./_helpers');

test.describe('UX dashboard depth', { tag: '@ux' }, () => {
  test('championship filter narrows Resumo de Campeonatos', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token).toBeTruthy();
      const unique = Date.now();
      const nameA = `E2E Dash A ${unique}`;
      const nameB = `E2E Dash B ${unique}`;
      for (const name of [nameA, nameB]) {
        const { response, body } = await apiJson(request, token, 'POST', '/api/championships', {
          name,
          season: '2026',
          'titles-count': 0,
          status: 'active',
        });
        expect(response.ok(), JSON.stringify(body)).toBeTruthy();
      }

      await page.goto('/#/dashboard');
      await expect(page.getByText('Resumo de Campeonatos')).toBeVisible({ timeout: 15_000 });
      // Force refresh so API-created championships land in dashboard-stats.
      await page.reload();
      await expect(page.getByText('Resumo de Campeonatos')).toBeVisible({ timeout: 15_000 });

      const resumo = mainContent(page).locator('.app-card').filter({ hasText: 'Resumo de Campeonatos' });
      await expect(resumo.getByRole('cell', { name: nameA })).toBeVisible({ timeout: 15_000 });
      await expect(resumo.getByRole('cell', { name: nameB })).toBeVisible();

      const champSelect = page.getByLabel('Campeonato', { exact: true });
      await expect(champSelect).toBeVisible();
      await champSelect.selectOption({ label: nameA });

      await expect(resumo.getByRole('cell', { name: nameA })).toBeVisible();
      await expect(resumo.getByRole('cell', { name: nameB })).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('charts render when seed has championship data', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expectPageTitle(page, 'Dashboard');
      await expect(page.getByText('Resumo de Campeonatos')).toBeVisible({ timeout: 15_000 });
      // Recharts mounts SVG surfaces for bar/line when data exists.
      const chart = page.locator('.recharts-responsive-container, .recharts-surface, svg.recharts-surface');
      await expect(chart.first()).toBeVisible({ timeout: 20_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('CSV export downloads success toast when authenticated', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(pageHeading(page, 'Dashboard')).toBeVisible({ timeout: 15_000 });
      const exportBtn = page.getByRole('button', { name: /Exportar CSV/i });
      await expect(exportBtn, 'Exportar CSV must be visible when authenticated').toBeVisible({
        timeout: 10_000,
      });
      await exportBtn.click();
      await expect(toastRegion(page).getByText(/exportado com sucesso/i)).toBeVisible({
        timeout: 15_000,
      });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
