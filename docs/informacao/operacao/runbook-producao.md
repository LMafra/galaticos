# Runbook: produção e preservação de dados

Este documento descreve como fazer deploy e manutenção do Galáticos em produção **sem perder dados** no MongoDB.

Para operações típicas em **VPS + domínio externo** (SSH, localização do `.env`, Nginx, timeouts do Clojars no `docker build`, seed com `MONGO_URI`), ver [vps-hospedeiro.md](vps-hospedeiro.md).

## Princípios

- Os dados persistem no volume Docker nomeado **`mongodb-data-prod`** ([config/docker/docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml)).
- O container **`app`** é substituível: rebuild/restart **não** apaga a base, desde que o volume do MongoDB seja mantido.
- O ficheiro [config/database/init-indexes.js](../../../config/database/init-indexes.js) corre **apenas no primeiro arranque** do MongoDB com diretório de dados vazio. Bases já existentes precisam de índices aplicados com o fluxo documentado abaixo. Mantê-lo alinhado com [scripts/mongodb/mongodb-indexes.js](../../../scripts/mongodb/mongodb-indexes.js).

## Deploy e ciclo de vida dos containers

### Comandos seguros (preservam o volume)

```bash
./bin/galaticos docker:prod build
./bin/galaticos docker:prod start
./bin/galaticos docker:prod restart   # reinicia containers; não reconstrói imagem (código novo exige build)
./bin/galaticos docker:prod stop   # usa `docker compose down` sem `-v`
```

### Nunca em produção

- `docker compose ... down -v` (ou `docker volume rm` no volume de dados): **apaga a base**.
- Recriar o stack com remoção explícita de volumes.

Para atualizar só a aplicação após alterações de código:

```bash
docker compose -f config/docker/docker-compose.prod.yml up -d --build app
```

(Isto reconstrói e reinicia o serviço `app`; o MongoDB e o volume `mongodb-data-prod` permanecem.)

## Seed e scripts de dados

### Seed oficial (Excel)

- Modo **idempotente** (recomendado para complementar dados sem wipe):

  ```bash
  export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/galaticos?authSource=admin'
  export DB_NAME=galaticos
  export GALATICOS_ENV=production   # recomendado ao apontar para produção
  ./bin/galaticos db:seed
  ```

- **Não** usar `--reset` em produção salvo decisão explícita, backup e janela de manutenção. Com `GALATICOS_ENV=production`, o script Python exige `ALLOW_DESTRUCTIVE_SEED=1` para permitir `--reset` (ver [scripts/python/seed_mongodb.py](../../../scripts/python/seed_mongodb.py)).

### Seed smoke (E2E)

- **Não** executar `./bin/galaticos db:seed-smoke` na mesma base (`DB_NAME`) que produção. Mistura dados de teste com dados reais e o seed oficial pode recusar-se a correr sem `--reset`.

## Índices em bases já inicializadas

Novos índices devem ser adicionados em:

1. [scripts/mongodb/mongodb-indexes.js](../../../scripts/mongodb/mongodb-indexes.js) — fonte para reaplicação em qualquer ambiente.
2. [config/database/init-indexes.js](../../../config/database/init-indexes.js) — manter alinhado para **novos** volumes Docker (primeiro boot).

Aplicar índices de forma idempotente num servidor já em produção:

```bash
export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/?authSource=admin'
export DB_NAME=galaticos
./bin/galaticos db:setup
```

O `db:setup` executa o script `mongodb-indexes.js` via `mongosh`; `createIndex` com as mesmas opções é seguro se o índice já existir.

## Backup e restore

### Backup (`mongodump`)

```bash
export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/?authSource=admin'
export DB_NAME=galaticos
./bin/galaticos db:backup
```

Por omissão os ficheiros ficam em `backups/mongodb/` com timestamp. Ver variáveis em [scripts/database/backup-mongodb.sh](../../../scripts/database/backup-mongodb.sh).

### Agendar (exemplo cron)

No servidor ou num bastion com acesso ao MongoDB (ajustar caminho e URI):

```cron
0 3 * * * cd /caminho/para/galaticos && MONGO_URI='mongodb://...' DB_NAME=galaticos ./bin/galaticos db:backup >> /var/log/galaticos-mongo-backup.log 2>&1
```

### Restore

Testar **sempre** o procedimento de restore num ambiente de staging antes de produção.

```bash
export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/?authSource=admin'
export DB_NAME=galaticos
./bin/galaticos db:restore --archive backups/mongodb/galaticos-YYYYMMDD-HHMMSS.archive.gz
```

Sem `--drop`, o `mongorestore` **funde** com dados existentes. Com `--drop`, **substitui** as coleções do `DB_NAME` — usar só com consciência do risco. Detalhes em [scripts/database/restore-mongodb.sh](../../../scripts/database/restore-mongodb.sh).

## Variáveis de ambiente relevantes (seed)

| Variável | Função |
|----------|--------|
| `GALATICOS_ENV=production` | Recomendado ao correr ferramentas contra produção; com este valor, `--reset` no seed exige `ALLOW_DESTRUCTIVE_SEED=1`. |
| `ALLOW_DESTRUCTIVE_SEED=1` | Permite `seed_mongodb.py --reset` quando `GALATICOS_ENV` é produção. |
| `MONGO_URI` / `DB_NAME` | Ligação ao MongoDB (alinhado com a string usada pela app, incl. `authSource=admin` se aplicável). |

## Referência rápida

| Ação | Comando / nota |
|------|----------------|
| Subir stack | `./bin/galaticos docker:prod start` |
| Redeploy só app | `docker compose -f config/docker/docker-compose.prod.yml up -d --build app` |
| Índices | `MONGO_URI=... DB_NAME=galaticos ./bin/galaticos db:setup` |
| Seed sem wipe | `GALATICOS_ENV=production MONGO_URI=... ./bin/galaticos db:seed` |
| Backup | `MONGO_URI=... ./bin/galaticos db:backup` |

Para detalhes de desenvolvimento local, ver o [README.md](../../../README.md) na raiz do repositório.
