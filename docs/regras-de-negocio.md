---

## Apêndice: Modelo de Regras segundo o Business Rules Manifesto

Este apêndice estrutura um subconjunto das regras de negócio do sistema Galáticos seguindo o mantra do Business Rules Group:

- **Termos** (vocabulário de negócio)
- **Fatos** (assertivas sobre esses termos)
- **Regras** (restrições ou derivação baseadas nesses fatos)

O foco aqui são as regras explicitadas no enunciado:

- CRUD de jogadores, times, campeonatos e partidas  
- Temporadas de campeonatos (ativa e passadas)  
- Finalização de campeonatos e incremento de títulos  
- Visualização de jogadores e estatísticas por partida  
- Navegação via dashboard  
- Buscas de jogadores  
- Inscrição automática via planilhas (campeonatos e jogadores, incluindo goleiros)

### Vocabulário de Negócio (Termos)

- **Jogador**: Pessoa cadastrada no sistema que pode participar de partidas e campeonatos, com atributos como nome, posição, estatísticas e títulos.
- **Time**: Entidade que representa um clube ou equipe, à qual jogadores podem estar associados.
- **Campeonato**: Competição composta por uma ou mais temporadas e um conjunto de partidas e jogadores inscritos.
- **Temporada de Campeonato**: Período (por exemplo, ano ou ano/ano) associado a um campeonato, podendo estar em estado ativo ou passado.
- **Temporada Ativa**: Temporada corrente de um campeonato, na qual partidas e estatísticas estão sendo registradas.
- **Temporada Passada**: Temporada historicamente concluída de um campeonato, preservada para fins de consulta e estatísticas.
- **Partida**: Evento esportivo individual associado a um campeonato, com data, adversário, resultado e estatísticas de jogadores.
- **Estatística de Jogador na Partida**: Registro dos eventos/participações de um jogador em uma partida (gols, assistências, cartões, minutos, etc.).
- **Título**: Conquista atribuída a jogadores quando seu clube é declarado campeão em um campeonato/finalização específica.
- **Dashboard**: Tela principal de visão geral do sistema, com indicadores (cards, listas, atalhos) para navegação às telas de CRUD e consultas.
- **Planilha de Campeonatos**: Fonte externa (arquivo) que contém dados de campeonatos e inscrições de jogadores a serem importados para o sistema.
- **Planilha de Jogadores**: Fonte externa (arquivo) que contém dados de jogadores (nome, posição, etc.) a serem importados para o sistema.
- **Goleiro**: Jogador cuja posição principal é de guarda-redes; pode ser inferida a partir de marcações como "GK" na planilha de jogadores.

### Fatos Estruturais do Domínio

- **F-01**: Um campeonato possui uma ou mais temporadas identificadas por um rótulo (por exemplo, "2023/2024").
- **F-02**: Para cada campeonato existe no máximo uma temporada ativa em um dado momento.
- **F-03**: Um campeonato pode ter zero ou mais temporadas passadas associadas (históricas).
- **F-04**: Uma partida pertence exatamente a um campeonato.
- **F-05**: Cada partida registra estatísticas de um ou mais jogadores.
- **F-06**: Um jogador pode estar inscrito em zero ou mais campeonatos.
- **F-07**: Um time pode ter zero ou mais jogadores ativos associados.
- **F-08**: Jogadores que participam de partidas de um campeonato (via estatísticas) são considerados participantes daquele campeonato.
- **F-09**: Cada jogador acumula estatísticas agregadas por campeonato e no total da carreira dentro da plataforma.
- **F-10**: O dashboard apresenta indicadores agregados sobre jogadores, times, campeonatos e partidas com links para telas detalhadas.

### Regras de Negócio Derivadas dos Requisitos

Cada regra abaixo é classificada conforme o Manifesto:

- **Tipo Estrutural (E)**: Constraint sobre a estrutura/dados.
- **Tipo de Ação (A)**: Regras que controlam/comandam comportamento.
- **Tipo de Derivação (D)**: Regras que calculam/derivam novos fatos a partir de fatos existentes.

#### Grupo 1 – CRUD de Entidades

- **BRM-01 (E/A – CRUD de Jogadores)**  
  - **Descrição**: O sistema deve permitir criar, ler, atualizar e inativar jogadores, garantindo obrigatoriedade de nome e posição, e preservando referências históricas (soft delete).  
  - **Relação com regras existentes**: Alinhado com `RN-PLAYER-01` a `RN-PLAYER-04`, `RN-INIT-01`, `RN-STATS-05`.

- **BRM-02 (E/A – CRUD de Times)**  
  - **Descrição**: O sistema deve permitir criar, ler, atualizar e deletar times, respeitando a constraint de que times com jogadores ativos não podem ser deletados.  
  - **Relação com regras existentes**: Alinhado com `RN-TEAM-01` a `RN-TEAM-07`, `RN-REF-02`, `RN-INIT-02`.

- **BRM-03 (E/A – CRUD de Campeonatos)**  
  - **Descrição**: O sistema deve permitir criar, ler, atualizar e deletar campeonatos, exigindo campos obrigatórios (nome, temporada, títulos) e impedindo deleção quando existirem partidas associadas.  
  - **Relação com regras existentes**: Alinhado com `RN-CHAMP-01` a `RN-CHAMP-06`, `RN-REF-01`.

- **BRM-04 (E/A – CRUD de Partidas)**  
  - **Descrição**: O sistema deve permitir criar, ler, atualizar e deletar partidas, exigindo vínculo a um campeonato e ao menos uma estatística de jogador, garantindo consistência das estatísticas agregadas em qualquer operação de CRUD de partida.  
  - **Relação com regras existentes**: Alinhado com `RN-MATCH-01` a `RN-MATCH-08`, `RN-STATS-01` a `RN-STATS-07`.

#### Grupo 2 – Temporadas de Campeonatos

- **BRM-05 (E – Temporada Ativa por Campeonato)**  
  - **Descrição**: Cada campeonato deve ter no máximo uma temporada ativa; alterações de temporada devem preservar o histórico das temporadas anteriores como temporadas passadas.  
  - **Tipo**: Estrutural.  
  - **Observação**: O modelo atual utiliza campo `season` no campeonato; a distinção entre ativa e passada pode demandar campos adicionais (`current-season`, `past-seasons`) ou convenções de status.

- **BRM-06 (A – Gestão de Temporadas)**  
  - **Descrição**: O administrador deve ser capaz de adicionar novas temporadas a um campeonato e selecionar qual temporada está ativa, garantindo que a temporada ativa apareça de forma destacada na tela de detalhes do campeonato, junto com o nome do campeonato.  
  - **Tipo**: Ação.

#### Grupo 3 – Finalização de Campeonatos e Títulos

- **BRM-07 (A – Finalização de Campeonato)**  
  - **Descrição**: O administrador deve ser capaz de finalizar um campeonato, alterando seu status para concluído e registrando a data de finalização. Um campeonato só pode ser finalizado uma vez.  
  - **Tipo**: Ação.  
  - **Relação com regras existentes**: Alinhado com `RN-PEND-03` (finalize-championship) e constraints de status.

- **BRM-08 (A/D – Incremento de Títulos)**  
  - **Descrição**: Ao finalizar um campeonato, os títulos dos jogadores só devem ser incrementados se o administrador informar explicitamente que o clube foi campeão. Desta maneira, todos os jogadores serão considerados vencedores.
  - **Tipo**: Ação + Derivação (deriva novo valor de títulos com base em vencedores e `titles-award-count`).  
  - **Relação com regras existentes**: Corresponde a `RN-PEND-03` (increment-titles).

- **BRM-09 (E – Participação e Pertencimento ao Time)**  
  - **Descrição**: Todos os jogadores que participam de um campeonato por meio de partidas (com estatísticas registradas) devem pertencer a um time válido e estar inscritos no campeonato, mantendo coerência entre inscrição, time e participação em partidas.  
  - **Tipo**: Estrutural.  
  - **Relação com regras existentes**: Corresponde a `RN-PEND-04` (validação de jogadores da partida pertencem ao campeonato) e regras de integridade referencial de time/jogador.

#### Grupo 4 – Partidas e Estatísticas

- **BRM-10 (A – Visualização de Jogadores da Partida)**  
  - **Descrição**: Ao visualizar uma partida, o administrador deve conseguir ver todos os jogadores do campeonato que participaram daquela partida, juntamente com suas estatísticas (assistências, gols, cartões, minutos, etc.).  
  - **Tipo**: Ação (orientada à UI/consulta).  
  - **Relação com regras existentes**: Alinhada com `RN-MATCH-03` a `RN-MATCH-07` e endpoints de consulta de partidas/estatísticas.

- **BRM-11 (A/D – Registro de Estatísticas na Partida)**  
  - **Descrição**: Na tela de partida, o administrador deve poder registrar ou editar as estatísticas de cada jogador (gols, assistências e participação), disparando o recálculo das estatísticas agregadas do jogador e o cálculo automático do placar da partida.  
  - **Tipo**: Ação + Derivação.  
  - **Relação com regras existentes**: Conecta `RN-MATCH-05/06/07` com `RN-STATS-01` a `RN-STATS-07` e `RN-PEND-05`.

#### Grupo 5 – Navegação via Dashboard

- **BRM-12 (A – Navegação por Itens do Dashboard)**  
  - **Descrição**: O administrador deve ser capaz de clicar em cada item (card, link ou atalho) do dashboard e ser redirecionado para a tela correspondente (por exemplo, lista de jogadores, lista de times, campeonatos, partidas, estatísticas agregadas).  
  - **Tipo**: Ação (regra de UX/navegação).  
  - **Observação**: Deve haver um mapeamento claro entre cada card do dashboard e sua rota de destino.

#### Grupo 6 – Buscas de Jogadores

- **BRM-13 (A – Busca de Jogadores ao Inscrever em Campeonato)**  
  - **Descrição**: Ao adicionar jogadores a um campeonato, o administrador deve poder buscar jogadores pelo nome (total ou parcial), facilitando a inscrição via autocomplete ou lista filtrada.  
  - **Tipo**: Ação.  
  - **Relação com regras existentes**: Complementa `RN-PEND-01` e `RN-PEND-02` (inscrição/listagem de jogadores por campeonato).

- **BRM-14 (A – Busca de Jogadores no Dashboard)**  
  - **Descrição**: A partir do dashboard, o administrador deve poder buscar todos os jogadores cadastrados no sistema, com filtro por nome e, idealmente, por outras propriedades (posição, time, status).  
  - **Tipo**: Ação.  
  - **Relação com regras existentes**: Compatível com `RN-STATS-07` (busca avançada de jogadores) e componentes de UI de players/dashboard.

#### Grupo 7 – Importação via Planilhas

- **BRM-15 (A – Inscrição Automática de Jogadores na Importação de Campeonatos)**  
  - **Descrição**: Ao importar campeonatos a partir da planilha de campeonatos, os jogadores listados para cada campeonato devem ser automaticamente inscritos nesse campeonato, respeitando constraints de limite de jogadores e consistência de IDs.  
  - **Tipo**: Ação.  
  - **Origem**: Regra de negócio de importação (scripts `scripts/python/*.py` e/ou fluxos futuros de UI).

- **BRM-16 (D – Derivação de Posição Goleiro por "GK" no Nome)**  
  - **Descrição**: Ao importar jogadores a partir da planilha de jogadores, se o nome do jogador contiver "GK" (por exemplo, "João Silva GK"), o sistema deve inferir automaticamente a posição do jogador como goleiro, caso nenhuma posição mais específica tenha sido definida.  
  - **Tipo**: Derivação.  
  - **Origem**: Regra de negócio de importação de jogadores (`scripts/python/read_excel.py`, `seed_mongodb.py` e correlatos).

### Questões Abertas e Lacunas Identificadas

- **Q-01 – Modelo de Temporadas**:  
  - Não está totalmente explícito no modelo atual como múltiplas temporadas por campeonato são representadas (um campeonato por temporada vs. campeonato com coleção de temporadas).  
  - **Sugestão de regra estrutural adicional**:  
    - Cada combinação `campeonato + temporada` deve ser única, e o modelo de dados deve representar explicitamente temporadas ativas e passadas (por exemplo, entidade `championship-season`).

- **Q-02 – Reabertura/Desfazer Finalização de Campeonato**:  
  - O enunciado não especifica se é permitido reabrir um campeonato já finalizado ou corrigir títulos concedidos de forma equivocada.  
  - **Sugestão de regras adicionais**:  
    - Definir se há uma operação de "reabrir" campeonato, incluindo regras para estornar títulos já incrementados.

- **Q-03 – Critérios de Busca de Jogadores**:  
  - Não está totalmente definido se a busca por jogadores (BRM-13 e BRM-14) é case-insensitive, se suporta acentos, paginação e ordenação padronizada.  
  - **Sugestão**:  
    - Normalizar buscas para serem case-insensitive e accent-insensitive, com limites de resultados para performance e ordenação consistente (por exemplo, por nome).

- **Q-04 – Conflitos na Importação por Planilhas**:  
  - Não está especificado o comportamento quando um jogador ou campeonato já existe no sistema e também aparece na planilha (duplicidade; conflitos de dados).  
  - **Sugestão**:  
    - Definir regras de merge vs. sobrescrita, tratamento de duplicados e validação de integridade ao inscrever jogadores automaticamente.

- **Q-05 – Derivação de "GK" quando Posição já Existe**:  
  - Não está definido o que acontece se a planilha descreve um jogador com "GK" no nome mas também já traz um campo de posição explícito divergente.  
  - **Sugestão**:  
    - Regra de precedência: posição explícita prevalece sobre inferência por "GK"; a inferência só é aplicada quando a posição não for fornecida.

- **Q-06 – Navegação do Dashboard para Novas Funcionalidades**:  
  - O escopo exato dos itens do dashboard (quais cards e quais telas) não está totalmente detalhado.  
  - **Sugestão**:  
    - Manter um mapeamento de navegação formal (tabela card → rota) para garantir consistência entre regras de negócio e implementação.

### Avaliação de Completude

- **Cobertura**: As regras BRM-01 a BRM-16 cobrem os pontos principais descritos no enunciado original (CRUD, temporadas, finalização, estatísticas, dashboard, buscas e importação via planilhas).  
- **Integração com regras existentes**: A maior parte dessas regras já está mapeada e/ou implementada nas seções anteriores (`RN-*`), especialmente no que diz respeito a campeonatos, partidas, estatísticas e finalização.  
- **Lacunas**: As principais lacunas concentram-se na modelagem explícita de temporadas múltiplas, políticas de reabertura de campeonatos, detalhes finos de busca e estratégias de resolução de conflitos em importações de planilhas.  
- **Próximos passos recomendados**: Validar as questões abertas com o time de negócio e, uma vez decididas, promover essas regras candidatas a regras oficiais (com novos identificadores `RN-*`) e alinhar implementações (API, scripts de importação e UI).

# Regras de Negócio - Sistema Galáticos

**Última atualização:** 19 de Fevereiro de 2026

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
- **Arquivo**: `src/galaticos/handlers/matches.clj` (linha 10-12)
- **Campos Permitidos**:
  - `championship-id`: ID do campeonato
  - `home-team-id`: ID do time mandante (nosso time)
  - `away-team-id`: ID do time visitante (opcional; na plataforma de um único time não é usado)
  - `date`: Data da partida
  - `location`: Local
  - `round`: Rodada
  - `status`: Status
  - `opponent`: Nome do time adversário (texto)
  - `venue`: Local da partida
  - `result`: Resultado (texto)
  - `away-score`: Placar do adversário (manual; aceito como input — não há estatísticas do adversário)
  - `player-statistics`: Array de estatísticas dos jogadores
  - `notes`: Observações
- **Campos calculados (somente leitura)**:
  - `home-score`: Calculado automaticamente pela soma dos gols em `player-statistics` do time mandante; não aceito na criação/atualização.

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


**Documento gerado automaticamente através da análise do código-fonte.**
**Última atualização: 29 de Janeiro de 2026**
**Para dúvidas ou sugestões, consulte o código-fonte nos arquivos mencionados.**

