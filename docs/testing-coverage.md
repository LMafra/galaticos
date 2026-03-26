# Cobertura de Testes - Galáticos

Este documento descreve como executar, interpretar e manter a cobertura de testes do projeto Galáticos.

## 📊 Requisitos de Cobertura

O projeto Galáticos mantém os seguintes requisitos de cobertura de código:

- **Cobertura de Linhas**: ≥ 80%
- **Cobertura de Branches**: ≥ 70%

Estes thresholds são validados automaticamente no CI/CD e bloqueiam merges de Pull Requests que não atendam aos requisitos.

## ✅ Estratégia de testes para Sports Data Analytics

Além da cobertura de código, a trilha de analytics exige validação contínua de qualidade e consistência de dados.

### 1) Testes de contrato de dados

- Validar shape mínimo de `matches.player-statistics`.
- Validar estrutura de `players.aggregated-stats`.
- Cobrir cenários de campos ausentes, tipos inválidos e valores negativos.

### 2) Testes de reconciliação

- Garantir que `aggregated-stats` seja reprodutível a partir de `matches`.
- Testar recálculo completo e recálculo parcial por partida.
- Garantir que divergências sejam detectadas e reportadas.

### 3) Testes de regressão de métricas

- Criar fixtures determinísticas e validar métricas-chave:
  - `games`
  - `goals`
  - `assists`
  - `goals-per-game`
  - `assists-per-game`
- Proteger fórmulas contra mudanças acidentais.

### 4) Critério de aceite para mudanças analíticas

Toda alteração em cálculo de métrica ou contrato de dados deve:

1. Atualizar `docs/analytics/metrics-catalog.md` e/ou `docs/analytics/data-contracts.md`.
2. Incluir teste de regressão correspondente.
3. Validar reconciliação sem divergências críticas.

### 5) Checklist de mudança obrigatória (métrica/contrato)

- [ ] Catálogo de métricas atualizado (`docs/analytics/metrics-catalog.md`).
- [ ] Contrato de dados atualizado (`docs/analytics/data-contracts.md`) quando houver mudança de schema.
- [ ] Testes de contrato e regressão adicionados/atualizados.
- [ ] Evidência de reconciliação registrada no runbook operacional.

## 🚀 Executando Cobertura Localmente

### Cobertura Backend (Clojure)

Execute a cobertura do backend com:

```bash
./bin/galaticos coverage
```

Este comando:
- Executa todos os testes backend
- Coleta dados de cobertura usando Cloverage
- Valida os thresholds (80% linhas, 70% branches)
- Gera relatório HTML em `target/coverage/index.html`
- **Falha** se os thresholds não forem atingidos

**Visualizar relatório:**
```bash
open target/coverage/index.html  # macOS
xdg-open target/coverage/index.html  # Linux
start target/coverage/index.html  # Windows
```

### Cobertura E2E (Playwright)

Execute a cobertura E2E com:

```bash
./bin/galaticos coverage:e2e
```

**Nota:** A cobertura E2E requer:
1. Aplicação rodando em `http://localhost:3000`
2. Código instrumentado com istanbul/nyc (opcional, veja seção de setup avançado)

Para uma experiência completa:
```bash
# Terminal 1: Iniciar aplicação
./bin/galaticos docker:dev start

# Terminal 2: Executar testes E2E
./bin/galaticos coverage:e2e
```

### Cobertura Completa (Backend + E2E)

Execute toda a cobertura de uma vez:

```bash
./bin/galaticos coverage:all
```

Este comando:
- Executa cobertura backend
- Executa cobertura E2E
- Gera relatório consolidado em `target/coverage-report/index.html`
- Valida thresholds
- **Falha** se o backend não atingir os thresholds

**Visualizar relatório consolidado:**
```bash
open target/coverage-report/index.html
```

## 📖 Interpretando Relatórios

### Relatório Backend (Cloverage)

O relatório HTML mostra:

- **Verde**: Linhas cobertas por testes
- **Vermelho**: Linhas não cobertas
- **Amarelo**: Branches parcialmente cobertos

#### Métricas importantes:

- **Lines**: Porcentagem de linhas executadas
- **Branches**: Porcentagem de branches (if/else, case, etc.) testados
- **Forms**: Porcentagem de expressões Clojure cobertas

#### Exemplo de interpretação:

```
Lines: 85.3% (240/281)     ✅ Acima de 80%
Branches: 72.1% (49/68)    ✅ Acima de 70%
Forms: 88.9% (320/360)     ℹ️  Informativo
```

### Relatório E2E

O relatório E2E (quando disponível) mostra a cobertura do código JavaScript compilado executado durante os testes de ponta a ponta.

**Interpretação:**
- Complementa a cobertura backend
- Verifica se fluxos completos estão sendo testados
- Identifica código frontend não exercitado

## 🔧 O Que Fazer Quando a Cobertura Está Baixa

### 1. Identifique áreas não cobertas

Abra o relatório HTML e procure por:
- Arquivos com baixa cobertura (< 80%)
- Funções completamente não testadas (0%)
- Branches não cobertos (if/else faltando)

### 2. Priorize por importância

Foque em adicionar testes para:

**Alta prioridade:**
- Lógica de negócio crítica
- Handlers de API
- Validações de dados
- Cálculos e transformações

**Média prioridade:**
- Utilitários e helpers
- Formatadores
- Parsers

**Baixa prioridade (pode ser excluído):**
- Código de setup/configuração
- Main/entry points
- Código puramente declarativo

### 3. Escreva testes focados

Para aumentar cobertura de forma eficaz:

```clojure
;; ❌ Evite: Testes genéricos que não exercitam branches
(deftest test-handler
  (is (= 200 (:status (handler {})))))

;; ✅ Melhor: Testes que cobrem diferentes casos
(deftest test-handler-success
  (is (= 200 (:status (handler {:valid true})))))

(deftest test-handler-validation-error
  (is (= 400 (:status (handler {:invalid true})))))

(deftest test-handler-not-found
  (is (= 404 (:status (handler {:id "nonexistent"})))))
```

### 4. Execute cobertura incrementalmente

Após adicionar testes:

```bash
# Execute apenas para verificar progresso
./bin/galaticos coverage

# Veja se a cobertura aumentou
open target/coverage/index.html
```

## 🎯 Exclusões de Cobertura

Alguns arquivos são excluídos da análise de cobertura por design:

### Arquivos excluídos automaticamente:

- `galaticos.core`: Entry point da aplicação
- Arquivos em `dev/`: Código de desenvolvimento
- Código de configuração e setup

### Como excluir um arquivo:

Se você precisa excluir um arquivo específico da cobertura, edite `deps.edn`:

```clojure
:coverage {:extra-paths ["test"]
           :extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
           :main-opts ["-m" "cloverage.coverage"
                       ;; ... outras opções ...
                       "--ns-exclude-regex" "galaticos.core|galaticos.setup|galaticos.dev.*"]}
```

**Regex de exclusão:**
- Separe namespaces com `|` (pipe)
- Use `.*` para excluir todos os sub-namespaces
- Exemplo: `galaticos.dev.*` exclui `galaticos.dev.fixtures`, `galaticos.dev.utils`, etc.

### Quando excluir:

✅ **Válido excluir:**
- Entry points (main functions)
- Código de configuração puro
- Fixtures complexas de teste
- Código de desenvolvimento/debugging

❌ **Não excluir:**
- Lógica de negócio
- Validações
- Cálculos
- Qualquer código que execute em produção

## 🔄 CI/CD e Enforcement

### Como funciona no CI:

1. **Pull Request é aberto**
2. **GitHub Actions executa** workflow `test-coverage.yml`
3. **Backend coverage é validado** (80/70)
4. **E2E tests são executados**
5. **Relatórios são gerados** e salvos como artifacts
6. **Check passa ou falha** baseado nos thresholds

### Se o check falhar:

1. Veja o log do GitHub Actions
2. Baixe os artifacts (coverage reports)
3. Identifique o que não está coberto
4. Adicione testes
5. Push das alterações

### Visualizando no GitHub:

- ✅ Check verde: Cobertura OK, pode fazer merge
- ❌ Check vermelho: Cobertura abaixo do threshold, merge bloqueado
- 🟡 Check amarelo: E2E opcional falhou, mas backend passou

### Artifacts disponíveis:

Após cada execução do CI, os seguintes artifacts estão disponíveis:

- `backend-coverage-report`: Relatório HTML completo do backend
- `e2e-coverage-report`: Dados de cobertura E2E (se disponível)
- `playwright-report`: Relatório de execução dos testes E2E

## 🏗️ Setup Avançado: Cobertura E2E Completa

A cobertura E2E básica está configurada, mas para cobertura completa do código JavaScript, siga:

### 1. Instalar ferramentas adicionais

```bash
npm install --save-dev nyc istanbul-lib-coverage babel-plugin-istanbul
```

### 2. Configurar NYC

Crie `.nycrc.json`:

```json
{
  "all": true,
  "include": ["resources/public/js/compiled/**/*.js"],
  "exclude": [
    "**/*.test.js",
    "**/node_modules/**"
  ],
  "reporter": ["html", "text", "json"],
  "report-dir": "playwright-coverage/report",
  "temp-dir": "playwright-coverage",
  "check-coverage": false,
  "lines": 70,
  "statements": 70,
  "functions": 70,
  "branches": 60
}
```

### 3. Instrumentar código ClojureScript

No `shadow-cljs.edn`, adicione para builds de teste:

```clojure
:app {:target :browser
      :output-dir "resources/public/js/compiled"
      ;; Para testes:
      :compiler-options {:instrument true}
      ;; ... resto da config
      }
```

### 4. Coletar cobertura no Playwright

Crie helper em `e2e/helpers/coverage.js`:

```javascript
export async function startCoverage(page) {
  await page.coverage.startJSCoverage();
}

export async function saveCoverage(page, testName) {
  const coverage = await page.coverage.stopJSCoverage();
  // Salvar em playwright-coverage/
  await fs.writeFile(
    `playwright-coverage/coverage-${testName}.json`,
    JSON.stringify(coverage)
  );
}
```

Use nos testes:

```javascript
import { startCoverage, saveCoverage } from './helpers/coverage';

test('user login flow', async ({ page }) => {
  await startCoverage(page);
  
  // ... seu teste ...
  
  await saveCoverage(page, 'login-flow');
});
```

## 📚 Boas Práticas

### ✅ Faça:

- Execute cobertura **antes** de abrir PRs
- Adicione testes para **cada novo arquivo**
- Mantenha cobertura **consistente** (não deixe cair)
- Foque em **qualidade** dos testes, não apenas números
- Revise relatórios **periodicamente**

### ❌ Não faça:

- Não escreva testes "vazios" só para aumentar cobertura
- Não exclua código importante da cobertura
- Não ignore branches não cobertos
- Não confie apenas em testes E2E para cobertura backend

## 🆘 Troubleshooting

### "Coverage failed to meet thresholds"

**Causa:** Código não está suficientemente testado.

**Solução:**
1. Execute: `./bin/galaticos coverage`
2. Abra: `target/coverage/index.html`
3. Identifique áreas vermelhas
4. Adicione testes
5. Repita até atingir 80/70

### "No coverage data found"

**Causa:** Testes não foram executados ou falharam antes da coleta.

**Solução:**
1. Execute: `./bin/galaticos test` (sem coverage)
2. Certifique-se de que testes passam
3. Depois execute: `./bin/galaticos coverage`

### "E2E coverage not collected"

**Causa:** Código não está instrumentado ou aplicação não está rodando.

**Solução:**
1. Certifique-se de que a app está rodando
2. Verifique se consegue acessar `http://localhost:3000`
3. Para cobertura E2E completa, veja "Setup Avançado" acima

### "Cloverage timeout"

**Causa:** Suite de testes muito lenta.

**Solução:**
1. Otimize testes lentos
2. Remova sleeps/delays desnecessários
3. Use fixtures em vez de setup repetido
4. Considere paralelização (com cautela)

## 📈 Monitoramento de Tendências

### Verificação mensal:

```bash
# Execute cobertura completa
./bin/galaticos coverage:all

# Compare com mês anterior
# Procure por:
# - Cobertura geral: aumentou ou diminuiu?
# - Novos arquivos: estão bem cobertos?
# - Áreas críticas: mantêm >90%?
```

### Integrações opcionais:

- **Codecov**: Badge de cobertura no README
- **Coveralls**: Tracking de tendências
- **SonarQube**: Análise de qualidade + cobertura

## 🔗 Recursos Adicionais

- [Cloverage Documentation](https://github.com/cloverage/cloverage)
- [Playwright Coverage](https://playwright.dev/docs/api/class-coverage)
- [NYC (Istanbul) Documentation](https://github.com/istanbuljs/nyc)
- [Guia de Contribuição](../CONTRIBUTING.md)

## 📞 Suporte

Se você tiver dúvidas sobre cobertura de testes:

1. Consulte este documento primeiro
2. Veja exemplos em `test/` e `test-cljs/`
3. Pergunte no canal de desenvolvimento do time
4. Abra uma issue para melhorias nesta documentação

