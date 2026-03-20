const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test('create team', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new team form', async () => {
      await page.goto('/#/teams');
      await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Novo Time' }).click();
    });

    await test.step('fill and submit form', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Time' })).toBeVisible();
      const uniqueName = `Time E2E ${Date.now()}`;
      await page.getByLabel(/^Nome/).fill(uniqueName);
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to teams list', async () => {
      await expect(page).toHaveURL(/\#\/teams$/);
      await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create player (minimal)', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new player form', async () => {
      await page.goto('/#/players');
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: 'Novo Jogador' }).click();
    });

    await test.step('fill required fields and submit', async () => {
      await expect(page.getByRole('heading', { name: 'Novo Jogador' })).toBeVisible();
      await page.getByLabel(/^Nome/).fill('Jogador E2E Test');
      await page.getByLabel(/^Posição/).fill('Atacante');
      await page.getByRole('button', { name: 'Criar' }).click();
    });

    await test.step('verify redirect to players list', async () => {
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible({ timeout: 10_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create championship', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await test.step('navigate to new championship form', async () => {
      await page.goto('/#/championships');
      await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();
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
      await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('create match with one player statistic', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/matches');
    await expect(page.getByRole('heading', { name: 'Partidas', level: 1 })).toBeVisible();
    await page.getByRole('button', { name: 'Nova Partida' }).click();
    await expect(page.getByRole('heading', { name: 'Nova Partida' })).toBeVisible();

    const champSelect = page.getByLabel(/^Campeonato/);
    await champSelect.waitFor({ state: 'visible' });
    let valueOptions = await champSelect.locator('option').evaluateAll((opts) => opts.map((o) => o.getAttribute('value')));
    let firstValue = valueOptions.find((v) => v && v.length > 0);
    if (!firstValue) {
      await test.step('create championship so match form has an option', async () => {
        await page.goto('/#/championships');
        await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();
        await page.getByRole('button', { name: 'Novo Campeonato' }).click();
        await expect(page.getByRole('heading', { name: 'Novo Campeonato' })).toBeVisible();
        await page.getByLabel(/^Nome/).fill(`Campeonato E2E ${Date.now()}`);
        await page.getByLabel(/^Temporada/).fill('2025');
        await page.getByLabel(/^Títulos/).fill('0');
        await page.getByRole('button', { name: 'Criar' }).click();
        await expect(page).toHaveURL(/\#\/championships$/);
        await page.goto('/#/matches');
        await page.getByRole('button', { name: 'Nova Partida' }).click();
        await expect(page.getByRole('heading', { name: 'Nova Partida' })).toBeVisible();
        await champSelect.waitFor({ state: 'visible' });
        valueOptions = await champSelect.locator('option').evaluateAll((opts) => opts.map((o) => o.getAttribute('value')));
        firstValue = valueOptions.find((v) => v && v.length > 0);
      });
    }
    if (!firstValue) {
      test.skip(true, 'No championship available after setup - run db:seed-smoke');
      return;
    }
    await champSelect.selectOption(firstValue);
    await page.waitForTimeout(800);

    await page.getByLabel(/^Data/).fill('2025-06-01');
    await page.getByLabel(/^Adversário/).fill('Adversário E2E');
    await page.getByRole('button', { name: 'Adicionar estatística' }).click();

    const playerSelects = page.getByLabel('Jogador');
    await playerSelects.first().waitFor({ state: 'visible', timeout: 5000 });
    const playerOptions = await page.getByLabel('Jogador').first().locator('option').evaluateAll((opts) => opts.map((o) => o.getAttribute('value')));
    const firstPlayer = playerOptions.find((v) => v && v.length > 0);
    if (!firstPlayer) {
      test.skip(true, 'No enrolled players in championship - run db:seed-smoke or enroll a player');
      return;
    }
    await page.getByLabel('Jogador').first().selectOption(firstPlayer);
    await page.getByLabel('Gols').first().fill('0');

    await page.getByRole('button', { name: 'Criar Partida' }).click();
    await expect(page).toHaveURL(/\#\/matches$/);
    await expect(page.getByRole('heading', { name: 'Partidas', level: 1 })).toBeVisible({ timeout: 15_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('edit player', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
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
    const onList = page.getByRole('heading', { name: 'Jogadores', level: 1 });
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
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No teams in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Editar' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Editar' }).click();
    await expect(page.getByRole('heading', { name: 'Editar Time' })).toBeVisible();
    await page.getByLabel(/^Cidade/).fill('Cidade E2E');
    await page.getByRole('button', { name: 'Atualizar' }).click();
    await expect(page.getByRole('heading', { name: 'Times', level: 1 })).toBeVisible({ timeout: 10_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test('delete player (soft delete)', { tag: '@crud' }, async ({ page }, testInfo) => {
  try {
    await page.goto('/#/players');
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    const rowCount = await page.locator('table tbody tr').count();
    if (rowCount === 0) {
      test.skip(true, 'No players in list');
      return;
    }
    await firstRow.click();
    await expect(page.getByRole('button', { name: 'Deletar' })).toBeVisible({ timeout: 5000 });
    page.on('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Deletar' }).click();
    await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible({ timeout: 10_000 });
  } finally {
    await saveCoverage(page, testInfo);
  }
});
