const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  expectPageTitle,
  loginAsAdmin,
} = require('./_helpers');

/**
 * Runs in the `auth` project (no storageState) so guest sessions stay logged out.
 * @see playwright.config.js
 */
test.describe('UX auth gates', { tag: '@ux' }, () => {
  test('guest visiting /players/new never sees create form; lands on dashboard', async ({
    page,
  }, testInfo) => {
    try {
      await page.goto('/#/players/new');
      await expect(page).toHaveURL(/\/#\/(dashboard)?$/, { timeout: 15_000 });
      await expect(pageHeading(page, 'Dashboard')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('heading', { name: 'Novo Jogador' })).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('guest visiting /teams is blocked from roster writes', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      await expect(page).toHaveURL(/\/#\/(dashboard)?$/, { timeout: 15_000 });
      await expect(pageHeading(page, 'Dashboard')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: 'Novo Time' })).toHaveCount(0);
      await expect(page.getByText('Elenco do time')).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('guest dashboard shows Entrar and hides Exportar CSV / Novo Jogador on list', async ({
    page,
  }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expectPageTitle(page, 'Dashboard');
      await expect(page.locator('header').getByRole('button', { name: 'Entrar' })).toBeVisible();
      await expect(page.getByRole('button', { name: /Exportar CSV/i })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Novo Jogador' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Novo Time' })).toHaveCount(0);

      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: 'Novo Jogador' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Mesclar jogadores' })).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('guest championships list and detail hide write actions', async ({ page, request }, testInfo) => {
    try {
      await page.goto('/#/championships');
      await expect(pageHeading(page, 'Campeonatos')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: 'Novo Campeonato' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Atualizar' })).toBeVisible();

      const listRes = await request.get('/api/championships');
      if (listRes.ok()) {
        const body = await listRes.json().catch(() => ({}));
        const champs = Array.isArray(body?.data) ? body.data : [];
        const id = champs[0]?._id;
        if (id) {
          await page.goto(`/#/championships/${id}`);
          await expect(page.locator('#main-content').getByRole('heading').first()).toBeVisible({
            timeout: 15_000,
          });
          await expect(page.getByRole('button', { name: 'Editar' })).toHaveCount(0);
          await expect(page.getByRole('button', { name: 'Deletar' })).toHaveCount(0);
          await expect(page.getByRole('button', { name: /Exportar CSV/i })).toHaveCount(0);
          await expect(page.getByRole('button', { name: 'Finalizar campeonato' })).toHaveCount(0);
          await expect(page.getByRole('button', { name: 'Inscrever' })).toHaveCount(0);
          await expect(page.getByRole('button', { name: 'Criar temporada' })).toHaveCount(0);
          await expect(page.getByRole('button', { name: 'Ativar' })).toHaveCount(0);
          await expect(page.getByRole('button', { name: 'Remover' })).toHaveCount(0);
        }
      }
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('after logout, protected hash re-gates to dashboard', async ({ page }, testInfo) => {
    try {
      await loginAsAdmin(page);
      await expect(page.getByRole('button', { name: 'Sair' })).toBeVisible();
      await page.getByRole('button', { name: 'Sair' }).click();
      await expect(page.getByRole('heading', { name: 'Login - Galáticos' })).toBeVisible({
        timeout: 10_000,
      });

      await page.goto('/#/matches/new');
      await expect(page).toHaveURL(/\/#\/(dashboard)?$/, { timeout: 15_000 });
      await expect(pageHeading(page, 'Dashboard')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('heading', { name: 'Nova Partida' })).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
