# Metodologia de auditoria de performance

## Base URL e pré-requisitos

1. Subir o backend e o frontend conforme o `README.md` da raiz do repositório (ex.: app em `http://localhost:3000`).
2. Confirmar que a raiz responde (`HTTP 200`) antes de rodar o Lighthouse.

## Lighthouse: o que medir

- Categoria **Performance** cobre carregamento, TBT, LCP, CLS, etc.
- Para comparar telas de forma consistente, anotar sempre: **modo** (mobile ou desktop), **throttling** (padrão Lighthouse ou “no throttling” apenas para debug local), **build** (dev watch vs release) e **data da medição**.

### Opção 1: Chrome DevTools (recomendado para rotas autenticadas)

1. Abrir `http://localhost:3000`, fazer login com um utilizador válido.
2. Navegar até a rota a auditar e **esperar** listas/gráficos carregarem (estado estável).
3. Abrir DevTools → aba **Lighthouse** → marcar **Performance** → gerar relatório.
4. Copiar para [backlog-acoes.md](../../a-fazer/performance/backlog-acoes.md): **score**, **LCP**, **TBT**, **CLS** e as **top opportunities** (ex.: “Reduce JavaScript execution time”).
5. Repetir por URL listada no [inventario-paginas.md](inventario-paginas.md).

Vantagem: o `localStorage` já contém o JWT; não é preciso automação.

### Opção 2: Lighthouse CLI (rota pública e sanity checks)

Útil principalmente para **`/` (login)**, onde não há exigência de sessão.

Exemplo (só categoria Performance, saída JSON):

```bash
npx lighthouse http://localhost:3000/ \
  --only-categories=performance \
  --output=json \
  --output-path=./lighthouse-login.json \
  --quiet
```

Para extrair o score com Node (após gerar o JSON):

```bash
node -e "const j=require('./lighthouse-login.json'); console.log(Math.round(j.categories.performance.score*100));"
```

**Rotas protegidas:** o CLI, por omissão, **não** injeta `galaticos.auth.token`. Sem passos extra (ver opção B abaixo), o resultado não reflete a tela logada.

### WSL e “Unable to connect to Chrome”

Em ambientes WSL, o Lighthouse pode tentar usar um Chrome instalado no Windows e falhar ao ligar ao DevTools (`ECONNREFUSED` na porta local). O log do Launcher pode mostrar `chrome.exe` em `/mnt/c/...` mesmo com `--chrome-path` passado ao CLI.

**Mitigação:** definir a variável de ambiente **`CHROME_PATH`** para o Chrome **Linux** (o launcher dá precedência a este valor). Instalação prática do binário:

```bash
npx @puppeteer/browsers install chrome@stable
# Exportar o caminho impresso, ex.:
export CHROME_PATH="$PWD/chrome/linux-147.0.7727.24/chrome-linux64/chrome"

CHROME_PATH="$CHROME_PATH" npx lighthouse http://localhost:3000/ \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --disable-dev-shm-usage" \
  --only-categories=performance \
  --output=json \
  --output-path=./docs/perf-output/lighthouse-login.json
```

Recomenda-se ignorar a pasta `chrome/` no Git (ver `.gitignore`).

### Build `release` com Docker Dev

O `js/compiled` em desenvolvimento costuma estar num **volume** Docker (`galaticos-compiled`), não só no diretório do host. Para gerar o bundle minificado que o container serve:

```bash
docker compose -f config/docker/docker-compose.dev.yml exec -T frontend-watch npx shadow-cljs release app
```

### Opção B: automação reprodutível (Lighthouse logado)

Há um script que liga o Chrome via `chrome-launcher`, faz login na UI (`admin` / `admin`, igual aos e2e) e corre Lighthouse nas URLs indicadas com `disableStorageReset`:

- Ficheiro: `scripts/performance/lighthouse-authenticated.cjs`
- Exemplo: `CHROME_PATH=... node scripts/performance/lighthouse-authenticated.cjs "/#/dashboard" "/#/stats"`

Para CI no futuro: preferir JWT obtido por API + `localStorage`, sem credenciais em repositório; reutilizar o mesmo fluxo Lighthouse+puppeteer.

## Fluxo resumido

```mermaid
flowchart LR
  inv[inventario_paginas]
  pub[Rota_publica]
  prot[Rota_protegida]
  jwt[localStorage_JWT]
  inv --> pub
  inv --> prot
  pub --> cli[Lighthouse_CLI]
  prot --> jwt
  jwt --> dt[Lighthouse_DevTools_logado]
  jwt --> auto[Script_futuro]
  cli --> bl[backlog_acoes]
  dt --> bl
  auto --> bl
```

## Consistência entre auditorias

- Usar o **mesmo** utilizador e volume de dados de teste quando possível (ex.: mesma equipa após seed).
- Para páginas com **listas grandes**, anotar quantidade aproximada de registos (impacta LCP e TBT).
- Após alterações relevantes no front, **re-medir** e atualizar a data na coluna de baseline em [backlog-acoes.md](../../a-fazer/performance/backlog-acoes.md).
