# Implementação do Schema MongoDB - Resumo

## Arquivos Criados

### Documentação
- `docs/informacao/dominio/mongodb-schema.md` - Documentação completa do schema com exemplos JSON para todas as coleções

### Scripts MongoDB
- `scripts/mongodb-indexes.js` - Script para criação de todos os índices necessários
- `scripts/mongodb-aggregations.js` - Exemplos de pipelines de agregação em JavaScript

### Estrutura Clojure
- `deps.edn` - Configuração de dependências do projeto
- `resources/config.edn` - Configurações de ambiente (dev/prod)

### Módulos de Banco de Dados
- `src/galaticos/db/core.clj` - Conexão MongoDB e configuração base
- `src/galaticos/db/championships.clj` - Operações CRUD para campeonatos
- `src/galaticos/db/players.clj` - Operações CRUD para jogadores
- `src/galaticos/db/matches.clj` - Operações CRUD para partidas
- `src/galaticos/db/teams.clj` - Operações CRUD para times
- `src/galaticos/db/admins.clj` - Operações CRUD para administradores (com hash de senha)
- `src/galaticos/db/aggregations.clj` - Funções de agregação para analytics

### Documentação do Projeto
- `README.md` - Guia de uso e configuração do projeto

## Coleções Implementadas

### 1. championships
- Campos: name, season, format, start-date, end-date, status, created-at, updated-at
- Índices: name+season (único), status, dates
- Operações: create, find-by-id, find-all, find-active, find-by-name-and-season, update-by-id, delete-by-id

### 2. players
- Campos: name, nickname, position, birth-date, height, weight, photo-url, team-id, active, aggregated-stats, created-at, updated-at
- Índices: name, team-id+active, position, aggregated-stats.total.games, aggregated-stats.by-championship.championship-id, nickname
- Operações: create, find-by-id, find-all, find-active, find-by-team, find-by-position, find-by-name, update-by-id, update-stats, delete-by-id (soft delete)

### 3. matches
- Campos: championship-id, date, opponent, venue, result, player-statistics (array), created-at, updated-at
- Índices: championship-id+date, date, player-statistics.player-id
- Operações: create, find-by-id, find-all, find-by-championship, find-by-date-range, find-by-player, update-by-id, delete-by-id

### 4. teams
- Campos: name, active-player-ids (array), created-at, updated-at
- Índices: name (único)
- Operações: create, find-by-id, find-by-name, find-all, update-by-id, add-player, remove-player, delete-by-id

### 5. admins
- Campos: username, password-hash, created-at, last-login
- Índices: username (único)
- Operações: create, find-by-id, find-by-username, verify-password, update-last-login, update-password, delete-by-id

## Funções de Agregação Implementadas

1. **player-stats-by-championship** - Estatísticas agregadas por jogador em um campeonato
2. **avg-goals-by-position** - Média de gols por posição
3. **player-performance-evolution** - Evolução temporal de performance de um jogador
4. **update-aggregated-stats-pipeline** - Pipeline para calcular estatísticas agregadas
5. **update-all-player-stats** - Atualiza estatísticas de todos os jogadores
6. **update-player-stats-for-match** - Atualiza estatísticas após inserir partida
7. **search-players** - Busca de jogadores com múltiplos filtros (posição, idade, performance)
8. **championship-comparison** - Comparativo entre campeonatos
9. **top-players-by-metric** - Top jogadores por métrica específica

## Próximos Passos

1. **Testar conexão MongoDB**: Executar `(galaticos.db.core/connect!)` no REPL
2. **Criar índices**: Executar `./bin/galaticos db:setup`
3. **Criar admin inicial**: Usar `(galaticos.db.admins/create "admin" "senha")`
4. **Implementar rotas HTTP**: Criar rotas em `src/galaticos/routes/`
5. **Implementar middleware de autenticação**: Criar em `src/galaticos/middleware/auth.clj`
6. **Implementar handlers**: Criar handlers para processar requisições HTTP

## Seeds: BASE_DADOS vs smoke (E2E)

- **`scripts/python/seed_mongodb.py`** — joga fora dados legados opcionais com `--reset`; campeonatos/temporadas vindos de `data/BASE_DADOS.csv` e planilha Base. Para um banco **só** com esse fluxo, use reset + este script e **não** rode o seed smoke depois.
- **`galaticos.tasks.seed-smoke`** / `bin/galaticos db:seed-smoke` — conjunto mínimo para testes E2E (“Smoke Championship”, etc.). Se rodar no **mesmo** Mongo **sem** limpar coleções, a lista de campeonatos mistura smoke + dados do Python; isso não é o script Python “lendo” o smoke, é **acúmulo** no banco.
- No frontend, rotas usam **hash**: ao testar manualmente, abra URLs como `/#/championships/<id>/seasons/<seasonId>` (e não só o path sem `#`).

## Notas de Implementação

- Todas as datas usam `java.util.Date` no Clojure, convertidas para ISODate no MongoDB
- IDs são `ObjectId` do MongoDB
- Senhas de admin são hasheadas com bcrypt usando a biblioteca `buddy`
- Estatísticas agregadas são atualizadas via background job após inserção de partidas
- Convenção de nomenclatura: kebab-case para campos e coleções

## Dependências Principais

- `com.novemberain/monger` - Driver MongoDB para Clojure
- `buddy` - Autenticação e hash de senhas
- `environ` - Gerenciamento de configurações por ambiente

