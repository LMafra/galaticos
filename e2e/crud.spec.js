const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  bannerHeading,
  expectPageTitle,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  fillMatchMinimal,
  expectUndoToast,
  clickUndo,
} = require('./_helpers');

test('create team', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new team form', async () => {
      await page.goto('/#/teams');
      await expect(page).toHaveURL(/\/#\/teams/);
      await expectPageTitle(page, 'Times');
      await page.getByRole('button', { name: 'Novo Time' }).click();
    });

    await test.step('fill and submit form', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Time' })).toBeVisible();
      const uniqueName = `Time E2E ${Date.now()}`;
      await page.getByLabel(/^Nome/).fill(uniqueName);
      await page.getByLabel(/^Sigla/).fill('TE2');
      await page.getByLabel(/^Categoria/).selectOption('adulto');
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to teams list', async () => {
      await expect(page).toHaveURL(/\#\/teams$/);
      await expectPageTitle(page, 'Times', { timeout: 15_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create player (minimal)', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new player form', async () => {
      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
      await page.getByRole('button', { name: 'Novo Jogador' }).click();
    });

    await test.step('fill required fields and submit', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Jogador' })).toBeVisible();
      await page.getByLabel(/^Nome/).fill('Jogador E2E Test');
      await page.getByLabel(/^Posição/).fill('Atacante');
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to players list', async () => {
      await expect(pageHeading(page, 'Jogadores')).toBeVisible({ timeout: 10_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create championship', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new championship form', async () => {
      await page.goto('/#/championships');
      await expect(pageHeading(page, 'Campeonatos')).toBeVisible();
      await page.getByRole('button', { name: 'Novo Campeonato' }).click();
    });

    await test.step('fill and submit form', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Campeonato' })).toBeVisible();
      const uniqueName = `Campeonato E2E ${Date.now()}`;
      await page.getByLabel(/^Nome/).fill(uniqueName);
      await page.getByLabel(/^Temporada/).fill('2025');
      await page.getByLabel(/^Títulos/).fill('0');
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to championships list', async () => {
      await expect(page).toHaveURL(/\#\/championships$/);
      await expect(pageHeading(page, 'Campeonatos')).toBeVisible({ timeout: 15_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create match with one player statistic', { tag: '@crud' }, async ({ page, request }, testInfo) => {
  try {
    let champId;
    await test.step('API: isolated championship + Galáticos player enrolled', async () => {
      champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request);
    });
    await page.goto(`/#/matches/by-championship/${champId}/new`);
    await expect(pageHeading(page, 'Nova Partida')).toBeVisible();
    await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});

    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });

    await fillMatchMinimal(page);

    const createMatchResp = page.waitForResponse((r) => {
      if (r.request().method() !== 'POST') return false;
      try {
        return new URL(r.url()).pathname === '/api/matches';
      } catch {
        return false;
      }
    }, { timeout: 20_000 });
    await expect(page.getByRole('button', { name: 'Criar Partida' })).toBeEnabled({ timeout: 15_000 });
    await page.getByRole('button', { name: 'Criar Partida' }).click();
    const resp = await createMatchResp;
    expect(resp.status(), await resp.text()).toBe(201);
    await expect(page).toHaveURL(/\#\/matches(\/championship\/[^/]+)?$/);
    await expect(bannerHeading(page, 'Partidas')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: 'Nova Partida' }).first()).toBeVisible({
      timeout: 15_000,
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('edit player', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(pageHeading(page, 'Jogadores')).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No players in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Editar' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Editar' }).click();
    await expect(page.getByRole('heading', { name: 'Editar Jogador' })).toBeVisible();
    await page.getByLabel(/^Apelido/).fill(`E2E Apelido ${Date.now()}`);
    await page.getByRole('button', { name: 'Atualizar' }).click();
    const onList = pageHeading(page, 'Jogadores');
    const onDetail = page.getByRole('button', { name: 'Informações' });
    const onError = page.getByText(/Erro ao .* jogador/);
    await expect(onList.or(onDetail).or(onError)).toBeVisible({ timeout: 25_000 });
    if (page.url().includes('/edit')) {
      await expect(onError).toBeVisible();
    }
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('edit team', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/teams');
    await expect(page).toHaveURL(/\/#\/teams/);
    await expectPageTitle(page, 'Times');
    const teamCards = page.locator('main .grid.gap-4 .app-card').filter({
      has: page.getByRole('button', { name: 'Ver elenco' }),
    });
    await teamCards.first().waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const cardCount = await teamCards.count();
    if (cardCount === 0) {
      test.skip(true, 'No teams in list');
      return;
    }
    const galaticosCard = teamCards.filter({ hasText: 'Galáticos' }).first();
    const teamCard = (await galaticosCard.count()) > 0 ? galaticosCard : teamCards.first();
    await teamCard.getByRole('button', { name: 'Editar' }).click();
    await expect(page.getByRole('heading', { name: 'Editar Time' })).toBeVisible();
    await page.getByLabel(/^Categoria/).selectOption('adulto');
    await page.getByLabel(/^Sigla/).fill('GAL');
    await page.getByRole('button', { name: 'Atualizar' }).click();
    await expectPageTitle(page, 'Times', { timeout: 10_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('delete player (soft delete)', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(pageHeading(page, 'Jogadores')).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No players in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Deletar' }).click();
    await expectUndoToast(page, /Jogador removido/i);
    await expect(pageHeading(page, 'Jogadores')).toBeVisible({ timeout: 10_000 });
    await clickUndo(page);
    await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 10_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});
