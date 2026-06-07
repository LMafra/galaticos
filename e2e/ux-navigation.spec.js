const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  expectBreadcrumb,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  fillMatchMinimal,
  pageHeading,
  getAdminToken,
  apiJson,
} = require('./_helpers');

test.describe('UX navigation and context', { tag: '@ux' }, () => {
  test('new match in championship shows breadcrumb trail', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const token = await getAdminToken(request, page);
      const { body } = await apiJson(request, token, 'GET', `/api/championships/${champId}`);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await expectBreadcrumb(page, ['Campeonatos', 'Partidas', body?.data?.name || 'Campeonato', 'Nova Partida']);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('team detail shows Times breadcrumb', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      const cards = page.locator('main .app-card');
      await cards.first().waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
      if ((await cards.count()) === 0) {
        test.skip(true, 'No teams');
        return;
      }
      await cards.first().click();
      await expectBreadcrumb(page, ['Times']);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('match return navigates to championship after save', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/championships/${champId}`);
      await page.evaluate(
        ({ id }) => {
          window.sessionStorage.setItem(
            'galaticos.match-return',
            JSON.stringify({ route: 'championship-detail', params: { id } })
          );
        },
        { id: champId }
      );
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await fillMatchMinimal(page, { opponent: `Return UX ${Date.now()}` });
      await page.getByRole('button', { name: 'Criar Partida' }).click();
      await expect(page).toHaveURL(new RegExp(`/#/championships/${champId}`), { timeout: 20_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('browser back restores players list scroll', async ({ page }, testInfo) => {
    try {
      await page.setViewportSize({ width: 1280, height: 480 });
      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
      const rows = page.locator('table tbody tr');
      await rows.first().waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
      const rowCount = await rows.count();
      if (rowCount === 0) {
        test.skip(true, 'No players');
        return;
      }
      await page.evaluate(() => {
        const sh = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
        window.scrollTo(0, Math.max(0, sh - window.innerHeight));
      });
      await page.waitForTimeout(400);
      const scrollBefore = await page.evaluate(() => {
        const y = Math.max(window.scrollY, document.documentElement.scrollTop);
        window.sessionStorage.setItem('galaticos.scroll.players', String(y));
        return y;
      });
      if (scrollBefore < 50) {
        test.skip(true, 'Players list too short for scroll restore');
        return;
      }
      await rows.nth(rowCount - 1).click();
      await page.goBack();
      await expect(pageHeading(page, 'Jogadores')).toBeVisible({ timeout: 10_000 });
      const minRestored = Math.max(50, Math.floor(scrollBefore * 0.85));
      await expect
        .poll(
          () =>
            page.evaluate(() =>
              Math.max(window.scrollY, document.documentElement.scrollTop)
            ),
          { timeout: 10_000 }
        )
        .toBeGreaterThan(minRestored);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('stats filters persist in sessionStorage', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/stats');
      await expect(page).toHaveURL(/\/#\/stats/);
      await expect(page.getByText('Filtros globais')).toBeVisible({ timeout: 15_000 });
      const stored = await page.evaluate(() => window.sessionStorage.getItem('galaticos.stats.global-filters'));
      expect(stored).toBeTruthy();
      await page.reload();
      const after = await page.evaluate(() => window.sessionStorage.getItem('galaticos.stats.global-filters'));
      expect(after).toBe(stored);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('hash PT routes (UX-PLAN-24 phase 1)', async ({ page }, testInfo) => {
    try {
      const pairs = [
        ['/jogadores', 'Jogadores'],
        ['/partidas', 'Partidas'],
        ['/campeonatos', 'Campeonatos'],
        ['/estatisticas', 'Estatísticas'],
      ];
      for (const [path, heading] of pairs) {
        await page.goto(`/#${path}`);
        await expect(page).toHaveURL(new RegExp(`#${path.replace(/\//g, '\\/')}`));
        await expect(pageHeading(page, heading)).toBeVisible({ timeout: 15_000 });
      }
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
