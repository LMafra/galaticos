# Operação em VPS (ex.: Kinghost) e domínio externo (ex.: Locaweb)

Runbook prático das interações típicas entre **máquina local**, **painel do hospedeiro** e **servidor Ubuntu** onde o Galáticos roda em Docker de produção. Complementa o [runbook-producao.md](runbook-producao.md), que foca em dados e comandos genéricos.

**Premissas usadas neste guia**

- Código em `/opt/galaticos` na VPS.
- Stack: [config/docker/docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml).
- Domínio de exemplo: `galaticosfr.com.br` / `www` na Locaweb; hostname da VPS: `galaticosfr.vps-kinghost.net` (ajuste aos seus registros).

---

## 1. Acesso SSH

### Chave pública no painel da VPS

- No wizard do hospedeiro cola-se só a chave **pública** (ficheiro `.pub`).
- A **privada** fica no teu PC; nunca enviar para o painel nem para repositórios.

### Duas chaves (ex.: GitHub + VPS)

Usar `~/.ssh/config` para não misturar identidades:

```sshconfig
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519
  IdentitiesOnly yes

Host galaticos-vps
  HostName galaticosfr.vps-kinghost.net
  User root
  IdentityFile ~/.ssh/id_ed25519_galaticos
  IdentitiesOnly yes
```

Ligar: `ssh galaticos-vps`.

### Aviso “REMOTE HOST IDENTIFICATION HAS CHANGED”

Após reinstalar o SO ou trocar imagem, a chave de host do servidor muda. No PC:

```bash
ssh-keygen -f ~/.ssh/known_hosts -R 'IP_OU_HOSTNAME_DA_VPS'
```

Depois volta a aceitar a nova fingerprint na primeira ligação.

### Senha

Se o painel diz “use a senha ou a chave configurada”, a senha é a **que definiste** no painel (ou só existe acesso por chave). Não há senha genérica nessa mensagem.

---

## 2. Variáveis de ambiente e ficheiro `.env` do Compose

### Onde colocar o `.env`

O ficheiro [docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml) está em `config/docker/`. O Docker Compose resolve variáveis a partir do **diretório do ficheiro compose** (ou do projeto); o mais simples é:

**`config/docker/.env`**

(na raiz do repo: `galaticos/config/docker/.env`)

Evita o problema de ter `.env` só em `/opt/galaticos/.env` e o Compose **não** interpolar `JWT_SECRET` / `MONGO_INITDB_*`.

### Caracteres especiais no `.env`

- **`$`** é interpretado pelo Compose (ex.: `$vM` vira variável `vM`). Para um dólar literal na senha, usar **`$$`** ou evitar `$` na senha.
- **`@` e `:` na palavra-passe do Mongo** quebram o parsing de `mongodb://user:pass@host` na **DATABASE_URL**. Preferir palavra-passe **URL-safe** (letras, números, `_`, `-`) ou codificar a palavra-passe (percent-encoding) se montares a URI manualmente.

### Depois de alterar `.env`

Recriar os serviços que leem essas variáveis, por exemplo:

```bash
cd /opt/galaticos
docker compose -f config/docker/docker-compose.prod.yml up -d --force-recreate
```

---

## 3. Atualizar código e imagem Docker (deploy)

**`./bin/galaticos docker:prod restart` não aplica código novo**: só reinicia o mesmo container com a **mesma imagem**.

Fluxo correto após `git pull`:

```bash
cd /opt/galaticos
git fetch origin && git checkout main && git pull origin main
./bin/galaticos docker:prod deploy:clean
```

(`deploy:clean` usa `docker build --network host` e evita timeouts ao Clojars a meio do Dockerfile; ver secção 4. Para só cache incremental: `./bin/galaticos docker:prod deploy`.)

O MongoDB e o volume `mongodb-data-prod` mantêm-se; ver [runbook-producao.md](runbook-producao.md) para o que **nunca** fazer (`down -v` sem backup).

---

## 4. Build na VPS: timeout ao Clojars (`repo.clojars.org`)

**Scripts (a partir da raiz do repo):** ver [`scripts/docker/prod-vps-build-app.sh`](../../../scripts/docker/prod-vps-build-app.sh) e o wrapper `./bin/galaticos docker:prod …`:

| Situação | Comando típico |
|----------|------------------|
| Deploy normal (build em **rede host** + recriar `app`) | `./bin/galaticos docker:prod deploy` ou `deploy:clean` |
| Só construir imagem `app` (rede host) | `./bin/galaticos docker:prod build` / `build:app` / `build:app:clean` |
| Build **só** com `docker compose build app` (rede bridge; pode falhar a meio do Dockerfile no VPS) | `./bin/galaticos docker:prod build:app:compose` |
| Deploy com compose build (não recomendado na VPS com timeouts) | `./bin/galaticos docker:prod deploy:app:compose` |
| Dica MTU / daemon.json | `./bin/galaticos docker:prod hint:vps-docker-mtu` |
| Dica build em CI + registry | `./bin/galaticos docker:prod hint:vps-ci-build` |

Sintoma: `RUN clj …` no Dockerfile falha com **Connect timed out** a `repo.clojars.org` (por exemplo em `clj -M:build:frontend` **depois** de `clj -P` já ter passado), enquanto no **host** `curl https://repo.clojars.org` funciona.

Causa típica: rede do **bridge** do Docker na VPS (MTU, rota ou limite de concorrência) comporta-se pior que a rede do host.

### Opção A (preferida) — `./bin/galaticos docker:prod deploy`

Os comandos **`deploy`**, **`deploy:clean`**, **`build`**, **`build:app`**, **`deploy:app`** usam **`docker build --network host`** (script [`prod-vps-build-app.sh`](../../../scripts/docker/prod-vps-build-app.sh)), para que **todos** os passos `RUN clj …` do [Dockerfile.prod](../../../config/docker/Dockerfile.prod) vejam a rede do host. Isto evita o caso em que `docker compose build` + `build.network: host` no YAML ainda deixa **algum** passo BuildKit na bridge e o `clj -M:build:frontend` volta a dar **Connect timed out** ao Clojars.

Para forçar o caminho antigo (`docker compose build app`), usar **`build:app:compose`** / **`deploy:app:compose`** (não recomendado na VPS com timeouts).

### Opção A′ (YAML) — `build.network: host` no Compose

O [docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml) pode definir `build.network: host` no serviço `app`; ajuda quem corre **`docker compose … build app`** manualmente, mas **não** é tão fiável como o `docker build --network host` dos scripts acima em todas as versões Docker/BuildKit.

### Manual (sem `./bin/galaticos`, equivalente ao deploy com rede host)

```bash
cd /opt/galaticos
docker inspect galaticos-app-prod --format '{{.Config.Image}}'
```

Anotar o nome da imagem (ex.: `docker-app:latest`). Depois:

```bash
docker build --network host --no-cache \
  -f config/docker/Dockerfile.prod \
  -t docker-app:latest \
  .
docker compose -f config/docker/docker-compose.prod.yml up -d --force-recreate --no-deps app
```

Ajustar `-t` ao nome que o Compose espera, se for diferente (ou `PROD_APP_IMAGE=…` ao usar o script).

### Opção B — MTU do Docker

Em `/etc/docker/daemon.json`:

```json
{ "mtu": 1400 }
```

Reiniciar Docker: `systemctl restart docker`. Voltar a tentar o build.

### Opção C — Build fora da VPS

CI (ex.: GitHub Actions) faz `docker build`, **push** para um registry; na VPS só `docker pull` + `up`. Assim a VPS não depende de baixar dependências Clojure durante o build.

---

## 5. Nginx, HTTPS e DNS

### Proxy para a app

A app expõe `3000` no host; o Nginx deve fazer `proxy_pass` para `http://127.0.0.1:3000` com cabeçalhos `Host`, `X-Forwarded-For`, `X-Forwarded-Proto`.

### Let’s Encrypt (Certbot)

- Registos **A** de `@` e `www` devem apontar para o IP da VPS **antes** do desafio HTTP.
- Erro “no valid A records” na validação secundária: esperar propagação DNS global; confirmar com `dig @8.8.8.8` e `dig @1.1.1.1`.
- **AAAA** vazio é normal se não usares IPv6.

### 502 Bad Gateway

Quase sempre: app não escuta na 3000 (container a reiniciar, crash ao ligar ao Mongo, URI inválida). Ver `docker logs galaticos-app-prod` e `curl -sf http://127.0.0.1:3000/health` na VPS.

---

## 6. Seed com Excel e Mongo em Docker prod

### Ficheiro

O script espera **`data/galaticos.xlsm`** na raiz do projeto (ver [scripts/database/seed.sh](../../../scripts/database/seed.sh)). A aba de jogadores tem de se chamar exatamente **`Base de dados`**.

### Enviar o Excel do Windows para a VPS

PowerShell:

```powershell
scp "C:\Users\SEU_USER\Downloads\galaticos.xlsm" root@galaticosfr.vps-kinghost.net:/opt/galaticos/data/galaticos.xlsm
```

WSL:

```bash
scp /mnt/c/Users/SEU_USER/Downloads/galaticos.xlsm root@HOST:/opt/galaticos/data/galaticos.xlsm
```

### MongoDB acessível a partir do host (seed / `mongosh`)

Por omissão o serviço `mongodb` no compose **não** publica `27017`. Para correr `./bin/galaticos db:seed` na VPS com o fallback Docker (`--network host`) ou ferramentas no host, mapear só em localhost:

```yaml
ports:
  - "127.0.0.1:27017:27017"
```

no serviço `mongodb`. Remover ou manter conforme política de segurança após a operação.

### Autenticação

Exportar **sempre** uma URI com utilizador e palavra-passe (e `authSource=admin` se for o caso), alinhada ao `.env`:

```bash
export MONGO_URI='mongodb://galaticos:SENHA@127.0.0.1:27017/galaticos?authSource=admin'
./bin/galaticos db:seed
```

Sem isto, aparece “Command find requires authentication”.

### Limpar e voltar a semear

Só com consciência do risco e variáveis de segurança do projeto (ver [runbook-producao.md](runbook-producao.md) para `GALATICOS_ENV` e `ALLOW_DESTRUCTIVE_SEED`):

```bash
./bin/galaticos db:seed --reset
```

---

## 7. Checklist rápido pós-manutenção

| Verificação | Comando / nota |
|-------------|----------------|
| Código atual | `cd /opt/galaticos && git log -1 --oneline` |
| Containers | `docker compose -f config/docker/docker-compose.prod.yml ps` |
| Saúde da app | `curl -sf http://127.0.0.1:3000/health` |
| Imagem recente | `docker images` / data de criação do container `galaticos-app-prod` |
| Site | `https://www.teudominio.tld` (hard refresh ou janela privada após deploy) |

---

## 8. Referências no repositório

- [README.md](../../../README.md) — visão geral e scripts `./bin/galaticos`.
- [runbook-producao.md](runbook-producao.md) — volumes, seed seguro, backup, índices.
- [docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml) — serviços `app` e `mongodb`.
