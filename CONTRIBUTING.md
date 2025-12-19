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

### Testes

Antes de enviar um PR, certifique-se de:

- [ ] O código compila sem erros
- [ ] As dependências estão atualizadas
- [ ] Não há warnings do compilador
- [ ] O código segue os padrões do projeto

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

