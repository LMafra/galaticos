const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  setupTwoPlayersForMerge,
  toastRegion,
  clickUndo,
} = require('./_helpers');

test.describe('UX players and merge', { tag: ['@ux', '@ux-slow'] }, () => {
  test('players list search toolbar works', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
      await page.getByPlaceholder('Buscar jogador...').fill('a');
      await page.waitForTimeout(400);
      await expect(page.getByText('Erro ao carregar players')).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('new player form validates required fields', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players/new');
      await expect(page.getByRole('heading', { name: 'Novo Jogador' })).toBeVisible();
      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(page.locator('p[role="alert"]', { hasText: 'Nome é obrigatório' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('merge modal opens with accessible dialog', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await page.getByRole('button', { name: 'Mesclar jogadores' }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      await expect(dialog.getByRole('heading', { name: 'Mesclar jogadores' })).toBeVisible();
      await expect(dialog.locator('.modal-cancel').first()).toBeFocused();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('merge three-step flow with undo toast', async ({ page, request }, testInfo) => {
    try {
      const { names } = await setupTwoPlayersForMerge(request, page);
      await page.goto('/#/players');
      await page.getByRole('button', { name: 'Mesclar jogadores' }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByPlaceholder('Nome para buscar...').fill(names[0].slice(0, 12));
      await dialog.getByRole('button', { name: 'Buscar' }).click();
      await dialog.getByRole('button', { name: names[0] }).first().click();
      await expect(dialog.getByText(/Referência:/)).toBeVisible({ timeout: 15_000 });
      const nextBtn = dialog.getByRole('button', { name: 'Próximo: comparar' });
      if (!(await nextBtn.isEnabled().catch(() => false))) {
        test.skip(true, 'No merge candidates from API');
        return;
      }
      await nextBtn.click();
      await expect(dialog.getByText(/Carregando comparação|Registro mestre/i)).toBeVisible({ timeout: 15_000 });
      await dialog.getByRole('button', { name: 'Confirmar mesclagem' }).click();
      await expect(toastRegion(page).getByText(/Unificação em 10 s/i)).toBeVisible({ timeout: 10_000 });
      await clickUndo(page);
      await expect(toastRegion(page).getByText(/Unificação cancelada/i)).toBeVisible({ timeout: 10_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('player detail has Informações and Estatísticas tabs', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
      const row = page.locator('table tbody tr').first();
      await row.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
      if ((await page.locator('table tbody tr').count()) === 0) {
        test.skip(true, 'No players');
        return;
      }
      await row.click();
      await expect(page.getByRole('button', { name: 'Informações' })).toBeVisible({ timeout: 10_000 });
      await expect(page.getByRole('button', { name: 'Estatísticas', exact: true })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
