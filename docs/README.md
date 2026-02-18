# Galáticos - Documentação do Projeto

**Última atualização:** 29 de Janeiro de 2026

Sistema de gestão de elenco esportivo com rastreamento de estatísticas, campeonatos, times e jogadores.

---

## 📊 Status do Projeto

### Arquitetura
- **Backend:** Clojure + Ring/Compojure + MongoDB
- **Frontend:** ClojureScript + Reagent
- **Auth:** JWT stateless
- **Build:** Shadow-cljs + Docker

### Completude

| Fase | Status | Percentual |
|------|--------|-----------|
| **Core (Fase 1)** | ✅ Completo | 100% |
| **Qualidade (Fase 2)** | 🟡 Parcial | 50% |
| **Pós-lançamento (Fase 3)** | ⚪ Pendente | 0% |

**✅ PRONTO PARA LANÇAMENTO BÁSICO** (com limitações conhecidas)

---

## 🎯 Funcionalidades Core (Implementadas)

### Autenticação e Autorização
- ✅ JWT com tokens stateless (HS256)
- ✅ Login/Logout/Check-auth
- ✅ Proteção de rotas frontend
- ✅ Middleware de autenticação

### CRUD Completo
- ✅ **Campeonatos**: Create, Read, Update, Delete
- ✅ **Jogadores**: CRUD + Soft delete
- ✅ **Times**: CRUD + Gestão de jogadores
- ✅ **Partidas**: CRUD + Estatísticas

### Integridade Referencial
- ✅ Campeonatos: não pode deletar se tiver partidas (409)
- ✅ Times: não pode deletar se tiver jogadores (409)
- ✅ Jogadores: soft delete para preservar histórico
- ✅ Partidas: recalcula stats automaticamente

### Estatísticas
- ✅ Agregação por campeonato
- ✅ Top players por métrica (gols, assistências, títulos)
- ✅ Evolução de performance temporal
- ✅ Comparação entre campeonatos
- ✅ Recalculo automático em alterações

---

## ⚠️ Regras de Negócio Pendentes

### 🔴 Alta Prioridade (CRÍTICO para produção)

#### 1. Cálculo Automático de Placar
- **Status**: ❌ Não implementado
- **Problema**: Placar é manual e pode divergir dos gols
- **Risco**: Inconsistência de dados
- **Esforço**: 1 dia
- **Impacto**: ALTO

#### 2. Finalização de Campeonato
- **Status**: ❌ Não implementado
- **Problema**: Não há processo para definir vencedores e títulos
- **Risco**: Sistema de títulos incompleto
- **Esforço**: 1-2 dias
- **Impacto**: ALTO

### 🟡 Média Prioridade

#### 3. Sistema de Inscrições
- **Status**: ❌ Não implementado
- **Falta**: Limite de jogadores, lista de inscritos, endpoints
- **Esforço**: 1-2 dias

#### 4. Validação de Inscrições em Partidas
- **Status**: ❌ Não implementado
- **Falta**: Validar que jogadores da partida estão no campeonato
- **Esforço**: 0.5 dia

#### 5. UI Melhorada para Partidas
- **Status**: ❌ Não implementado
- **Falta**: Dropdown de jogadores filtrados por campeonato
- **Esforço**: 0.5-1 dia

---

## 🛠️ Pendências Técnicas

### Críticas (Fazer antes do lançamento)
- [ ] Smoke tests E2E (1-2 dias)
- [ ] RN-PEND-05: Placar automático (1 dia)
- [ ] RN-PEND-03: Finalização de campeonato (1-2 dias)

### Alta Prioridade
- [ ] Tratamento de 404 em telas de detalhe (0.5 dia)
- [ ] Validações de formulário melhoradas (0.5-1 dia)
- [ ] Endpoint reconciliação manual de stats (0.5-1 dia)

### Média Prioridade
- [ ] RN-PEND-01,02,04,06: Sistema de inscrições (2.5-4 dias)
- [ ] Telas de agregações avançadas (2-4 dias)
- [ ] Refresh automático por tempo (0.5 dia)

### Baixa Prioridade (Pós-lançamento)
- [ ] Acessibilidade básica (0.5-1 dia)
- [ ] Monitoramento e logs estruturados (0.5-1 dia)
- [ ] API base URL configurável (0.5 dia)

---

## 📋 Regras de Negócio Implementadas (Resumo)

### Autenticação (6 regras)
- JWT stateless com HS256
- Bearer token authentication
- Bypass em dev/test
- Logout client-side
- Credenciais via bcrypt

### Campeonatos (6 regras)
- Campos obrigatórios: name, season, titles-count
- Validação de campos desconhecidos
- Filtragem por status
- Proteção contra deleção com partidas
- Timestamps automáticos

### Times (7 regras)
- Campo obrigatório: name
- Lista de jogadores ativos (array)
- Add/remove jogadores (com $addToSet/$pull)
- Proteção contra deleção com jogadores
- Normalização de IDs

### Jogadores (7 regras)
- Campos obrigatórios: name, position
- Soft delete (active: false)
- Estatísticas inicializadas zeradas
- Filtragem de ativos
- Timestamps automáticos

### Partidas (8 regras)
- Campos obrigatórios: championship-id, player-statistics
- Validação de array não vazio
- Recalculo automático de stats (create/update/delete)
- Filtragem por campeonato
- Ordenação por data

### Estatísticas (10 regras)
- Agregação por campeonato
- Pipeline MongoDB para cálculos
- Top players por métrica
- Busca avançada com filtros
- Comparação entre campeonatos
- Validação de integridade

### Validação (5 regras)
- Campos obrigatórios
- Campos desconhecidos rejeitados
- ObjectId validado
- Atualização parcial permitida

### Integridade Referencial (5 regras)
- Deny delete com dependências
- Soft delete preserva histórico
- Verificação de existência antes de operações

### Formatação (5 regras)
- Formato padronizado: `{success, data/error}`
- Códigos HTTP semânticos
- ObjectId → string
- Date → ISO-8601
- Stack trace em logs

**Total: 65 regras implementadas**

---

## 🧪 Testes e Cobertura

### Requisitos
- **Cobertura de linhas**: ≥ 80%
- **Cobertura de branches**: ≥ 70%

### Comandos

```bash
# Cobertura backend
./bin/galaticos coverage

# Cobertura E2E
./bin/galaticos coverage:e2e

# Cobertura completa
./bin/galaticos coverage:all

# Apenas testes (sem cobertura)
./bin/galaticos test
```

### Relatórios
- Backend: `target/coverage/index.html`
- Consolidado: `target/coverage-report/index.html`

### CI/CD
- ✅ Check verde: pode fazer merge
- ❌ Check vermelho: merge bloqueado
- Artifacts disponíveis para download

---

## 🚀 Roadmap de Lançamento

### Opção 1: Lançamento Rápido (Hoje)
**Status**: Sistema funcional com limitações conhecidas

**Riscos**:
- Placar pode ficar inconsistente
- Sistema de títulos incompleto
- Sem controle de inscrições

**Recomendação**: Documentar limitações para usuários

### Opção 2: Lançamento com Qualidade (3-5 dias)
**Implementar**:
1. ✅ Placar automático (1 dia)
2. ✅ Finalização de campeonato (1-2 dias)
3. ✅ Smoke tests E2E (1-2 dias)

**Resultado**: Sistema production-ready sem riscos críticos

### Opção 3: Lançamento Completo (7-10 dias)
**Implementar**:
- Opção 2 +
- Sistema de inscrições
- Validações melhoradas
- Tratamento de 404

**Resultado**: Sistema robusto e completo

---

## 📚 Contratos da API

### Formato de Resposta
```json
// Sucesso
{
  "success": true,
  "data": {...}
}

// Erro
{
  "success": false,
  "error": "mensagem"
}
```

### Códigos HTTP
- `200` OK (leitura/atualização)
- `201` Created
- `400` Bad Request (validação)
- `401` Unauthorized
- `404` Not Found
- `409` Conflict (integridade)
- `500` Internal Error

### Autenticação
```http
Authorization: Bearer <token>
```

---

## 🔧 Configuração

### Variáveis de Ambiente

```bash
# JWT
JWT_SECRET=seu-segredo-aqui
JWT_TTL_SECONDS=86400

# MongoDB
MONGODB_URI=mongodb://localhost:27017/galaticos

# Auth (apenas dev/test)
DISABLE_AUTH=true  # NÃO usar em produção

# Ambiente
APP_ENV=development  # ou production
```

### Docker

```bash
# Desenvolvimento
./bin/galaticos docker:dev start

# Produção
./bin/galaticos docker:prod build
./bin/galaticos docker:prod start
```

---

## 📖 Guias Rápidos

### Adicionar Nova Entidade

1. **Schema MongoDB** (`docs/mongodb-schema.md`)
2. **DB Layer** (`src/galaticos/db/*.clj`)
3. **Handlers** (`src/galaticos/handlers/*.clj`)
4. **Rotas** (`src/galaticos/routes/*.clj`)
5. **Frontend** (`src-cljs/galaticos/components/*.cljs`)
6. **Testes** (`test/` e `test-cljs/`)

### Adicionar Nova Regra de Negócio

1. Implementar lógica no DB/Handler
2. Adicionar validação
3. Adicionar testes
4. Documentar em `regras-de-negocio.md`
5. Verificar cobertura: `./bin/galaticos coverage`

### Debugging

```bash
# Logs do backend
docker-compose logs -f app

# Logs do MongoDB
docker-compose logs -f mongodb

# REPL
./bin/galaticos repl
```

---

## 🐛 Troubleshooting

### Erro 401 em todas as requisições
- Verifique se token está no localStorage
- Confirme que `JWT_SECRET` está configurado
- Check se token não expirou

### Erro 409 ao deletar
- Normal: protege integridade referencial
- Solução: remova dependências primeiro

### Cobertura abaixo do threshold
1. `./bin/galaticos coverage`
2. Abrir `target/coverage/index.html`
3. Identificar áreas vermelhas
4. Adicionar testes específicos

### Testes E2E falhando
- Confirme que app está rodando em `localhost:3000`
- Verifique logs: `docker-compose logs app`
- Execute manualmente: `npm run test:e2e`

---

## 📁 Estrutura de Arquivos

```
galaticos/
├── src/                    # Backend Clojure
│   └── galaticos/
│       ├── db/            # Camada de dados
│       ├── handlers/      # Request handlers
│       ├── middleware/    # Auth, logging, etc
│       ├── routes/        # Definição de rotas
│       └── util/          # Utilitários
├── src-cljs/              # Frontend ClojureScript
│   └── galaticos/
│       ├── components/    # Componentes React
│       ├── api.cljs       # Cliente HTTP
│       └── routes.cljs    # Roteamento
├── test/                  # Testes backend
├── test-cljs/            # Testes frontend
├── e2e/                  # Testes E2E Playwright
├── docs/                 # Documentação
├── scripts/              # Scripts de build/deploy
└── resources/            # Assets estáticos
```

---

## 🔗 Links Úteis

### Documentação Detalhada
- `docs/regras-de-negocio.md` - Todas as 71 regras implementadas
- `docs/mongodb-schema.md` - Schema do banco
- `docs/testing-coverage.md` - Guia de testes completo
- `CONTRIBUTING.md` - Como contribuir

### Recursos Externos
- [Clojure](https://clojure.org/)
- [ClojureScript](https://clojurescript.org/)
- [Reagent](https://reagent-project.github.io/)
- [MongoDB](https://www.mongodb.com/docs/)
- [Playwright](https://playwright.dev/)

---

## 🎨 UI Design System

### Technology Stack
- **Framework**: Tailwind CSS 3.x
- **Charts**: Recharts 2.x
- **Icons**: Lucide React
- **Responsive**: Mobile-first, breakpoints at 768px, 1024px, 1280px

### Color Palette
- Primary (Maroon): `#820000`
- Secondary (Gold): `#FFD500`
- Accent: `#F97316`
- Danger: `#EF4444`
- Neutral: `#F9FAFB` → `#111827`

### Component Library
See `src-cljs/galaticos/components/common.cljs` for:
- Cards, buttons, forms, tables, badges, alerts, modals
- All components use Tailwind utility classes
- Consistent spacing, shadows, and rounded corners

### Design Principles
- Mobile-first responsive design
- Card-based information architecture
- Data visualization for key metrics
- Accessible (WCAG 2.1 AA target)
- Clean, professional sports management aesthetic

---

## 📞 Suporte

### Dúvidas Frequentes

**P: Posso lançar agora?**
R: Sim, mas com riscos. Recomendado implementar Fase 1 (3-5 dias) primeiro.

**P: Qual a prioridade máxima?**
R: Placar automático e finalização de campeonato.

**P: Como adiciono um novo campo?**
R: 1) Adicionar em `allowed-fields`, 2) Migrar dados se necessário, 3) Atualizar testes.

**P: Cobertura obrigatória?**
R: Sim, 80% linhas, 70% branches. CI bloqueia se não atingir.

**P: Posso desabilitar auth?**
R: Apenas em dev/test com `DISABLE_AUTH=true`.

### Canais
1. Documentação deste README
2. Issues no GitHub
3. Canal de desenvolvimento do time

---

## 📊 Métricas do Projeto

- **Regras de negócio**: 65 implementadas, 6 pendentes
- **Cobertura de testes**: Backend ≥80%, Branches ≥70%
- **Completude geral**: 95% backend, 85% frontend
- **Status**: ✅ Pronto para lançamento básico
- **Tempo para production-ready**: 3-5 dias
- **Tempo para completo**: 11.5-20 dias

---

**Versão da documentação:** 1.0  
**Consolidado em:** 29 de Janeiro de 2026  
**Status do projeto:** PRONTO PARA LANÇAMENTO BÁSICO  
**Próxima revisão:** Após implementação da Fase 1

