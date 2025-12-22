# Galáticos - Sistema de Gestão de Elenco Esportivo

Sistema web para gestão de elenco esportivo com dashboard analítico, desenvolvido em Clojure/Luminus com MongoDB.

## Stack Tecnológico

- **Backend:** Clojure (Framework Luminus)
- **Frontend:** ClojureScript (via ClojureScript no Luminus)
- **Banco de Dados:** MongoDB
- **Autenticação:** Admin único

## Estrutura do Projeto

```
galaticos/
├── .github/                       # GitHub templates e workflows
│   ├── ISSUE_TEMPLATE/
│   └── workflows/
├── bin/                           # Scripts executáveis
│   └── galaticos                  # Ponto de entrada principal
├── config/                        # Configurações centralizadas
│   ├── docker/                    # Configurações Docker
│   │   ├── Dockerfile.dev
│   │   ├── Dockerfile.prod
│   │   ├── docker-compose.dev.yml
│   │   ├── docker-compose.prod.yml
│   │   └── .dockerignore
│   └── database/                  # Scripts de inicialização do banco
│       └── init-indexes.js
├── data/                          # Dados do projeto
│   ├── raw/                       # Arquivos de dados originais (Excel, etc.)
│   └── processed/                # Dados processados
├── docs/                          # Documentação
│   ├── mongodb-schema.md          # Documentação do schema
│   ├── backend-gap-report.md
│   ├── build-gap-report.md
│   ├── frontend-gap-report.md
│   └── IMPLEMENTATION.md          # Documentação de implementação
├── resources/                     # Recursos estáticos e configurações
│   ├── config.edn                 # Configurações da aplicação
│   └── templates/
│       └── index.html
├── scripts/                       # Scripts utilitários organizados
│   ├── build/                     # Scripts de build
│   ├── database/                  # Scripts de banco de dados
│   │   ├── check-stats.sh
│   │   ├── seed.sh
│   │   └── setup.sh
│   ├── dev/                       # Scripts de desenvolvimento
│   │   ├── console.sh
│   │   ├── run.sh
│   │   ├── test.sh
│   │   ├── validate.sh
│   │   └── watch-cljs.sh
│   ├── docker/                    # Scripts Docker
│   │   ├── dev.sh
│   │   ├── prod.sh
│   │   └── validate.sh
│   ├── mongodb/                   # Scripts MongoDB
│   │   ├── mongodb-indexes.js     # Script de criação de índices
│   │   └── mongodb-aggregations.js # Exemplos de agregações
│   ├── python/                    # Scripts Python
│   │   ├── read_excel.py
│   │   └── seed_mongodb.py        # Script de seed do banco
│   └── utils/                     # Utilitários
│       ├── check-deps.sh
│       ├── clean.sh
│       └── common.sh
├── src/                           # Código fonte Clojure
│   └── galaticos/
│       ├── core.clj               # Ponto de entrada da aplicação
│       ├── handler.clj            # Handler principal
│       ├── db/                    # Camada de dados
│       │   ├── core.clj           # Conexão MongoDB
│       │   ├── championships.clj  # Operações de campeonatos
│       │   ├── players.clj        # Operações de jogadores
│       │   ├── matches.clj        # Operações de partidas
│       │   ├── teams.clj          # Operações de times
│       │   ├── admins.clj         # Operações de admins
│       │   └── aggregations.clj   # Pipelines de agregação
│       ├── handlers/              # Handlers de requisições
│       ├── middleware/            # Middleware (auth, CORS, etc.)
│       ├── routes/                # Rotas HTTP
│       └── util/                  # Utilitários
├── src-cljs/                      # Código fonte ClojureScript
│   └── galaticos/
├── test/                          # Testes Clojure
│   └── galaticos/
├── test-cljs/                     # Testes ClojureScript
│   └── galaticos/
├── .gitignore                     # Arquivos ignorados pelo Git
├── CONTRIBUTING.md                # Guia de contribuição
├── deps.edn                       # Dependências Clojure
├── deps-lock.edn                  # Lock file de dependências
├── build.clj                      # Script de build do ClojureScript
├── shadow-cljs.edn                # Configuração do Shadow-CLJS
├── package.json                   # Dependências Node.js
├── package-lock.json              # Lock file Node.js
├── requirements.txt               # Dependências Python
├── Makefile                       # Wrapper para comandos
├── LICENSE                        # Licença do projeto
└── README.md                      # Este arquivo
```

## Configuração

### 1. Instalar Dependências

```bash
# Usando Clojure CLI
clj -M:dev
```

### 2. Configurar MongoDB

Edite `resources/config.edn` com suas configurações de MongoDB:

```clojure
{:dev {:database-url "mongodb://localhost:27017/galaticos"
       :database-name "galaticos"}}
```

### 3. Criar Índices

Execute o script de criação de índices:

```bash
./bin/galaticos db:setup
```

Este comando:
- Verifica se o MongoDB está rodando
- Conecta ao banco de dados `galaticos`
- Cria todos os índices necessários para as coleções
- Verifica que os índices foram criados corretamente

### 4. Seed do Banco de Dados

Para popular o banco de dados com dados do arquivo Excel:

```bash
./bin/galaticos db:seed
```

Este comando:
- Verifica se o MongoDB está rodando
- Ativa o ambiente virtual Python automaticamente
- Instala/atualiza dependências Python se necessário
- Lê o arquivo Excel e popula o banco de dados
- Verifica que os dados foram inseridos corretamente

**Nota:** O arquivo Excel deve estar em `data/raw/Galáticos 2025 Automatizada 1.12.xlsm`

**Nota:** O arquivo Excel deve estar em `data/raw/Galáticos 2025 Automatizada 1.12.xlsm`

O script irá:
- Ler o arquivo `data/raw/Galáticos 2025 Automatizada 1.12.xlsm`
- Criar o time "Galáticos"
- Criar jogadores a partir da planilha "Base de dados"
- Criar campeonatos a partir das outras planilhas
- Atualizar estatísticas agregadas dos jogadores por campeonato

**Nota:** Certifique-se de que o MongoDB está rodando antes de executar o script.

### 5. Compilar ClojureScript

O frontend da aplicação é escrito em ClojureScript e precisa ser compilado para JavaScript antes de ser servido. Agora usamos Shadow-CLJS (com hot-reload e integração npm).

> Requer Node.js 18+ e `npm ci` executado pelo menos uma vez.

**Desenvolvimento (build único):**
```bash
clj -M:build:frontend dev
```

**Watch (hot reload):**
```bash
npm run cljs:watch
```

**Produção (otimizações avançadas):**
```bash
clj -M:build:frontend prod
```

**Nota:** O script `./bin/galaticos run` compila automaticamente o ClojureScript antes de iniciar o servidor. Os arquivos compilados são gerados em `resources/public/js/compiled/`.

## Scripts

O projeto inclui scripts utilitários organizados em uma estrutura clara e fácil de usar. O ponto de entrada principal é `./bin/galaticos`, que fornece uma interface unificada para todos os comandos.

### Estrutura de Scripts

```
bin/
  galaticos              # Ponto de entrada principal
scripts/
  dev/                   # Scripts de desenvolvimento
    run.sh               # Executa a aplicação
    console.sh           # Inicia REPL Clojure
    test.sh              # Executa testes
    validate.sh          # Valida aplicação
    watch-cljs.sh        # Watch ClojureScript
  database/              # Scripts de banco de dados
    setup.sh             # Configura índices MongoDB
    seed.sh              # Popula banco de dados
    check-stats.sh       # Verifica estatísticas
  docker/                # Scripts Docker
    dev.sh               # Ambiente de desenvolvimento
    prod.sh              # Ambiente de produção
    validate.sh          # Valida aplicação em Docker
  build/                 # Scripts de build
    build.sh             # Compila uberjar
  utils/                 # Utilitários
    check-deps.sh        # Verifica dependências
    clean.sh             # Limpa artefatos
    common.sh            # Funções comuns
  python/                # Scripts Python
    read_excel.py        # Leitura de arquivos Excel
    seed_mongodb.py      # Script de seed
  mongodb/               # Scripts MongoDB
    mongodb-indexes.js   # Criação de índices
    mongodb-aggregations.js # Exemplos de agregações
```

### Uso Principal: `./bin/galaticos`

A forma mais fácil de usar os scripts é através do comando principal:

```bash
# Mostrar ajuda
./bin/galaticos help

# Desenvolvimento
./bin/galaticos run              # Executa a aplicação
./bin/galaticos console          # Inicia REPL Clojure
./bin/galaticos test             # Executa testes

# Banco de Dados
./bin/galaticos db:setup         # Configura índices MongoDB
./bin/galaticos db:seed          # Popula banco de dados

# Docker
./bin/galaticos docker:dev start     # Inicia ambiente dev
./bin/galaticos docker:dev stop      # Para ambiente dev
./bin/galaticos docker:dev logs      # Mostra logs
./bin/galaticos docker:dev status   # Status dos serviços
./bin/galaticos docker:dev validate # Valida aplicação em Docker
./bin/galaticos docker:dev clean     # Remove containers/volumes
./bin/galaticos docker:prod start    # Ambiente de produção

# Build
./bin/galaticos build             # Compila uberjar
./bin/galaticos clean             # Limpa artefatos

# Utilitários
./bin/galaticos check-deps        # Verifica dependências
```

### Usando o Makefile

Alternativamente, você pode usar o Makefile:

```bash
make help              # Mostra ajuda
make run               # Executa a aplicação
make test              # Executa testes
make console           # Inicia REPL
make build             # Compila uberjar
make db:setup          # Configura MongoDB
make db:seed           # Popula banco de dados
make docker:dev CMD=start  # Gerencia Docker dev
make docker:prod CMD=start # Gerencia Docker prod
make check-deps        # Verifica dependências
make clean             # Limpa artefatos
```

### Início Rápido

```bash
# 1. Verificar dependências
./bin/galaticos check-deps

# 2. Iniciar MongoDB (Docker)
./bin/galaticos docker:dev start

# 3. Configurar índices
./bin/galaticos db:setup

# 4. Popular banco de dados (opcional)
./bin/galaticos db:seed

# 5. Executar aplicação (compila ClojureScript automaticamente)
./bin/galaticos run

# 6. Validar que aplicação está funcionando (em outro terminal)
./bin/galaticos validate
```

**Nota:** O comando `./bin/galaticos run` compila automaticamente o ClojureScript antes de iniciar o servidor. A aplicação estará disponível em `http://localhost:3000`.

### Validação

A aplicação inclui scripts de validação para verificar se está funcionando corretamente:

**Validação Local:**
```bash
./bin/galaticos validate
```

Este comando verifica:
- Se o servidor está rodando na porta 3000
- Se o endpoint `/health` funciona
- Se o endpoint `/` retorna HTML com Content-Type correto
- Se o arquivo JavaScript existe e tem Content-Type correto
- Se não está baixando arquivos em vez de servir HTML

**Validação Docker:**
```bash
# Validar aplicação rodando em Docker
./bin/galaticos validate:docker

# Ou usando o comando docker:dev
./bin/galaticos docker:dev validate
```

A validação Docker verifica:
- Se os containers Docker estão rodando
- Se o container da aplicação está saudável
- Todos os mesmos endpoints da validação local
- Se arquivos JavaScript compilados existem no container
- Logs do container para erros

**Nota:** O script de validação local detecta automaticamente se está rodando em Docker e ajusta as mensagens apropriadamente.

### Executando Scripts Diretamente

Se preferir, você também pode executar os scripts diretamente:

```bash
./scripts/dev/run.sh
./scripts/database/setup.sh
./scripts/docker/dev.sh start
```

## Uso

### Conexão com MongoDB

```clojure
(require '[galaticos.db.core :as db])

;; Conectar
(db/connect!)

;; Obter instância do banco
(db/db)

;; Desconectar
(db/disconnect!)
```

### Operações CRUD

#### Campeonatos

```clojure
(require '[galaticos.db.championships :as ch])

;; Criar
(ch/create {:name "Boleiro fut7"
            :season "2025"
            :format "society-7"
            :status "active"})

;; Buscar
(ch/find-by-id "507f1f77bcf86cd799439011")
(ch/find-active)
```

#### Jogadores

```clojure
(require '[galaticos.db.players :as players])

;; Criar
(players/create {:name "Gabriel Leal"
                 :nickname "Leal"
                 :position "Atacante"
                 :team-id "507f1f77bcf86cd799439015"})

;; Buscar
(players/find-by-position "Atacante")
(players/find-active)
```

#### Partidas

```clojure
(require '[galaticos.db.matches :as matches])

;; Criar partida com estatísticas
(matches/create {:championship-id "507f1f77bcf86cd799439011"
                 :date (java.util.Date.)
                 :opponent "Time Adversário"}
                [{:player-id "507f1f77bcf86cd799439012"
                  :player-name "Gabriel Leal"
                  :position "Atacante"
                  :goals 2
                  :assists 1}])
```

### Agregações e Analytics

```clojure
(require '[galaticos.db.aggregations :as agg])

;; Estatísticas por campeonato
(agg/player-stats-by-championship "507f1f77bcf86cd799439011")

;; Média de gols por posição
(agg/avg-goals-by-position "507f1f77bcf86cd799439011")

;; Evolução temporal de jogador
(agg/player-performance-evolution "507f1f77bcf86cd799439012")

;; Busca com filtros
(agg/search-players {:position "Atacante"
                     :min-games 10
                     :sort-by :goals-per-game
                     :limit 20})

;; Comparativo entre campeonatos
(agg/championship-comparison)

;; Top jogadores
(agg/top-players-by-metric :goals 10)
```

### Atualização de Estatísticas Agregadas

```clojure
;; Atualizar todas as estatísticas
(agg/update-all-player-stats)

;; Atualizar após inserir partida
(agg/update-player-stats-for-match "match-id")
```

## Schema MongoDB

Consulte `docs/mongodb-schema.md` para documentação completa do schema, incluindo:
- Estrutura de coleções
- Schemas JSON de exemplo
- Relacionamentos
- Estratégias de embedding vs referencing

## Índices

Os índices são criados ao executar `./bin/galaticos db:setup`. Principais índices:

- `championships`: name + season (único), status, dates
- `players`: name, team-id + active, position, aggregated-stats
- `matches`: championship-id + date, date, player-statistics.player-id
- `teams`: name (único)
- `admins`: username (único)

## Convenções

- **Nomenclatura**: kebab-case para campos e coleções
- **Datas**: ISODate no MongoDB, java.util.Date no Clojure
- **IDs**: ObjectId do MongoDB
- **Estatísticas**: Embedded em matches, cached em players.aggregated-stats

## Desenvolvimento

### Estrutura de Diretórios Recomendada

```
src/galaticos/
  db/              # Camada de dados
  routes/          # Rotas HTTP
    api/           # API endpoints
  middleware/      # Middleware (auth, etc.)
  handlers/        # Handlers de requisições
```

### Variáveis de Ambiente

Crie um arquivo `.env` baseado em `.env.example` (se disponível) ou configure as variáveis diretamente em `resources/config.edn`.

### Docker

Para desenvolvimento com Docker:

```bash
docker-compose -f docker-compose.dev.yml up
```

Para produção:

```bash
docker-compose -f docker-compose.prod.yml up
```

## Contribuindo

Por favor, leia [CONTRIBUTING.md](CONTRIBUTING.md) para detalhes sobre nosso código de conduta e o processo para enviar pull requests.

## Licença

Este projeto está licenciado sob a Licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.

