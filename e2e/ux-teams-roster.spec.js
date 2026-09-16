const { test, expect } = require('@playwright/test');
const {
  saveCoverage,
  getAdminToken,
  apiJson,
  pickPlayerInSearchAddPanel,
  mainContent,
} = require('./_helpers');

test.describe('UX teams roster', { tag: '@ux' }, () => {
  test('add player to team via picker appears in elenco then remove', async ({
    page,
    request,
  }, testInfo) => {
    try {
      const token = await getAdminToken(request, page);
      expect(token).toBeTruthy();
      const unique = Date.now();
      const playerName = `E2E Roster Free ${unique}`;

      const { response: tRes, body: tBody } = await apiJson(request, token, 'POST', '/api/teams', {
        name: `E2E Roster Team ${unique}`,
        abbreviation: `R${String(unique).slice(-3)}`,
        category: 'Adulto',
      });
      expect(tRes.ok(), JSON.stringify(tBody)).toBeTruthy();
      const teamId = String(tBody?.data?._id);
      expect(teamId).toBeTruthy();

      const { response: pRes, body: pBody } = await apiJson(request, token, 'POST', '/api/players', {
        name: playerName,
        position: 'Atacante',
      });
      expect(pRes.ok(), JSON.stringify(pBody)).toBeTruthy();

      await page.goto(`/#/teams/${teamId}`);
      await expect(page.getByRole('heading', { level: 2 }).first()).toBeVisible({ timeout: 15_000 });
      await expect(mainContent(page).getByText('Elenco do time')).toBeVisible();
      await expect(mainContent(page).getByText(/Nenhum jogador no time/i)).toBeVisible();

      await pickPlayerInSearchAddPanel(page, playerName, 'Adicionar');
      const rosterRow = mainContent(page)
        .locator('table tbody tr')
        .filter({ hasText: playerName });
      await expect(rosterRow).toBeVisible({ timeout: 15_000 });
      await expect(mainContent(page).getByText(/Nenhum jogador no time/i)).toHaveCount(0);

      const removeResp = page.waitForResponse((r) => {
        try {
          const path = new URL(r.url()).pathname;
          return (
            r.request().method() === 'DELETE' &&
            path.includes(`/api/teams/${teamId}/players/`)
          );
        } catch {
          return false;
        }
      }, { timeout: 15_000 });
      await rosterRow.getByRole('button', { name: 'Remover' }).click();
      const del = await removeResp;
      expect(del.ok(), await del.text()).toBeTruthy();

      await expect(
        mainContent(page).locator('table tbody tr').filter({ hasText: playerName })
      ).toHaveCount(0, { timeout: 10_000 });
      await expect(
        mainContent(page).getByText(/Nenhum jogador no time|Jogadores:\s*0/i).first()
      ).toBeVisible({ timeout: 10_000 });
    } finally {
      await saveCoverage(page, testInfo);
    }
  });
});
