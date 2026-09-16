const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  toastRegion,
  getAdminToken,
  apiJson,
  activateChampionshipSeason,
  mainContent,
  pageHeading,
} = require('./_helpers');

test.describe('UX stats reconcile and filters', { tag: '@ux' }, () => {
  test('Executar reconciliação shows success toast', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(pageHeading(page, 'Estatísticas')).toBeVisible({ timeout: 15_000 });
      const reconcileBtn = page.getByRole('button', {
        name: /Recalcular estatísticas|Executar reconciliação/i,
      });
      await expect(reconcileBtn).toBeVisible();
      await reconcileBtn.click();
      await expect(
        toastRegion(page).getByText(/Reconciliação concluída|jogador\(es\) atualizado/i)
      ).toBeVisible({ timeout: 30_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('global championship filter changes Top Jogadores; clear restores', async ({
    page,
    request,
  }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token).toBeTruthy();
      const unique = Date.now();
      const emptyName = `E2E Stats Empty ${unique}`;
      const { response, body } = await apiJson(request, token, 'POST', '/api/championships', {
        name: emptyName,
        season: '2026',
        'titles-count': 0,
        status: 'active',
      });
      expect(response.ok(), JSON.stringify(body)).toBeTruthy();
      const emptyId = String(body?.data?._id);
      await activateChampionshipSeason(request, token, emptyId);

      await page.goto('/#/stats');
      await expect(pageHeading(page, 'Estatísticas')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Top Jogadores por/i)).toBeVisible({ timeout: 20_000 });

      const beforeRows = await mainContent(page).locator('table tbody tr').count();

      const champSelect = mainContent(page)
        .locator('.app-card')
        .filter({ hasText: 'Filtros globais' })
        .getByLabel('Campeonato', { exact: true });
      await champSelect.selectOption({ label: emptyName });
      await page.getByRole('button', { name: 'Buscar' }).click();

      await expect(
        mainContent(page).getByText(/Nenhum resultado com estes filtros|Nenhum jogador encontrado/i)
      ).toBeVisible({ timeout: 15_000 });

      await page.getByRole('button', { name: 'Limpar filtros' }).first().click();
      await page.getByRole('button', { name: 'Buscar' }).click();
      await expect(page.getByText(/Top Jogadores por/i)).toBeVisible({ timeout: 20_000 });
      if (beforeRows > 0) {
        await expect(mainContent(page).locator('table tbody tr').first()).toBeVisible({
          timeout: 15_000,
        });
      }
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
