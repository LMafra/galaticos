const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

test('dashboard loads and shows overview stats', { tag: '@smoke' }, async ({ page }, testInfo) => {
  try {
    await test.step('load dashboard', async () => {
      await page.goto('/#/dashboard');
    });

    await test.step('verify heading and overview', async () => {
      await expect(page.getByText('Visão geral')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
    });

    await test.step('verify stats content', async () => {
      const body = page.locator('body');
      await expect(body).toContainText('Dashboard');
      const hasStats =
        (await body.getByText('Jogadores').count()) > 0 ||
        (await body.getByText('Partidas').count()) > 0 ||
        (await body.getByText('Gols').count()) > 0 ||
        (await body.getByText('Campeonatos').count()) > 0;
      expect(hasStats).toBeTruthy();
    });
  } finally {
    await saveCoverage(page, testInfo);
  }
});

test.describe('Dashboard card navigation', { tag: '@navigation' }, () => {
  test('clicking Jogadores card navigates to players list', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
      await page.locator('main').getByRole('button').filter({ hasText: 'Jogadores' }).first().click();
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('clicking Partidas card navigates to matches list', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
      await page.locator('main').getByRole('button').filter({ hasText: 'Partidas' }).first().click();
      await expect(page.getByRole('heading', { name: 'Partidas', level: 1 })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('clicking Campeonatos card navigates to championships list', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
      await page.locator('main').getByRole('button').filter({ hasText: 'Campeonatos' }).first().click();
      await expect(page.getByRole('heading', { name: 'Campeonatos', level: 1 })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('clicking Gols card navigates to players list', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/dashboard');
      await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
      await page.locator('main').getByRole('button').filter({ hasText: 'Gols' }).first().click();
      await expect(page.getByRole('heading', { name: 'Jogadores', level: 1 })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
