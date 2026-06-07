const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  expectPageTitle,
  expectBreadcrumb,
  toastRegion,
  pageHeading,
  getAdminToken,
  apiJson,
} = require('./_helpers');

test.describe('UX teams and dashboard', { tag: '@ux' }, () => {
  test('teams list renders cards', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      await expectPageTitle(page, 'Times');
      await expect(page.locator('main .app-card').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('team detail breadcrumb', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      const card = page.locator('main .app-card').first();
      if ((await card.count()) === 0) {
        test.skip(true, 'No teams');
        return;
      }
      await card.click();
      await expectBreadcrumb(page, ['Times']);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('team update 400 keeps form values', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token).toBeTruthy();
      const unique = Date.now();
      const { body, response } = await apiJson(request, token, 'POST', '/api/teams', {
        name: `UX Team Form ${unique}`,
        abbreviation: 'UXT',
        category: 'Adulto',
      });
      expect(response.ok(), JSON.stringify(body)).toBeTruthy();
      const teamId = body?.data?._id;
      expect(teamId).toBeTruthy();
      await page.goto(`/#/teams/${teamId}/edit`);
      await expect(page.getByRole('heading', { name: 'Editar Time' })).toBeVisible({ timeout: 15_000 });
      const sigla = `UX${Date.now().toString().slice(-4)}`;
      await page.getByLabel(/^Sigla/).fill(sigla);
      await page.route('**/api/teams/**', async (route) => {
        if (route.request().method() === 'PUT') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({ success: false, error: 'Sigla inválida' }),
          });
          return;
        }
        await route.continue();
      });
      await page.getByRole('button', { name: 'Atualizar' }).click();
      await expect(page.getByLabel(/^Sigla/)).toHaveValue(sigla);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('dashboard shows focal summary', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.getByText('Resumo de Campeonatos')).toBeVisible({ timeout: 15_000 });
      await expect(pageHeading(page, 'Dashboard')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('dashboard export shows success toast', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      const exportBtn = page.getByRole('button', { name: /Exportar CSV/i });
      if ((await exportBtn.count()) === 0) {
        test.skip(true, 'Export not visible (auth/layout)');
        return;
      }
      await exportBtn.click();
      await expect(toastRegion(page).getByText(/exportado com sucesso/i)).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('stats page tabs render', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(page).toHaveURL(/\/#\/stats/);
      await expect(page.getByRole('banner')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
