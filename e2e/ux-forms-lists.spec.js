const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  formFieldAlert,
  mainContent,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  fillMatchMinimal,
} = require('./_helpers');

test.describe('UX forms and lists', { tag: '@ux' }, () => {
  test('400 response keeps player form field values', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players/new');
      const name = `UX Keep Fields ${Date.now()}`;
      await page.getByLabel(/^Nome/).fill(name);
      await page.getByLabel(/^Posição/).fill('Atacante');
      await page.route('**/api/players', async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({ success: false, error: 'Nome inválido', field: 'name' }),
          });
          return;
        }
        await route.continue();
      });
      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(page.getByLabel(/^Nome/)).toHaveValue(name);
      await expect(page.getByLabel(/^Posição/)).toHaveValue('Atacante');
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('save button shows A guardar… quickly', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players/new');
      await page.getByLabel(/^Nome/).fill(`UX Saving ${Date.now()}`);
      await page.getByLabel(/^Posição/).fill('Atacante');
      await page.route('**/api/players', async (route) => {
        if (route.request().method() === 'POST') {
          await new Promise((r) => setTimeout(r, 2000));
          await route.continue();
          return;
        }
        await route.continue();
      });
      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(page.getByRole('button', { name: 'A guardar…' })).toBeVisible({ timeout: 500 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('players list position filter does not error', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
      const positionSelect = page.getByLabel(/^Posição/);
      await positionSelect.selectOption({ index: 0 });
      await page.waitForTimeout(400);
      await expect(page.getByText('Erro ao carregar players')).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('team form required validation', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      await page.getByRole('button', { name: 'Novo Time' }).click();
      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(formFieldAlert(page, 'Nome é obrigatório')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('match 409 shows conflict alert in form', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await page.route('**/api/matches', async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 409,
            contentType: 'application/json',
            body: JSON.stringify({
              success: false,
              error: 'Conflito',
              'resource-id': '507f1f77bcf86cd799439011',
            }),
          });
          return;
        }
        await route.continue();
      });
      await fillMatchMinimal(page, { opponent: 'Conflict UX', date: '2026-05-01' });
      const createBtn = mainContent(page).getByRole('button', { name: /Criar Partida/i });
      await expect(createBtn).toBeEnabled({ timeout: 10_000 });
      await createBtn.click();
      await expect(mainContent(page).getByText(/Conflito|Ver partida/i).first()).toBeVisible({
        timeout: 10_000,
      });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
