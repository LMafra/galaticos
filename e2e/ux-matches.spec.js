const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  pageHeading,
  expectBreadcrumb,
  waitSkeletonHidden,
  setupActiveChampionshipWithEnrolledGalaticosPlayer,
  fillMatchMinimal,
  apiJson,
  getAdminToken,
  mainContent,
  waitForMatchDraftOpponent,
} = require('./_helpers');

test.describe('UX matches form', { tag: '@ux' }, () => {
  test('match form uses steppers not spinbuttons for goals', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await expect(pageHeading(page, 'Nova Partida')).toBeVisible();
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });
      await expect(
        page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button', { name: '+' })
      ).toBeVisible();
      await expect(page.locator('input[type="number"]').first()).toHaveCount(0);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('match form shows championship context in header', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const token = await getAdminToken(request, page);
      const { body } = await apiJson(request, token, 'GET', `/api/championships/${champId}`);
      const champName = body?.data?.name;
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await expect(page.getByRole('banner').getByText(champName)).toBeVisible({ timeout: 15_000 });
      await expectBreadcrumb(page, ['Campeonatos', 'Partidas']);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('skeleton then table on new match navigation', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await waitSkeletonHidden(page);
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('picker shows only enrolled players', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const token = await getAdminToken(request, page);
      const { body } = await apiJson(request, token, 'GET', `/api/championships/${champId}/players`);
      const enrolledCount = (body?.data || []).length;
      expect(enrolledCount).toBeGreaterThan(0);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      const rows = page.locator('table tbody tr');
      await expect(rows).toHaveCount(enrolledCount, { timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('local draft restores after reload', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const opponent = `UX Draft ${Date.now()}`;
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      const opponentInput = mainContent(page).getByLabel(/^Adversário/).first();
      await opponentInput.fill(opponent);
      await opponentInput.press('Tab');
      await expect(opponentInput).toHaveValue(opponent);
      await page.waitForLoadState('networkidle');
      await waitForMatchDraftOpponent(page, champId, opponent);
      await page.reload();
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await expect(mainContent(page).getByLabel(/^Adversário/).first()).toHaveValue(opponent, { timeout: 10_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('stale draft banner when saved-at is old', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      const routeId = `new-${champId}`;
      await page.goto('/#/dashboard');
      await page.evaluate(
        ({ rid }) => {
          const payload = {
            'saved-at': Date.now() - 8 * 24 * 60 * 60 * 1000,
            'form-data': {
              opponent: 'Stale UX',
              date: '2026-01-01',
              'player-statistics': {},
            },
          };
          window.localStorage.setItem(`galaticos.match-draft.${rid}`, JSON.stringify(payload));
        },
        { rid: routeId }
      );
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await expect(page.getByText(/Rascunho antigo/i)).toBeVisible({ timeout: 10_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('match table headers use scope col', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      await expect(page.locator('table thead th[scope="col"]').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});

test.describe('UX matches mobile', { tag: ['@ux', '@ux-mobile'] }, () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('mobile shows FAB Guardar partida without horizontal scroll', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      const fab = page.getByRole('button', { name: 'Guardar partida' });
      await expect(fab).toBeVisible({ timeout: 15_000 });
      const overflow = await page.evaluate(() => {
        const el = document.documentElement;
        return el.scrollWidth > el.clientWidth + 2;
      });
      expect(overflow).toBe(false);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('mobile stepper buttons meet 44px touch target', async ({ page, request }, testInfo) => {
    try {
      const champId = await setupActiveChampionshipWithEnrolledGalaticosPlayer(request, page);
      await page.goto(`/#/matches/by-championship/${champId}/new`);
      await page.getByText('Carregando jogadores inscritos...').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
      const firstCard = mainContent(page).locator('div.space-y-3.md\\:hidden > div').first();
      await firstCard.getByRole('button').first().click();
      const plus = firstCard.getByRole('button', { name: '+' }).first();
      await expect(plus).toBeVisible({ timeout: 15_000 });
      const box = await plus.boundingBox();
      expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
