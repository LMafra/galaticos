const { test, expect } = require('@playwright/test');
const { saveCoverage } = require('./_helpers');

const FAKE_ID = '000000000000000000000001';

test.describe('Resource not found', { tag: '@validation' }, () => {
  test('player detail with invalid ID shows not found and Voltar', async ({ page }, testInfo) => {
    try {
      await page.goto(`/#/players/${FAKE_ID}`);
      await expect(page.getByText('Jogador não encontrado.')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('team detail with invalid ID shows not found and Voltar', async ({ page }, testInfo) => {
    try {
      await page.goto(`/#/teams/${FAKE_ID}`);
      await expect(page.getByText('Time não encontrado.')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });

  test('championship detail with invalid ID shows not found and Voltar', async ({ page }, testInfo) => {
    try {
      await page.goto(`/#/championships/${FAKE_ID}`);
      await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible({ timeout: 15_000 });
      // Same copy can appear inline and in toasts; scope to main to satisfy strict mode.
      await expect(
        page
          .locator('#main-content')
          .getByText(/Campeonato não encontrado\.|Erro ao carregar campeonato|Erro ao carregar partidas|Erro ao carregar inscritos/)
      ).toBeVisible();
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
