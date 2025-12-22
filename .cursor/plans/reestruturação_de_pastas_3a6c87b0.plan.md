# Reestruturação de Pastas do Projeto Galáticos

## Objetivo
Reorganizar a estrutura de diretórios do projeto para melhorar a manutenibilidade, facilitar a navegação e seguir boas práticas de organização de projetos open-source no GitHub.

## Estrutura Atual vs. Proposta

### Problemas Identificados
1. Arquivos Docker na raiz (Dockerfile.dev, docker-compose.dev.yml)
2. Scripts misturados sem clara separação de propósito
3. Falta de pasta `data/` mencionada no README mas não existente
4. Arquivos de configuração espalhados na raiz
5. Estrutura de testes poderia ser mais clara
6. Arquivos avulsos na raiz precisam ser analisados e organizados

### Nova Estrutura Proposta

```
galaticos/
├── .github/                    # GitHub templates e workflows (mantém)
│   ├── ISSUE_TEMPLATE/
│   └── workflows/              # Adicionar workflows CI/CD
├── .clojure/                   # Configurações Clojure (mantém)
├── bin/                        # Scripts executáveis (mantém)
│   └── galaticos
├── config/                     # NOVO: Configurações centralizadas
│   ├── docker/
│   │   ├── Dockerfile.dev
│   │   ├── Dockerfile.prod
│   │   ├── docker-compose.dev.yml
│   │   ├── docker-compose.prod.yml
│   │   └── .dockerignore       # Move de raiz
│   └── database/
│       └── init-indexes.js     # Move de docker-entrypoint-initdb.d/
├── data/                       # NOVO: Dados do projeto
│   ├── raw/                    # Arquivos originais (Excel, etc.)
│   ├── processed/              # Dados processados
│   └── .gitkeep                # Garantir que pasta existe no git
├── docs/                       # Documentação (mantém e expande)
│   ├── mongodb-schema.md
│   ├── backend-gap-report.md
│   ├── build-gap-report.md
│   ├── frontend-gap-report.md
│   └── IMPLEMENTATION.md       # NOVO: Move de raiz
├── resources/                  # Recursos estáticos (mantém)
│   ├── config.edn
│   └── templates/
│       └── index.html
├── scripts/                    # Scripts organizados por categoria
│   ├── build/                  # NOVO: Scripts de build
│   │   └── build.sh
│   ├── database/               # RENOMEADO: de db/ para database/
│   │   ├── check-stats.sh
│   │   ├── seed.sh
│   │   └── setup.sh
│   ├── dev/                    # Scripts de desenvolvimento (mantém)
│   │   ├── console.sh
│   │   ├── run.sh
│   │   ├── test.sh
│   │   ├── validate.sh
│   │   └── watch-cljs.sh
│   ├── docker/                 # Scripts Docker (mantém)
│   │   ├── dev.sh
│   │   ├── prod.sh
│   │   └── validate.sh
│   ├── mongodb/                # Scripts MongoDB (mantém)
│   │   ├── mongodb-aggregations.js
│   │   └── mongodb-indexes.js
│   ├── python/                 # Scripts Python (mantém)
│   │   ├── read_excel.py
│   │   └── seed_mongodb.py
│   └── utils/                  # Utilitários (mantém)
│       ├── check-deps.sh
│       ├── clean.sh
│       └── common.sh
├── src/                        # Código fonte Clojure (mantém)
│   └── galaticos/
├── src-cljs/                   # Código fonte ClojureScript (mantém)
│   └── galaticos/
├── test/                       # Testes Clojure (mantém)
│   └── galaticos/
└── test-cljs/                  # Testes ClojureScript (mantém)
    └── galaticos/
```

## Análise de Arquivos Avulsos na Raiz

### Arquivos Essenciais (MANTER na raiz)
Estes arquivos são padrão do ecossistema e devem permanecer na raiz:

1. **`deps.edn`** ✅ MANTER
   - Arquivo principal de dependências Clojure
   - Referenciado em `deps-lock.edn` e scripts
   - Padrão do ecossistema Clojure

2. **`deps-lock.edn`** ✅ MANTER
   - Lock file de dependências (gerado automaticamente)
   - Garante builds reproduzíveis
   - Padrão do ecossistema Clojure

3. **`build.clj`** ✅ MANTER
   - Script de build do ClojureScript
   - Referenciado em `deps.edn` (alias `:build`)
   - Referenciado em `docker-compose.dev.yml` (volume mount)
   - Necessário para compilação do frontend

4. **`shadow-cljs.edn`** ✅ MANTER
   - Configuração do Shadow-CLJS
   - Essencial para compilação do ClojureScript
   - Padrão do ecossistema Shadow-CLJS

5. **`package.json`** ✅ MANTER
   - Dependências Node.js
   - Referenciado em scripts npm
   - Padrão do ecossistema Node.js

6. **`package-lock.json`** ✅ MANTER
   - Lock file de dependências Node.js
   - Garante builds reproduzíveis
   - Padrão do ecossistema Node.js

7. **`requirements.txt`** ✅ MANTER
   - Dependências Python
   - Usado por scripts de seed
   - Padrão do ecossistema Python

8. **`LICENSE`** ✅ MANTER
   - Licença do projeto
   - Padrão para projetos open-source
   - Deve estar na raiz para visibilidade

9. **`README.md`** ✅ MANTER
   - Documentação principal do projeto
   - Primeiro arquivo que usuários veem
   - Padrão para projetos GitHub

10. **`CONTRIBUTING.md`** ✅ MANTER
    - Guia de contribuição
    - Referenciado no README
    - Padrão para projetos open-source

11. **`Makefile`** ✅ MANTER
    - Wrapper para comandos do projeto
    - Referenciado no README
    - Facilita uso do projeto

12. **`.gitignore`** ✅ MANTER
    - Arquivos ignorados pelo Git
    - Deve estar na raiz (padrão Git)

### Arquivos para Mover

1. **`IMPLEMENTATION.md`** → `docs/IMPLEMENTATION.md`
   - Documentação de implementação
   - Não é referenciado em código
   - Deve estar em `docs/` com outros documentos

2. **`.dockerignore`** → `config/docker/.dockerignore`
   - Configuração específica do Docker
   - Faz sentido estar junto com outros arquivos Docker
   - **Nota**: Docker procura `.dockerignore` na raiz por padrão, mas podemos usar `-f` no Dockerfile

### Arquivos Docker (já no plano anterior)
- `Dockerfile.dev` → `config/docker/Dockerfile.dev`
- `Dockerfile.prod` → `config/docker/Dockerfile.prod`
- `docker-compose.dev.yml` → `config/docker/docker-compose.dev.yml`
- `docker-compose.prod.yml` → `config/docker/docker-compose.prod.yml`

## Mudanças Detalhadas

### 1. Criar `config/` para Centralizar Configurações
- **Mover**: `Dockerfile.dev` → `config/docker/Dockerfile.dev`
- **Mover**: `Dockerfile.prod` → `config/docker/Dockerfile.prod`
- **Mover**: `docker-compose.dev.yml` → `config/docker/docker-compose.dev.yml`
- **Mover**: `docker-compose.prod.yml` → `config/docker/docker-compose.prod.yml`
- **Mover**: `.dockerignore` → `config/docker/.dockerignore`
- **Mover**: `docker-entrypoint-initdb.d/init-indexes.js` → `config/database/init-indexes.js`

**Impacto**: Scripts em `scripts/docker/` precisarão atualizar caminhos relativos. Dockerfiles precisarão atualizar referências ao `.dockerignore`.

### 2. Criar `data/` para Dados do Projeto
- **Criar**: `data/raw/` (com `.gitkeep`)
- **Criar**: `data/processed/` (com `.gitkeep`)
- **Atualizar**: `.gitignore` para manter `data/raw/` e `data/input/` ignorados (já está)

**Impacto**: Scripts Python em `scripts/python/` precisarão atualizar caminhos para arquivos Excel.

### 3. Renomear `scripts/db/` para `scripts/database/`
- **Renomear**: `scripts/db/` → `scripts/database/`
- **Atualizar**: Referências no `bin/galaticos` e README

**Impacto**: Script principal `bin/galaticos` precisa atualizar caminhos.

### 4. Criar `scripts/build/` para Scripts de Build
- **Criar**: `scripts/build/` (pode estar vazio inicialmente, mas preparado para futuros scripts de build)

### 5. Mover Documentação
- **Mover**: `IMPLEMENTATION.md` → `docs/IMPLEMENTATION.md`

**Impacto**: Nenhum (não é referenciado em código)

### 6. Atualizar Arquivos de Referência
- **Atualizar**: `README.md` com nova estrutura
- **Atualizar**: `bin/galaticos` com novos caminhos
- **Atualizar**: Scripts Docker com novos caminhos para Dockerfiles
- **Atualizar**: Scripts Python com novos caminhos para `data/raw/`
- **Atualizar**: `docker-compose*.yml` com novos caminhos para Dockerfiles e volumes
- **Atualizar**: `Dockerfile.dev` e `Dockerfile.prod` com novo caminho para `.dockerignore` (usar `--ignorefile` ou copiar)

## Arquivos que Precisam de Atualização

### Scripts que Referenciam Caminhos
1. `bin/galaticos` - atualizar caminhos para scripts
2. `scripts/docker/dev.sh` - atualizar caminhos para docker-compose
3. `scripts/docker/prod.sh` - atualizar caminhos para docker-compose
4. `scripts/python/seed_mongodb.py` - atualizar caminho para `data/raw/`
5. `docker-compose.dev.yml` - atualizar caminhos para Dockerfile, volumes e `.dockerignore`
6. `docker-compose.prod.yml` - atualizar caminhos para Dockerfile, volumes e `.dockerignore`
7. `Dockerfile.dev` - atualizar referência a `.dockerignore` (se necessário)
8. `Dockerfile.prod` - atualizar referência a `.dockerignore` (se necessário)
9. `README.md` - atualizar estrutura de diretórios

### Arquivos de Configuração
1. `docker-compose.dev.yml` - atualizar `build.context`, `dockerfile` e referências a volumes
2. `docker-compose.prod.yml` - atualizar `build.context`, `dockerfile` e referências a volumes
3. `.gitignore` - verificar se precisa ajustar (já está bom)

## Ordem de Execução

1. Criar novas pastas (`config/`, `data/`, `scripts/build/`, `scripts/database/`)
2. Mover arquivos Docker para `config/docker/`
3. Mover `.dockerignore` para `config/docker/`
4. Mover `docker-entrypoint-initdb.d/init-indexes.js` para `config/database/`
5. Mover `IMPLEMENTATION.md` para `docs/`
6. Renomear `scripts/db/` para `scripts/database/`
7. Criar `data/raw/` e `data/processed/` com `.gitkeep`
8. Atualizar todos os scripts e arquivos de configuração com novos caminhos
9. Atualizar `README.md` com nova estrutura
10. Remover pasta vazia `docker-entrypoint-initdb.d/` se existir

## Notas Importantes

- **Backward Compatibility**: Alguns scripts podem precisar de ajustes para funcionar com novos caminhos
- **Git**: Usar `git mv` quando possível para preservar histórico
- **Testes**: Verificar que todos os scripts ainda funcionam após mudanças
- **Documentação**: Atualizar qualquer documentação adicional que referencie caminhos antigos
- **Dockerignore**: Se mover `.dockerignore`, pode ser necessário usar `--ignorefile` no Docker build ou copiar o arquivo durante o build
- **Arquivos Essenciais**: Os arquivos mantidos na raiz (`deps.edn`, `build.clj`, etc.) são padrão do ecossistema e devem permanecer na raiz
