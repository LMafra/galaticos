const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  bannerHeading,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  fillMatchMinimal,
  getAdminToken,
  apiJson,
  mainContent,
} = require('./_helpers');

test.describe('UX match lifecycle', { tag: '@ux' }, () => {
  test('match detail shows player statistics after create', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const opponent = `E2E Life Detail ${Date.now()}`;
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await expect(pageHeading(page, 'Nova Partida')).toBeVisible();
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });
      await fillMatchMinimal(page, { opponent });

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
      const body = await resp.json();
      const matchId = body?.data?._id;
      expect(matchId).toBeTruthy();

      await page.goto(`/#/matches/${matchId}`);
      await expect(mainContent(page).getByRole('heading', { name: opponent, level: 2 })).toBeVisible({
        timeout: 15_000,
      });
      await expect(mainContent(page).getByText(/Gols \(jogadores\)|Estatísticas|Jogador/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Editar' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('edit match updates goals and persists after reload', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const token = await getAdminToken(request, page);
      const { body: teamsBody } = await apiJson(request, token, 'GET', '/api/teams');
      const team = (teamsBody?.data || []).find((x) => x?.name === 'Galáticos' || x?.name === 'Galaticos');
      const { body: pBody } = await apiJson(request, token, 'GET', `/api/championships/${champId}/players`);
      const playerId = String(pBody?.data?.[0]?._id);
      const opponent = `E2E Life Edit ${Date.now()}`;
      const { response: mRes, body: mBody } = await apiJson(request, token, 'POST', '/api/matches', {
        'championship-id': champId,
        'home-team-id': String(team._id),
        date: '2026-05-01',
        opponent,
        'player-statistics': [{ 'player-id': playerId, 'team-id': String(team._id), goals: 1 }],
      });
      expect(mRes.ok(), JSON.stringify(mBody)).toBeTruthy();
      const matchId = String(mBody?.data?._id);

      await page.goto(`/#/matches/${matchId}/edit`);
      await expect(pageHeading(page, 'Editar Partida')).toBeVisible({ timeout: 15_000 });
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });

      const goalsCell = page.locator('table tbody tr').first().locator('td').nth(1);
      await goalsCell.getByRole('button', { name: '+' }).click();
      await goalsCell.getByRole('button', { name: '+' }).click();

      const updateResp = page.waitForResponse((r) => {
        if (r.request().method() !== 'PUT') return false;
        try {
          return new URL(r.url()).pathname === `/api/matches/${matchId}`;
        } catch {
          return false;
        }
      }, { timeout: 20_000 });
      await page.getByRole('button', { name: 'Atualizar' }).click();
      const put = await updateResp;
      expect(put.ok(), await put.text()).toBeTruthy();

      await page.goto(`/#/matches/${matchId}`);
      await expect(mainContent(page).getByRole('heading', { name: opponent, level: 2 })).toBeVisible({
        timeout: 15_000,
      });
      await expect(mainContent(page).getByText(/Gols \(jogadores\):\s*3/)).toBeVisible({ timeout: 10_000 });

      await page.reload();
      await expect(mainContent(page).getByText(/Gols \(jogadores\):\s*3/)).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('matches-by-championship hub lists created match', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const token = await getAdminToken(request, page);
      const { body: teamsBody } = await apiJson(request, token, 'GET', '/api/teams');
      const team = (teamsBody?.data || []).find((x) => x?.name === 'Galáticos' || x?.name === 'Galaticos');
      const { body: pBody } = await apiJson(request, token, 'GET', `/api/championships/${champId}/players`);
      const playerId = String(pBody?.data?.[0]?._id);
      const opponent = `E2E Life Hub ${Date.now()}`;
      await apiJson(request, token, 'POST', '/api/matches', {
        'championship-id': champId,
        'home-team-id': String(team._id),
        date: '2026-05-02',
        opponent,
        'player-statistics': [{ 'player-id': playerId, 'team-id': String(team._id), goals: 1 }],
      });

      await page.goto(`/#/matches/championship/${champId}`);
      await expect(bannerHeading(page, 'Partidas')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(opponent)).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
