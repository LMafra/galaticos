const { test, expect } = require('@playwright/test');
const { saveCoverage, pageHeading, expectPageTitle, formFieldAlert } = require('./_helpers');

test.describe('Required fields validation', { tag: '@validation' }, () => {
  test('player form shows error when name or position is missing', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/players');
      await expect(pageHeading(page, 'Jogadores')).toBeVisible();
      await page.getByRole('button', { name: 'Novo Jogador' }).click();
      await expect(page.getByRole('heading', { name: 'Novo Jogador' })).toBeVisible();

      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(formFieldAlert(page, 'Nome é obrigatório')).toBeVisible();
      await expect(formFieldAlert(page, 'Posição é obrigatória')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('team form shows error when name is missing', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/teams');
      await expect(page).toHaveURL(/\/#\/teams/);
      await expectPageTitle(page, 'Times');
      await page.getByRole('button', { name: 'Novo Time' }).click();
      await expect(page.getByRole('heading', { name: 'Novo Time' })).toBeVisible();

      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(formFieldAlert(page, 'Nome é obrigatório')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('championship form shows error when name or season is missing', async ({ page }, testInfo) => {
    try {
      await page.goto('/#/championships');
      await expect(pageHeading(page, 'Campeonatos')).toBeVisible();
      await page.getByRole('button', { name: 'Novo Campeonato' }).click();
      await expect(page.getByRole('heading', { name: 'Novo Campeonato' })).toBeVisible();

      await page.getByRole('button', { name: 'Criar' }).click();
      await expect(formFieldAlert(page, 'Nome é obrigatório')).toBeVisible();
      await expect(formFieldAlert(page, 'Temporada é obrigatória')).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
