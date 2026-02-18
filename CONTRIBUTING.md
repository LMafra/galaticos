# Guia de Contribuição

Obrigado por considerar contribuir com o projeto Galáticos!

## Como Contribuir

### Reportando Bugs

Se você encontrou um bug, por favor:

1. Verifique se o bug já não foi reportado nas [Issues](https://github.com/seu-usuario/galaticos/issues)
2. Se não foi reportado, crie uma nova issue usando o template de [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md)
3. Forneça o máximo de detalhes possível sobre o bug

### Sugerindo Melhorias

Para sugerir uma nova funcionalidade:

1. Verifique se a funcionalidade já não foi sugerida
2. Crie uma nova issue usando o template de [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md)
3. Descreva claramente a funcionalidade e seu caso de uso

### Enviando Pull Requests

1. **Fork o repositório**
2. **Crie uma branch para sua feature** (`git checkout -b feature/MinhaFeature`)
3. **Faça suas alterações**
4. **Commit suas mudanças** (`git commit -m 'Adiciona alguma feature'`)
5. **Push para a branch** (`git push origin feature/MinhaFeature`)
6. **Abra um Pull Request**

### Padrões de Código

- Siga as convenções de código Clojure
- Use kebab-case para nomes de funções e variáveis
- Adicione comentários para código complexo
- Mantenha funções pequenas e focadas
- Escreva testes quando possível

### Estrutura de Commits

Use mensagens de commit descritivas:

```
feat: adiciona funcionalidade de busca de jogadores
fix: corrige cálculo de estatísticas agregadas
docs: atualiza documentação do schema
refactor: reorganiza módulos de banco de dados
```

### Testes e Cobertura

Antes de enviar um PR, certifique-se de:

- [ ] O código compila sem erros
- [ ] Todos os testes passam: `./bin/galaticos test`
- [ ] **Cobertura atinge os requisitos mínimos (80% linhas, 70% branches)**
- [ ] Novos recursos incluem testes adequados
- [ ] As dependências estão atualizadas
- [ ] Não há warnings do compilador
- [ ] O código segue os padrões do projeto

#### Requisitos de Cobertura

O projeto mantém os seguintes requisitos de cobertura de código:

- **Cobertura de Linhas**: ≥ 80%
- **Cobertura de Branches**: ≥ 70%

**Validar cobertura localmente:**

```bash
# Cobertura backend (obrigatória)
./bin/galaticos coverage

# Cobertura completa (backend + E2E)
./bin/galaticos coverage:all

# Visualizar relatórios
open target/coverage-report/index.html
```

**O que fazer se a cobertura estiver baixa:**

1. Execute `./bin/galaticos coverage` para ver o relatório
2. Identifique áreas não cobertas (linhas vermelhas no relatório HTML)
3. Adicione testes para cobrir essas áreas
4. Repita até atingir 80/70

**Importante:** PRs que não atingirem os thresholds de cobertura serão bloqueados automaticamente pelo CI. Veja [docs/testing-coverage.md](docs/testing-coverage.md) para mais detalhes.

## Ambiente de Desenvolvimento

### Setup

1. Clone o repositório
2. Instale as dependências Clojure: `clj -M:dev`
3. Configure o MongoDB localmente (ou use Docker: `./bin/galaticos docker:dev start`)
4. Execute os scripts de setup: `./bin/galaticos db:setup`

### Executando Localmente

```bash
# Iniciar REPL
clj -M:dev

# Ou usar Docker
docker-compose -f docker-compose.dev.yml up
```

## Perguntas?

Se você tiver dúvidas, abra uma issue ou entre em contato com os mantenedores do projeto.

Obrigado por contribuir! 🚀

