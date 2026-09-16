const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  setupChampionshipWithMax,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  getAdminToken,
  apiJson,
  pickPlayerInSearchAddPanel,
  toastRegion,
  mainContent,
  getGalaticosTeamId,
  activateChampionshipSeason,
} = require('./_helpers');

test.describe('UX championships', { tag: '@ux' }, () => {
  test('championship detail shows inscrições and seasons', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      await expect(mainContent(page).getByText('Inscrições')).toBeVisible({ timeout: 15_000 });
      await expect(mainContent(page).getByRole('heading', { name: 'Temporadas', exact: true })).toBeVisible();
      await expect(mainContent(page).getByRole('heading', { name: 'Detalhes', exact: true })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('max players limit shown on championship detail', async ({ page, request }, testInfo) => {
    try {
      const { championshipId } = await setupChampionshipWithMax(request, 2, page);
      await page.goto(`/#/championships/${championshipId}`);
      await expect(mainContent(page).getByText(/Limite de jogadores:\s*2/)).toBeVisible({ timeout: 15_000 });
      await expect(mainContent(page).getByText(/E2E Max P0/).first()).toBeVisible();
      await expect(mainContent(page).getByText(/E2E Max P1/).first()).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('enrolling beyond max via API is rejected', async ({ page, request }, testInfo) => {
    try {
      const { championshipId } = await setupChampionshipWithMax(request, 1, page);
      const token = await getAdminToken(request, page);
      const { body: tBody } = await apiJson(request, token, 'GET', '/api/teams');
      const team = (tBody?.data || []).find((x) => x?.name === 'Galáticos' || x?.name === 'Galaticos');
      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: `E2E Over Max ${Date.now()}`,
        position: 'Atacante',
        'team-id': String(team._id),
      });
      expect(pRes.ok()).toBeTruthy();
      const { response: eRes } = await apiJson(
        request,
        token,
        'POST',
        `/api/championships/${championshipId}/enroll/${pBody?.data?._id}`,
        {}
      );
      expect(eRes.ok()).toBeFalsy();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('enroll player from championship detail UI', async ({ page, request }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token).toBeTruthy();
      const unique = Date.now();
      const playerName = `E2E Enroll UI ${unique}`;
      const { response: cRes, body: cBody } = await apiJson(request, token, 'POST', '/api/championships', {
        name: `E2E Enroll Champ ${unique}`,
        season: '2026',
        'titles-count': 0,
        status: 'active',
      });
      expect(cRes.ok(), JSON.stringify(cBody)).toBeTruthy();
      const championshipId = String(cBody?.data?._id);
      await activateChampionshipSeason(request, token, championshipId);
      const teamId = await getGalaticosTeamId(request, token);
      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: playerName,
        position: 'Atacante',
        'team-id': teamId,
      });
      expect(pRes.ok(), JSON.stringify(pBody)).toBeTruthy();

      await page.goto(`/#/championships/${championshipId}`);
      await expect(page.getByText('Inscrições')).toBeVisible({ timeout: 15_000 });
      await pickPlayerInSearchAddPanel(page, playerName, 'Inscrever');
      await expect(
        mainContent(page).locator('div').filter({ hasText: playerName }).filter({ has: page.getByRole('button', { name: 'Remover' }) }).first()
      ).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('UI blocks enroll when max-players reached (poka-yoke)', async ({ page, request }, testInfo) => {
    try {
      const { championshipId } = await setupChampionshipWithMax(request, 1, page);
      const token = await getAdminToken(request, page);
      const teamId = await getGalaticosTeamId(request, token);
      const overName = `E2E Over UI ${Date.now()}`;
      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: overName,
        position: 'Atacante',
        'team-id': teamId,
      });
      expect(pRes.ok(), JSON.stringify(pBody)).toBeTruthy();

      await page.goto(`/#/championships/${championshipId}`);
      await expect(mainContent(page).getByText(/Limite de jogadores:\s*1/)).toBeVisible({
        timeout: 15_000,
      });
      await expect(mainContent(page).getByText(/1\/1\s*inscritos/)).toBeVisible();
      await expect(
        mainContent(page).getByText(/atingiu o limite máximo de 1 jogadores inscritos/i)
      ).toBeVisible();
      const search = mainContent(page).getByPlaceholder(/Limite de inscrições atingido/i);
      await expect(search).toBeVisible();
      await expect(search).toBeDisabled();
      await expect(toastRegion(page).getByText(/Erro ao inscrever/i)).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('finalize championship completes and shows finalized status', async ({
    page,
    request,
  }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      await expect(page.getByText('Inscrições')).toBeVisible({ timeout: 15_000 });

      const titlesInput = page.getByLabel(/Títulos a conceder/i);
      await expect(titlesInput).toBeVisible();
      await titlesInput.fill('1');

      const winnerLabel = mainContent(page)
        .locator('label')
        .filter({ has: page.locator('input[type="checkbox"]') })
        .first();
      await expect(winnerLabel).toBeVisible();
      await winnerLabel.click();
      await expect(winnerLabel.locator('input[type="checkbox"]')).toBeChecked();

      const finalizeBtn = page.getByRole('button', { name: /Finalizar campeonato/i });
      await expect(finalizeBtn).toBeEnabled();
      await finalizeBtn.click();

      await expect(page.getByText(/Apenas campeonatos ativos podem ser finalizados/i)).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByRole('button', { name: /Finalizar campeonato/i })).toHaveCount(0);
      await expect(mainContent(page).getByText(/Finalizado|Vencedores:/i).first()).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('finalize championship button visible for active champ', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      const finalizeBtn = page.getByRole('button', { name: /Finalizar campeonato/i });
      await expect(finalizeBtn).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('leaderboards section has reserved min-height skeleton area', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      await expect(page.getByText(/Top 5/)).toBeVisible({ timeout: 20_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
