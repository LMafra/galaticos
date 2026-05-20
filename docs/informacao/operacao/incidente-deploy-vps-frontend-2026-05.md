# Incidente operacional: deploy na VPS, Clojars e carregamento do frontend (maio 2026)

Nota de **contexto e decisões** após resolver na produção (VPS) uma cadeia de problemas: **build Docker**, **imagem sem código novo**, **cache no browser**, e **falha do chunk lazy do ClojureScript**. Serve para futuras operações e para alinhar documentação com o que realmente ocorreu.

**Relacionado:** [vps-hospedeiro.md](vps-hospedeiro.md), [runbook-producao.md](runbook-producao.md), [`scripts/docker/prod-vps-build-app.sh`](../../../scripts/docker/prod-vps-build-app.sh), [`shadow-cljs.edn`](../../../shadow-cljs.edn), [`src-cljs/galaticos/lazy_pages.cljs`](../../../src-cljs/galaticos/lazy_pages.cljs).

---

## 1. Sintomas observados

1. **`bin/galaticos db:seed`** (e scripts Python via Docker) falhavam com MongoDB **`Unauthorized` / `Command aggregate requires authentication`** quando a URI era só `mongodb://localhost:27017` sem credenciais — o Mongo em prod tinha auth; faltava URI com utilizador ou carregar `config/docker/.env` no fluxo do seed (corrigido em `scripts/database/seed.sh`).

2. **Build na VPS** (`docker compose … build app`): **`Connect timed out`** a **`repo.clojars.org`** durante **`RUN clj -P`** ou passos seguintes — rede do **bridge** do Docker (ou BuildKit) na VPS pior que a do host.

3. Depois de **`docker build --network host`**, **`clj -P`** podia passar e **`clj -M:build:frontend prod`** ainda falhar com timeout ao Clojars — **`docker compose build`** com `build.network: host` no YAML **não** garante o mesmo comportamento em **todos** os passos BuildKit.

4. **`./bin/galaticos docker:prod deploy`** (sem `--no-cache`) concluía em segundos com **todas as camadas `CACHED`** — **nenhum código novo** no JAR; o browser continuava com UI antiga se o utilizador esperava alterações de outro branch.

5. **`git log` na VPS** mostrava apenas o merge esperado em **`main`**; alterações de UI noutro branch **não** apareciam até merge + pull + novo build.

6. **Janela anónima** mostrava layout novo; o **browser habitual** não — típico de **cache** de `app.js` / assets (Ctrl+F5 nem sempre basta; “limpar dados do site” costuma resolver).

7. Erros na UI: **`Falha ao carregar módulo: Error loading pages: Consecutive load failures`** — o runtime **shadow.lazy** tentava carregar o segundo bundle **`/js/compiled/pages.js`**; o pedido falhava (rede, cache incoerente entre `app.js` e `pages.js`, proxy, extensão, etc.).

---

## 2. Causas em raiz (resumo)

| Área | Causa |
|------|--------|
| Mongo / seed | URI sem auth; script de seed não lia `config/docker/.env` como outros scripts. |
| Build Clojars | Timeout HTTPS da rede do **container** (bridge / BuildKit) para Clojars; intermitente. |
| “Deploy não muda nada” | **Cache de layers** Docker + código em `main` sem os commits esperados. |
| Browser | Cache agressivo ou perfil com extensões; **dois ficheiros JS** (`app.js` + `pages.js`) aumentam a probabilidade de **estado inconsistente**. |
| Loader Shadow | Dependência de **`pages.js`** em runtime; falha = rotas pesadas quebradas. |

---

## 3. Mitigações e alterações no repositório

### 3.1. Seed e Mongo

- **`scripts/database/seed.sh`**: se `MONGO_URI` for o default e existir **`config/docker/.env`** com `MONGO_INITDB_ROOT_*`, montar URI com **`authSource=admin`** (alinhado a `setup.sh` / `reset.sh`). Log da URI com password **redigida**.

### 3.2. Build e deploy na VPS

- **`config/docker/docker-compose.prod.yml`**: `build.network: host` no serviço `app` (ajuda quem usa **só** `docker compose build`; não resolve todos os casos BuildKit).
- **`scripts/docker/prod-vps-build-app.sh`** + comandos **`./bin/galaticos docker:prod …`**: `deploy`, `deploy:clean`, `build`, `build:app`, etc. passam a usar **`docker build --network host`** para o **`Dockerfile.prod`**, depois **`docker compose up`** para recriar o `app` — **trajetória por defeito** fiável para Clojars na VPS.
- Documentação atualizada em **`vps-hospedeiro.md`** e **`runbook-producao.md`** (fluxo `deploy`, MTU, CI).

### 3.3. Frontend: fim do code-split `:pages` (decisão estável)

Para **eliminar** a segunda ida ao servidor (**`pages.js`**) e os erros **“Consecutive load failures”**:

- **`shadow-cljs.edn`**: um único módulo **`:app`** com todas as entradas das páginas pesadas; removidos **`module-loader`** e o módulo **`:pages`**.
- **`src-cljs/galaticos/lazy_pages.cljs`**: `require` direto dos namespaces de página; **`loadable-route`** passa a ser apenas **`(into [comp] args)`** (sem `shadow.lazy`).
- **`.clj-kondo/config.edn`**: removida a exceção específica de `lazy-pages`.

**Trade-off:** o primeiro download de JS é **maior** (tudo no `app.js`); ganha-se **robustez** em produção (menos pontos de falha, menos confusão com cache de dois bundles).

---

## 4. Checklist rápido para a próxima vez

1. **`main`** (ou branch de deploy) contém **mesmo** o commit com alterações de UI.  
2. Na VPS: **`git pull`**, depois **`./bin/galaticos docker:prod deploy:clean`** se mudaram dependências ou há dúvida sobre cache; caso contrário **`deploy`**.  
3. Se o build falhar ao Clojars: rede host já está no fluxo; ver **MTU** / **CI+registry** em `vps-hospedeiro.md` §4.  
4. Após deploy: **limpar dados do site** ou janela privada para validar; confirmar **`/health`** e um pedido a **`/js/compiled/app.js`** (200).  
5. Já **não** é necessário validar **`/js/compiled/pages.js`** no bundle de release atual (ficheiro deixa de existir nesse pipeline).

---

## 5. Estado após o incidente

- Deploy de aplicação em VPS documentado com scripts e **build em rede host** por defeito.  
- Frontend de produção **monólito CLJS** (sem chunk `pages` lazy), o que corresponde ao comportamento “**funcionou**” reportado após o merge e o novo deploy.

Data de referência dos sintomas e correções: **maio de 2026** (repositório Galáticos).
