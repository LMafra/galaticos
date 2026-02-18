# Regras de Negócio - Sistema Galáticos

**Última atualização:** 29 de Janeiro de 2026

Este documento lista todas as regras de negócio implementadas no sistema Galáticos, identificadas através da análise do código-fonte.

---

## Índice

1. [Autenticação e Autorização](#autenticação-e-autorização)
2. [Campeonatos (Championships)](#campeonatos-championships)
3. [Times (Teams)](#times-teams)
4. [Jogadores (Players)](#jogadores-players)
5. [Partidas (Matches)](#partidas-matches)
6. [Estatísticas e Agregações](#estatísticas-e-agregações)
7. [Validação de Dados](#validação-de-dados)
8. [Integridade Referencial](#integridade-referencial)
9. [Formatação de Respostas](#formatação-de-respostas)

---

## Autenticação e Autorização

### RN-AUTH-01: Autenticação JWT Stateless
- **Descrição**: O sistema utiliza autenticação baseada em JWT (JSON Web Tokens) sem estado de sessão no servidor.
- **Arquivo**: `src/galaticos/middleware/auth.clj`
- **Comportamento**:
  - Tokens são assinados com algoritmo HS256
  - Tokens incluem claims: `sub` (username), `iat` (issued at), `exp` (expiration)
  - TTL padrão do token: 86400 segundos (24 horas), configurável via `JWT_TTL_SECONDS`

### RN-AUTH-02: Segredo JWT Obrigatório em Produção
- **Descrição**: Em ambientes de produção, um segredo JWT deve ser configurado via variável de ambiente.
- **Arquivo**: `src/galaticos/middleware/auth.clj` (linha 27-29)
- **Comportamento**:
  - Ambientes dev/test podem usar segredo padrão "dev-secret"
  - Produção exige `JWT_SECRET` ou `JWT_SECRET` configurado
  - Sistema lança exceção se segredo não estiver definido em produção

### RN-AUTH-03: Autenticação via Bearer Token
- **Descrição**: Todas as requisições autenticadas devem incluir header `Authorization: Bearer <token>`.
- **Arquivo**: `src/galaticos/middleware/auth.clj` (linha 47-52)
- **Comportamento**:
  - Token extraído do header Authorization
  - Formato: `Bearer <token>` (case-insensitive)
  - Token inválido ou ausente retorna 401 Unauthorized

### RN-AUTH-04: Modo de Bypass para Desenvolvimento
- **Descrição**: Autenticação pode ser desabilitada em ambientes dev/test via `DISABLE_AUTH=true`.
- **Arquivo**: `src/galaticos/middleware/auth.clj` (linha 31-42)
- **Comportamento**:
  - Somente permitido em ambientes dev/development/test/testing
  - Sistema lança exceção se tentado em produção
  - Log de warning é emitido quando ativado

### RN-AUTH-05: Verificação de Credenciais de Admin
- **Descrição**: Login valida credenciais contra a coleção `admins` no MongoDB.
- **Arquivo**: `src/galaticos/handlers/auth.clj` (linha 7-23)
- **Comportamento**:
  - Username e password são obrigatórios
  - Senha é verificada com hash bcrypt
  - Atualiza campo `last-login` em caso de sucesso
  - Retorna token JWT em caso de sucesso
  - Retorna 401 em caso de credenciais inválidas

### RN-AUTH-06: Logout Stateless
- **Descrição**: Logout é uma operação client-side; servidor não mantém estado.
- **Arquivo**: `src/galaticos/handlers/auth.clj` (linha 25-33)
- **Comportamento**:
  - Endpoint existe apenas para paridade de API
  - Cliente deve descartar o token localmente
  - Sempre retorna sucesso

---

## Campeonatos (Championships)

### RN-CHAMP-01: Campos Obrigatórios
- **Descrição**: Campeonatos devem ter nome, temporada e contagem de títulos.
- **Arquivo**: `src/galaticos/handlers/championships.clj` (linha 11-12)
- **Campos Obrigatórios**:
  - `name` (string): Nome do campeonato
  - `season` (string): Temporada (ex: "2024", "2023/2024")
  - `titles-count` (número): Número de títulos disputados

### RN-CHAMP-02: Campos Permitidos
- **Descrição**: Apenas campos específicos são aceitos na criação/atualização de campeonatos.
- **Arquivo**: `src/galaticos/handlers/championships.clj` (linha 8-9)
- **Campos Permitidos**:
  - `name`: Nome do campeonato
  - `season`: Temporada
  - `status`: Status (ex: "active", "completed", "cancelled")
  - `format`: Formato (ex: "league", "cup", "knockout")
  - `start-date`: Data de início
  - `end-date`: Data de término
  - `location`: Local
  - `notes`: Observações
  - `titles-count`: Número de títulos

### RN-CHAMP-03: Validação de Campos Desconhecidos
- **Descrição**: Campos não listados como permitidos geram erro 400.
- **Arquivo**: `src/galaticos/handlers/championships.clj` (linha 20-24)
- **Comportamento**:
  - Retorna mensagem: "Unknown fields: <lista de campos>"
  - Status HTTP: 400 Bad Request

### RN-CHAMP-04: Filtragem por Status
- **Descrição**: Listagem de campeonatos pode ser filtrada por status.
- **Arquivo**: `src/galaticos/handlers/championships.clj` (linha 35-45)
- **Comportamento**:
  - Query param `status` filtra resultados
  - Ex: `GET /api/championships?status=active`

### RN-CHAMP-05: Proteção contra Deleção com Partidas
- **Descrição**: Campeonatos com partidas associadas não podem ser deletados.
- **Arquivo**: `src/galaticos/handlers/championships.clj` (linha 90-103)
- **Comportamento**:
  - Verifica se existem partidas antes de deletar
  - Retorna erro 409 Conflict se houver partidas
  - Mensagem: "Cannot delete championship: it has associated matches. Please delete or reassign matches first."

### RN-CHAMP-06: Timestamps Automáticos
- **Descrição**: Campeonatos recebem timestamps de criação e atualização automaticamente.
- **Arquivo**: `src/galaticos/db/championships.clj` (linha 14-24)
- **Comportamento**:
  - `created-at`: definido na criação
  - `updated-at`: atualizado em toda modificação

---

## Times (Teams)

### RN-TEAM-01: Campos Obrigatórios
- **Descrição**: Times devem ter pelo menos um nome.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 11-12)
- **Campos Obrigatórios**:
  - `name` (string): Nome do time

### RN-TEAM-02: Campos Permitidos
- **Descrição**: Apenas campos específicos são aceitos na criação/atualização de times.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 8-9)
- **Campos Permitidos**:
  - `name`: Nome do time
  - `city`: Cidade
  - `coach`: Treinador
  - `stadium`: Estádio
  - `founded-year`: Ano de fundação
  - `logo-url`: URL do logotipo
  - `active-player-ids`: IDs dos jogadores ativos (array)
  - `notes`: Observações

### RN-TEAM-03: Lista de Jogadores Ativos
- **Descrição**: Times mantêm uma lista de IDs de jogadores ativos.
- **Arquivo**: `src/galaticos/db/teams.clj` (linha 11-22)
- **Comportamento**:
  - Campo `active-player-ids` é um array de ObjectIds
  - Inicializado como array vazio na criação
  - Gerenciado via operações específicas (add/remove player)

### RN-TEAM-04: Adicionar Jogador ao Time
- **Descrição**: Jogadores podem ser adicionados à lista de ativos de um time.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 105-119)
- **Comportamento**:
  - Usa operador `$addToSet` para evitar duplicatas
  - Requer `team-id` e `player-id` válidos
  - Retorna time atualizado

### RN-TEAM-05: Remover Jogador do Time
- **Descrição**: Jogadores podem ser removidos da lista de ativos de um time.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 121-135)
- **Comportamento**:
  - Usa operador `$pull` para remover o ID
  - Requer `team-id` e `player-id` válidos
  - Retorna time atualizado

### RN-TEAM-06: Proteção contra Deleção com Jogadores
- **Descrição**: Times com jogadores ativos não podem ser deletados.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 90-103)
- **Comportamento**:
  - Verifica se existem jogadores ativos antes de deletar
  - Retorna erro 409 Conflict se houver jogadores
  - Mensagem: "Cannot delete team: it has associated players. Please remove players from team first."

### RN-TEAM-07: Normalização de IDs de Jogadores
- **Descrição**: IDs de jogadores no array `active-player-ids` são convertidos para ObjectId.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 23-25)
- **Comportamento**:
  - Conversão automática de strings para ObjectId
  - Valida formato de ObjectId (retorna 400 se inválido)

---

## Jogadores (Players)

### RN-PLAYER-01: Campos Obrigatórios
- **Descrição**: Jogadores devem ter nome e posição.
- **Arquivo**: `src/galaticos/handlers/players.clj` (linha 12-13)
- **Campos Obrigatórios**:
  - `name` (string): Nome do jogador
  - `position` (string): Posição (ex: "goleiro", "zagueiro", "atacante")

### RN-PLAYER-02: Campos Permitidos
- **Descrição**: Apenas campos específicos são aceitos na criação/atualização de jogadores.
- **Arquivo**: `src/galaticos/handlers/players.clj` (linha 8-10)
- **Campos Permitidos**:
  - `name`: Nome do jogador
  - `position`: Posição
  - `team-id`: ID do time (ObjectId)
  - `birth-date`: Data de nascimento
  - `nationality`: Nacionalidade
  - `height`: Altura (em cm)
  - `weight`: Peso (em kg)
  - `preferred-foot`: Pé preferido
  - `shirt-number`: Número da camisa
  - `active`: Status ativo/inativo (boolean)
  - `email`: Email
  - `phone`: Telefone
  - `number`: Número
  - `photo-url`: URL da foto
  - `notes`: Observações

### RN-PLAYER-03: Status Ativo por Padrão
- **Descrição**: Jogadores são criados com status ativo (`active: true`).
- **Arquivo**: `src/galaticos/db/players.clj` (linha 15-29)
- **Comportamento**:
  - Campo `active` definido como `true` na criação
  - Usado para filtrar listagens

### RN-PLAYER-04: Soft Delete
- **Descrição**: Deleção de jogadores é soft delete (marca como inativo).
- **Arquivo**: `src/galaticos/db/players.clj` (linha 79-82)
- **Comportamento**:
  - Define `active: false` ao invés de remover o documento
  - Jogadores inativos não aparecem em listagens por padrão
  - Preserva dados históricos e referências

### RN-PLAYER-05: Estatísticas Agregadas Inicializadas
- **Descrição**: Jogadores são criados com estrutura de estatísticas agregadas zerada.
- **Arquivo**: `src/galaticos/db/players.clj` (linha 24-25)
- **Comportamento**:
  - Campo `aggregated-stats` inicializado com:
    - `total`: `{games: 0, goals: 0, assists: 0, titles: 0}`
    - `by-championship`: `[]` (array vazio)
  - Preenchido por processo de agregação de partidas

### RN-PLAYER-06: Filtragem de Jogadores Ativos
- **Descrição**: Listagem pode filtrar apenas jogadores ativos.
- **Arquivo**: `src/galaticos/handlers/players.clj` (linha 37-51)
- **Comportamento**:
  - Query param `active=true` filtra apenas ativos
  - Query param `team-id` filtra por time
  - Padrão: retorna todos os jogadores (ativos e inativos)

### RN-PLAYER-07: Normalização de Team ID
- **Descrição**: Team ID é convertido para ObjectId na criação/atualização.
- **Arquivo**: `src/galaticos/handlers/players.clj` (linha 27-28)
- **Comportamento**:
  - Conversão automática de string para ObjectId
  - Valida formato de ObjectId (retorna 400 se inválido)

---

## Partidas (Matches)

### RN-MATCH-01: Campos Obrigatórios
- **Descrição**: Partidas devem ter championship-id e estatísticas de jogadores.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 13-14)
- **Campos Obrigatórios**:
  - `championship-id` (ObjectId): ID do campeonato
  - `player-statistics` (array): Estatísticas dos jogadores (não pode ser vazio)

### RN-MATCH-02: Campos Permitidos
- **Descrição**: Apenas campos específicos são aceitos na criação/atualização de partidas.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 9-11)
- **Campos Permitidos**:
  - `championship-id`: ID do campeonato
  - `home-team-id`: ID do time mandante
  - `away-team-id`: ID do time visitante
  - `date`: Data da partida
  - `location`: Local
  - `round`: Rodada
  - `status`: Status
  - `home-score`: Placar do mandante
  - `away-score`: Placar do visitante
  - `player-statistics`: Array de estatísticas dos jogadores
  - `notes`: Observações

### RN-MATCH-03: Estrutura de Estatísticas de Jogador
- **Descrição**: Cada entrada em `player-statistics` deve ter estrutura específica.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 16-21)
- **Campos Obrigatórios nas Estatísticas**:
  - `player-id` (ObjectId): ID do jogador
- **Campos Permitidos nas Estatísticas**:
  - `player-id`: ID do jogador
  - `player-name`: Nome do jogador
  - `position`: Posição
  - `goals`: Gols marcados
  - `assists`: Assistências
  - `yellow-cards`: Cartões amarelos
  - `red-cards`: Cartões vermelhos
  - `minutes-played`: Minutos jogados

### RN-MATCH-04: Validação de Array de Estatísticas
- **Descrição**: Player-statistics deve ser um array não vazio de objetos.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 23-42)
- **Comportamento**:
  - Retorna erro 400 se não for array
  - Retorna erro 400 se array estiver vazio
  - Mensagem: "player-statistics must be a non-empty vector"

### RN-MATCH-05: Recalculo Automático de Estatísticas na Criação
- **Descrição**: Ao criar uma partida, estatísticas dos jogadores são recalculadas.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 102-116)
- **Comportamento**:
  - Após inserir partida, chama `update-player-stats-for-match`
  - Atualiza campo `aggregated-stats` dos jogadores envolvidos
  - Agregação é feita via pipeline MongoDB

### RN-MATCH-06: Recalculo Automático de Estatísticas na Atualização
- **Descrição**: Ao atualizar uma partida, estatísticas dos jogadores são recalculadas.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 118-137)
- **Comportamento**:
  - Após atualizar partida, chama `update-player-stats-for-match`
  - Garante consistência das estatísticas agregadas

### RN-MATCH-07: Recalculo Completo de Estatísticas na Deleção
- **Descrição**: Ao deletar uma partida, todas as estatísticas de jogadores são recalculadas.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 139-151)
- **Comportamento**:
  - Após remover partida, chama `update-all-player-stats`
  - Recalcula estatísticas de TODOS os jogadores
  - Garante que estatísticas estejam corretas após remoção

### RN-MATCH-08: Filtragem por Campeonato
- **Descrição**: Listagem de partidas pode ser filtrada por campeonato.
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 79-89)
- **Comportamento**:
  - Query param `championship-id` filtra resultados
  - Resultados ordenados por data (decrescente)

---

## Estatísticas e Agregações

### RN-STATS-01: Agregação por Campeonato
- **Descrição**: Sistema calcula estatísticas de jogadores por campeonato específico.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 9-37)
- **Métricas Calculadas**:
  - `games`: Número de partidas jogadas
  - `goals`: Total de gols marcados
  - `assists`: Total de assistências
  - `yellow-cards`: Total de cartões amarelos
  - `red-cards`: Total de cartões vermelhos
  - `goals-per-game`: Média de gols por partida
  - `assists-per-game`: Média de assistências por partida

### RN-STATS-02: Média de Gols por Posição
- **Descrição**: Sistema calcula média de gols por posição em um campeonato.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 39-63)
- **Métricas Calculadas**:
  - `avg-goals`: Média de gols da posição
  - `total-goals`: Total de gols da posição
  - `total-assists`: Total de assistências da posição
  - `player-count`: Número de entradas de jogadores
  - `unique-games`: Número único de partidas

### RN-STATS-03: Evolução de Performance do Jogador
- **Descrição**: Sistema rastreia evolução temporal de performance de jogadores.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 65-88)
- **Comportamento**:
  - Agrega dados por ano, mês e semana
  - Calcula métricas por período temporal
  - Retorna série temporal ordenada cronologicamente

### RN-STATS-04: Pipeline de Atualização de Estatísticas
- **Descrição**: Sistema usa pipeline de agregação MongoDB para calcular estatísticas.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 90-125)
- **Comportamento**:
  - Desdobra estatísticas de jogadores das partidas
  - Agrupa por jogador e campeonato
  - Faz lookup de informações de campeonatos
  - Calcula totais gerais e por campeonato
  - Estrutura: `{total: {...}, by-championship: [...]}`

### RN-STATS-05: Atualização de Estatísticas de Todos os Jogadores
- **Descrição**: Sistema pode recalcular estatísticas de todos os jogadores.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 127-140)
- **Comportamento**:
  - Executa pipeline de agregação completo
  - Atualiza campo `aggregated-stats` de cada jogador
  - Atualiza campo `updated-at` com timestamp
  - Retorna quantidade de jogadores atualizados

### RN-STATS-06: Atualização de Estatísticas por Partida
- **Descrição**: Sistema pode recalcular estatísticas apenas dos jogadores de uma partida.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 142-159)
- **Comportamento**:
  - Identifica jogadores envolvidos na partida
  - Executa pipeline de agregação completo
  - Atualiza apenas jogadores da partida específica
  - Otimização para evitar recalcular tudo

### RN-STATS-07: Busca Avançada de Jogadores
- **Descrição**: Sistema permite busca de jogadores com múltiplos filtros.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 161-204)
- **Filtros Disponíveis**:
  - `position`: Posição do jogador
  - `min-games`: Mínimo de partidas jogadas
  - `min-goals`: Mínimo de gols marcados
  - `min-age`: Idade mínima
  - `max-age`: Idade máxima
  - `sort-by`: Campo para ordenação
  - `sort-order`: Ordem (crescente/decrescente)
  - `limit`: Limite de resultados

### RN-STATS-08: Comparação entre Campeonatos
- **Descrição**: Sistema compara estatísticas agregadas entre diferentes campeonatos.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 206-296)
- **Comportamento**:
  - Tenta agregar de partidas se existirem dados
  - Fallback para agregação de `aggregated-stats` dos jogadores
  - Calcula métricas comparativas entre campeonatos
- **Métricas Calculadas**:
  - `championship-name`: Nome do campeonato
  - `championship-format`: Formato do campeonato
  - `matches-count`: Número de partidas
  - `players-count`: Número de jogadores únicos
  - `total-goals`: Total de gols
  - `total-assists`: Total de assistências
  - `avg-goals-per-match`: Média de gols por partida

### RN-STATS-09: Top Jogadores por Métrica
- **Descrição**: Sistema retorna ranking de jogadores por métrica específica.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 298-347)
- **Comportamento**:
  - Filtra apenas jogadores ativos com `aggregated-stats`
  - Pode filtrar por campeonato específico
  - Ordena por métrica solicitada (decrescente)
  - Limita número de resultados
- **Métricas Suportadas**:
  - `goals`: Gols marcados
  - `assists`: Assistências
  - `games`: Partidas jogadas
  - `titles`: Títulos conquistados

### RN-STATS-10: Validação de Integridade de Dados
- **Descrição**: Sistema valida integridade de dados antes de agregações.
- **Arquivo**: `src/galaticos/handlers/aggregations.clj` (linha 9-42)
- **Verificações**:
  - Partidas referenciando campeonatos inexistentes
  - Partidas sem `player-statistics`
  - Partidas com `player-statistics` vazio
- **Comportamento**:
  - Emite logs de warning para problemas encontrados
  - Não interrompe operação (continua apesar de problemas)

---

## Validação de Dados

### RN-VALID-01: Validação de Campos Obrigatórios
- **Descrição**: Todos os handlers validam presença de campos obrigatórios.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Retorna erro 400 se campos obrigatórios estiverem ausentes
  - Mensagem: "Missing required fields: <lista de campos>"

### RN-VALID-02: Validação de Campos Desconhecidos
- **Descrição**: Campos não listados como permitidos são rejeitados.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Retorna erro 400 se campos desconhecidos forem enviados
  - Mensagem: "Unknown fields: <lista de campos>"
  - Previne poluição do banco de dados

### RN-VALID-03: Validação de ObjectId
- **Descrição**: Strings de ID são validadas e convertidas para ObjectId.
- **Arquivo**: `src/galaticos/util/response.clj`
- **Comportamento**:
  - Tenta converter string para ObjectId
  - Retorna erro 400 se formato for inválido
  - Mensagem: "Invalid ID format"

### RN-VALID-04: Validação de Corpo da Requisição
- **Descrição**: Corpo da requisição deve ser um objeto JSON válido.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Verifica se body é um map
  - Retorna erro 400 se não for: "Invalid request body"

### RN-VALID-05: Validação em Atualização Parcial
- **Descrição**: Atualizações não exigem todos os campos obrigatórios.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Função `validate-*-body` recebe flag `require-required?`
  - Em updates, `require-required?` é `false`
  - Permite atualização parcial de entidades

---

## Integridade Referencial

### RN-REF-01: Campeonato não pode ser Deletado com Partidas
- **Descrição**: Sistema impede deleção de campeonatos que têm partidas.
- **Arquivo**: `src/galaticos/handlers/championships.clj` (linha 96-97)
- **Comportamento**:
  - Verifica via `has-matches?`
  - Retorna 409 Conflict se houver partidas
  - Usuário deve deletar/reatribuir partidas primeiro

### RN-REF-02: Time não pode ser Deletado com Jogadores
- **Descrição**: Sistema impede deleção de times que têm jogadores ativos.
- **Arquivo**: `src/galaticos/handlers/teams.clj` (linha 96-97)
- **Comportamento**:
  - Verifica via `has-players?`
  - Retorna 409 Conflict se houver jogadores
  - Usuário deve remover jogadores do time primeiro

### RN-REF-03: Jogadores são Soft Deleted
- **Descrição**: Jogadores não são fisicamente removidos para preservar referências.
- **Arquivo**: `src/galaticos/db/players.clj` (linha 79-82)
- **Comportamento**:
  - Marca `active: false` ao invés de deletar
  - Preserva referências em partidas históricas
  - Mantém integridade de dados históricos

### RN-REF-04: Verificação de Existência antes de Atualização
- **Descrição**: Sistema verifica se entidade existe antes de atualizar.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Chama `exists?` antes de `update-by-id`
  - Retorna 404 Not Found se não existir
  - Previne criação acidental via update

### RN-REF-05: Verificação de Existência antes de Deleção
- **Descrição**: Sistema verifica se entidade existe antes de deletar.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Chama `exists?` antes de `delete-by-id`
  - Retorna 404 Not Found se não existir
  - Operação idempotente (deletar inexistente retorna 404)

---

## Formatação de Respostas

### RN-RESP-01: Formato Padrão de Resposta
- **Descrição**: Todas as respostas seguem formato padronizado com `success` e `data`/`error`.
- **Arquivo**: `src/galaticos/util/response.clj`
- **Formato de Sucesso**:
  ```json
  {
    "success": true,
    "data": <qualquer>
  }
  ```
- **Formato de Erro**:
  ```json
  {
    "success": false,
    "error": "mensagem de erro"
  }
  ```

### RN-RESP-02: Códigos HTTP Padronizados
- **Descrição**: Sistema usa códigos HTTP semânticos.
- **Arquivo**: `src/galaticos/util/response.clj`
- **Códigos Utilizados**:
  - `200 OK`: Leitura ou atualização bem-sucedida
  - `201 Created`: Criação bem-sucedida
  - `400 Bad Request`: Validação falhou, JSON inválido, ID inválido
  - `401 Unauthorized`: Não autenticado
  - `403 Forbidden`: Sem permissão
  - `404 Not Found`: Recurso não encontrado
  - `409 Conflict`: Conflito por integridade referencial
  - `500 Internal Server Error`: Erro interno do servidor

### RN-RESP-03: Serialização de ObjectId
- **Descrição**: ObjectIds do MongoDB são serializados como strings.
- **Arquivo**: `src/galaticos/util/response.clj`
- **Comportamento**:
  - ObjectId convertido para string hexadecimal
  - Formato: 24 caracteres hexadecimais
  - Exemplo: `"65a1b2c3d4e5f6a7b8c9d0e1"`

### RN-RESP-04: Serialização de Datas
- **Descrição**: Datas Java são serializadas como ISO-8601.
- **Arquivo**: `src/galaticos/util/response.clj`
- **Comportamento**:
  - `java.util.Date` convertido para string ISO_INSTANT
  - Formato: `"2024-01-29T10:30:00.000Z"`
  - Timezone: UTC

### RN-RESP-05: Tratamento de Exceções
- **Descrição**: Exceções são capturadas e convertidas em respostas padronizadas.
- **Arquivos**: `src/galaticos/handlers/*.clj`
- **Comportamento**:
  - Exceções com status 400 retornam erro de validação
  - Outras exceções são logadas e retornam 500
  - Mensagem amigável ao usuário
  - Stack trace completo no log

---

## Regras de Timestamps

### RN-TIME-01: Created At Automático
- **Descrição**: Todas as entidades recebem timestamp de criação.
- **Arquivos**: `src/galaticos/db/*.clj`
- **Comportamento**:
  - Campo `created-at` definido com `java.util.Date()` atual
  - Definido apenas na criação
  - Nunca alterado posteriormente

### RN-TIME-02: Updated At Automático
- **Descrição**: Todas as entidades recebem timestamp de última atualização.
- **Arquivos**: `src/galaticos/db/*.clj`
- **Comportamento**:
  - Campo `updated-at` definido com `java.util.Date()` atual
  - Definido na criação
  - Atualizado em toda modificação (update)

---

## Regras de Consulta

### RN-QUERY-01: Ordenação de Partidas por Data
- **Descrição**: Partidas de um campeonato são retornadas ordenadas por data (mais recentes primeiro).
- **Arquivo**: `src/galaticos/db/matches.clj` (linha 36-41)
- **Comportamento**:
  - Ordem decrescente por campo `date`
  - Aplicado automaticamente em `find-by-championship`

### RN-QUERY-02: Ordenação de Estatísticas por Métrica
- **Descrição**: Estatísticas são ordenadas pela métrica relevante.
- **Arquivo**: `src/galaticos/db/aggregations.clj` (linha 34)
- **Comportamento**:
  - Ordem decrescente (maiores valores primeiro)
  - Ordenação secundária quando aplicável

---

## Regras de Inicialização

### RN-INIT-01: Estrutura de Estatísticas Zerada
- **Descrição**: Jogadores são criados com estrutura de estatísticas inicializada.
- **Arquivo**: `src/galaticos/db/players.clj` (linha 24-25)
- **Estrutura Inicial**:
  ```clojure
  {:aggregated-stats 
    {:total {:games 0 :goals 0 :assists 0 :titles 0}
     :by-championship []}}
  ```

### RN-INIT-02: Lista de Jogadores Vazia em Times
- **Descrição**: Times são criados com lista de jogadores vazia.
- **Arquivo**: `src/galaticos/db/teams.clj` (linha 18)
- **Comportamento**:
  - Campo `active-player-ids` inicializado como `[]`
  - Preenchido via operações add-player

---

## Observações Finais

### Consistência de Dados

O sistema implementa várias estratégias para garantir consistência:

1. **Validação Rigorosa**: Todos os campos são validados antes de persistência
2. **Soft Delete**: Entidades referenciadas não são fisicamente removidas
3. **Integridade Referencial**: Deleções em cascata são bloqueadas
4. **Recalculo Automático**: Estatísticas são recalculadas automaticamente
5. **Timestamps**: Todas as modificações são rastreadas
6. **Transações Implícitas**: MongoDB garante atomicidade em operações individuais

### Manutenibilidade

O código segue padrões que facilitam manutenção:

1. **Validação Centralizada**: Funções `validate-*-body` em cada handler
2. **Separação de Responsabilidades**: Handlers, DB, e Util são separados
3. **Logging Estruturado**: Erros são logados com contexto adequado
4. **Mensagens Claras**: Erros têm mensagens descritivas para o usuário
5. **Documentação**: Docstrings em todas as funções públicas

### Extensibilidade

O sistema está preparado para extensões:

1. **Campos Opcionais**: Muitos campos são opcionais para flexibilidade
2. **Filtros Dinâmicos**: Queries suportam filtros múltiplos
3. **Pipeline de Agregação**: MongoDB pipelines permitem análises complexas
4. **Soft Delete**: Dados históricos são preservados
5. **Estatísticas Flexíveis**: Sistema de agregação é extensível

---

## Regras de Negócio Pendentes de Implementação

As seguintes regras de negócio foram identificadas como requisitos mas **NÃO estão implementadas** no código atual:

### RN-PEND-01: Limite de Jogadores por Campeonato
- **Descrição**: Cada campeonato deve ter um limite máximo de jogadores que podem ser inscritos.
- **Status**: ❌ **NÃO IMPLEMENTADO**
- **Impacto**: Médio
- **Requisitos para Implementação**:
  1. Adicionar campo `max-players` ao schema de championships
  2. Adicionar campo `enrolled-player-ids` (array de ObjectIds) ao schema de championships
  3. Validar limite ao tentar inscrever jogador em campeonato
  4. Retornar erro 409 se limite for excedido
  5. Criar endpoints:
     - `POST /api/championships/:id/enroll/:player-id` - Inscrever jogador
     - `DELETE /api/championships/:id/unenroll/:player-id` - Desinscrever jogador
     - `GET /api/championships/:id/players` - Listar jogadores inscritos
- **Exemplo de Implementação**:
  ```clojure
  ;; Em championships.clj
  (def ^:private allowed-championship-fields
    #{:name :season :status :format :start-date :end-date :location :notes 
      :titles-count :max-players :enrolled-player-ids})
  
  (defn enroll-player
    "Enroll a player in a championship"
    [championship-id player-id]
    (let [champ (find-by-id championship-id)
          max-players (:max-players champ)
          enrolled (count (:enrolled-player-ids champ []))]
      (if (and max-players (>= enrolled max-players))
        {:error "Championship has reached maximum number of players" :status 409}
        (update-by-id championship-id 
          {:$addToSet {:enrolled-player-ids player-id}}))))
  ```

### RN-PEND-02: Listagem de Jogadores Inscritos por Campeonato
- **Descrição**: Cada campeonato deve mostrar a lista de jogadores inscritos.
- **Status**: ❌ **NÃO IMPLEMENTADO**
- **Impacto**: Médio
- **Dependências**: RN-PEND-01
- **Requisitos para Implementação**:
  1. Endpoint `GET /api/championships/:id/players`
  2. Retornar jogadores com informações completas (join com collection players)
  3. Frontend: componente para exibir lista de inscritos
  4. Frontend: botão para inscrever/desinscrever jogadores

### RN-PEND-03: Finalização de Campeonato e Definição de Títulos
- **Descrição**: Ao finalizar um campeonato, o sistema deve permitir definir o(s) vencedor(es) e atualizar contador de títulos dos jogadores.
- **Status**: ❌ **NÃO IMPLEMENTADO**
- **Impacto**: Alto
- **Requisitos para Implementação**:
  1. Adicionar campo `winner-player-ids` (array de ObjectIds) ao schema de championships
  2. Adicionar campo `finished-at` (Date) ao schema de championships
  3. Criar endpoint `POST /api/championships/:id/finalize`
  4. Payload deve incluir IDs dos jogadores vencedores
  5. Ao finalizar:
     - Mudar status para "finished"
     - Definir `finished-at` com data atual
     - Incrementar contador de títulos dos jogadores vencedores
     - Atualizar `aggregated-stats.total.titles` de cada jogador
  6. Validações:
     - Campeonato deve estar com status "active"
     - Vencedores devem ser jogadores inscritos no campeonato
     - Campeonato só pode ser finalizado uma vez
- **Exemplo de Implementação**:
  ```clojure
  ;; Em handlers/championships.clj
  (defn finalize-championship
    "Finalize a championship and award titles to winners"
    [request]
    (let [champ-id (get-in request [:params :id])
          {:keys [winner-player-ids]} (:json-body request)
          champ (championships-db/find-by-id champ-id)]
      (cond
        (not= (:status champ) "active")
        (resp/error "Only active championships can be finalized" 400)
        
        (not (seq winner-player-ids))
        (resp/error "At least one winner must be specified" 400)
        
        :else
        (do
          ;; Update championship status
          (championships-db/update-by-id champ-id 
            {:status "finished"
             :finished-at (java.util.Date.)
             :winner-player-ids winner-player-ids})
          ;; Increment titles for winners
          (doseq [player-id winner-player-ids]
            (mc/update (db) "players"
              {:_id player-id}
              {:$inc {:aggregated-stats.total.titles 1}}))
          (resp/success {:message "Championship finalized successfully"})))))
  ```

### RN-PEND-04: Validação de Jogadores da Partida pertencem ao Campeonato
- **Descrição**: Ao criar ou editar uma partida, validar que todos os jogadores listados nas estatísticas estão inscritos no campeonato.
- **Status**: ❌ **NÃO IMPLEMENTADO**
- **Impacto**: Médio
- **Dependências**: RN-PEND-01
- **Requisitos para Implementação**:
  1. Na validação de partida, buscar campeonato pelo `championship-id`
  2. Verificar se cada `player-id` em `player-statistics` está em `enrolled-player-ids` do campeonato
  3. Retornar erro 400 se algum jogador não estiver inscrito
  4. Mensagem: "Player {name} is not enrolled in this championship"
- **Exemplo de Implementação**:
  ```clojure
  ;; Em handlers/matches.clj
  (defn- validate-players-enrolled [championship-id player-ids]
    (let [champ (championships-db/find-by-id championship-id)
          enrolled-ids (set (:enrolled-player-ids champ []))]
      (when-not (every? #(contains? enrolled-ids %) player-ids)
        (let [not-enrolled (remove #(contains? enrolled-ids %) player-ids)]
          (throw (ex-info 
            (str "Players not enrolled in championship: " 
                 (str/join ", " not-enrolled))
            {:status 400}))))))
  
  ;; No create-match, antes de criar:
  (let [player-ids (map :player-id player-statistics)]
    (validate-players-enrolled championship-id player-ids))
  ```

### RN-PEND-05: Cálculo Automático do Placar da Partida
- **Descrição**: O resultado (placar) da partida deve ser calculado automaticamente somando os gols marcados pelos jogadores em cada time.
- **Status**: ❌ **NÃO IMPLEMENTADO**
- **Impacto**: Alto
- **Requisitos para Implementação**:
  1. Remover campos `home-score` e `away-score` dos campos permitidos (ou torná-los computed)
  2. Adicionar campo `team-id` em `player-statistics` para identificar o time de cada jogador
  3. Calcular placar automaticamente:
     - `home-score` = soma de gols dos jogadores do time mandante
     - `away-score` = soma de gols dos jogadores do time visitante
  4. Recalcular placar sempre que `player-statistics` for atualizado
  5. Armazenar campos calculados ou computá-los dinamicamente
- **Exemplo de Implementação**:
  ```clojure
  ;; Em db/matches.clj
  (defn- calculate-scores [home-team-id away-team-id player-statistics]
    (let [home-goals (reduce + 0 
                       (map :goals 
                         (filter #(= (:team-id %) home-team-id) player-statistics)))
          away-goals (reduce + 0 
                       (map :goals 
                         (filter #(= (:team-id %) away-team-id) player-statistics)))]
      {:home-score home-goals
       :away-score away-goals}))
  
  (defn create [match-data player-statistics]
    (let [home-team-id (:home-team-id match-data)
          away-team-id (:away-team-id match-data)
          scores (calculate-scores home-team-id away-team-id player-statistics)
          doc (merge match-data scores
                {:_id (ObjectId.)
                 :player-statistics player-statistics
                 :created-at (java.util.Date.)
                 :updated-at (java.util.Date.)})]
      (mc/insert (db) collection-name doc)
      doc))
  ```

### RN-PEND-06: UI para Seleção de Jogadores do Campeonato
- **Descrição**: Na criação e edição de partida, exibir apenas jogadores inscritos no campeonato selecionado.
- **Status**: ❌ **NÃO IMPLEMENTADO**
- **Impacto**: Médio
- **Dependências**: RN-PEND-01, RN-PEND-02
- **Requisitos para Implementação**:
  1. Ao selecionar campeonato no formulário, buscar jogadores inscritos
  2. Exibir dropdown com apenas esses jogadores
  3. Desabilitar adição de estatísticas até que campeonato seja selecionado
  4. Atualizar lista de jogadores se campeonato for alterado

---

## Resumo de Implementação

### Status Atual
- ✅ **Implementadas**: 65 regras de negócio
- ❌ **Pendentes**: 6 regras de negócio

### Priorização das Regras Pendentes

#### Alta Prioridade
- **RN-PEND-03**: Finalização de Campeonato e Definição de Títulos
  - Essencial para ciclo completo de campeonato
  - Impacta sistema de títulos de jogadores
- **RN-PEND-05**: Cálculo Automático do Placar
  - Evita inconsistências entre estatísticas e placar
  - Reduz erro humano

#### Média Prioridade
- **RN-PEND-01**: Limite de Jogadores por Campeonato
  - Controle de inscrições
  - Requisito para RN-PEND-02 e RN-PEND-04
- **RN-PEND-02**: Listagem de Jogadores Inscritos
  - Visibilidade de participantes
- **RN-PEND-04**: Validação de Jogadores nas Partidas
  - Integridade de dados
- **RN-PEND-06**: UI para Seleção de Jogadores
  - Melhora UX

### Esforço Estimado

| Regra | Esforço | Complexidade |
|-------|---------|--------------|
| RN-PEND-01 | 1-2 dias | Média |
| RN-PEND-02 | 0.5 dia | Baixa |
| RN-PEND-03 | 1-2 dias | Alta |
| RN-PEND-04 | 0.5 dia | Baixa |
| RN-PEND-05 | 1 dia | Média |
| RN-PEND-06 | 0.5-1 dia | Baixa |

**Total Estimado**: 4.5-7.5 dias úteis

---

**Documento gerado automaticamente através da análise do código-fonte.**
**Última atualização: 29 de Janeiro de 2026**
**Para dúvidas ou sugestões, consulte o código-fonte nos arquivos mencionados.**

