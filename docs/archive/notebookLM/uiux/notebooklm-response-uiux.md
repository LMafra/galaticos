> **Archived** — historical NotebookLM material (may be Portuguese). For current docs see [docs/README.md](../../../README.md) and [reference/](../../../reference/).

# NotebookLM Responses – UI/UX (Galáticos)

Respostas do notebook **UI/UX**. Preencher após colar cada prompt de [notebooklm-prompts-uiux.md](notebooklm-prompts-uiux.md).

**Workflow:** uma resposta por secção → consolidar **Decisões consolidadas** → derivar planos (Cursor ou `docs/backlog/`) seguindo **Como derivar planos** abaixo.

### Instruções (acção manual)

1. Abrir o notebook **UI/UX** no NotebookLM
2. Copiar o prompt da secção N de [notebooklm-prompts-uiux.md](notebooklm-prompts-uiux.md)
3. Colar no chat; aguardar resposta
4. Colar a resposta na secção N abaixo (substituir o placeholder)
5. Repetir para secções 1–20
6. Preencher **Decisões consolidadas** e **Notas de sessão**; usar **Como derivar planos** para abrir trabalho no repositório

---

## 1. Auditoria heurística (Nielsen + domínio esportivo)

_Prompt: [secção 1](notebooklm-prompts-uiux.md#1-auditoria-heurística-nielsen--domínio-esportivo) de notebooklm-prompts-uiux.md_

Esta auditoria heurística para o sistema **Galáticos** baseia-se nos princípios de Jakob Nielsen, Alan Cooper (About Face), Don Norman (Design of Everyday Things) e tendências de UX para 2026.

### 1. Avaliação por Heurísticas (Nielsen e Notebook)

Abaixo, os achados divididos por heurística, com severidade de 1 (cosmético) a 4 (catastrófico):

*   **Prevenção de Erros (Severidade: 4):** Formulários pesados sem validação em tempo real e o uso de `window.confirm` são problemáticos. O sistema deve tornar o erro impossível através de seletores limitados (spinners, dropdowns) em vez de campos de texto livre para dados críticos como placares ou IDs.
*   **Visibilidade do Status do Sistema (Severidade: 3):** Em tarefas críticas de dia de jogo, o admin precisa de **Feedback de Modelo Rico (RVMF)**. Toasts são transitórios e podem ser ignorados; indicadores persistentes de "Partida em Andamento" ou "Sincronizando Dados" no header mantêm o contexto.
*   **Controle e Liberdade do Utilizador (Severidade: 3):** O processo de merge de jogadores em 3 passos deve permitir desfazer ações facilmente. Cooper defende que o software deve ser imune a inconsistências, permitindo que o admin "force" uma regra se necessário no calor do jogo (fudgeability).
*   **Correspondência com o Mundo Real (Severidade: 2):** A terminologia deve seguir o vocabulário controlado do futebol (ex: "elenco" vs "lista de usuários").
*   **Consistência e Padrões (Severidade: 2):** O uso de URLs em hash (#/) deve garantir que o botão "voltar" do navegador não cause perda de dados em formulários longos.

### 2. Heurísticas Específicas: Dados Tabulares e Formulários Longos

Para o contexto esportivo administrativo, que exige **postura soberana** (uso intensivo por períodos longos):

*   **Heurística de Imunidade de Dados:** Ao encontrar um erro num formulário de 20 campos, o sistema **nunca deve limpar os campos válidos** (o erro do "professor de geografia").
*   **Heurística de Entrada Bifurcada:** Dados tabulares devem permitir entrada direta na célula (estilo Excel) para estatísticas rápidas, em vez de abrir um modal para cada jogador.
*   **Heurística de Redução de Excrescência (Excise):** Minimizar a navegação entre janelas. Se o admin está registrando uma partida, ele deve ver o elenco lateralmente sem sair da tela atual.
*   **Critério de Aceite Mensurável:** O admin deve ser capaz de registrar uma substituição e dois eventos de estatística em menos de **15 segundos** em desktop e mobile.

### 3. Top 10 Problemas de Usabilidade Prováveis

1.  **Navigational Excise:** Excesso de cliques na sidebar para alternar entre "Partida" e "Jogadores" durante o jogo.
2.  **Trauma de Navegação em Hash:** URLs em hash que não salvam o estado da scroll, fazendo o admin perder a posição na tabela de stats.
3.  **Diálogos Obsequiosos:** O uso de `window.confirm` para ações frequentes (ex: "Tem certeza que quer salvar o gol?") irrita o usuário experto.
4.  **Falta de Dicas de Cursor (Cursor Hinting):** Em tabelas densas, o admin não sabe quais colunas são editáveis ou ordenáveis por falta de mudança no ícone do mouse.
5.  **Hierarquia Visual Fraca em Estatísticas:** Dados de gols, cartões e faltas misturados sem contraste de cor ou tamanho.
6.  **Mobile: Alvos de Toque Pequenos:** Tabelas de estatísticas que exigem precisão de mouse num tablet à beira do campo.
7.  **Input de Dados Rígido:** Exigir formato exato de data ou hora (ex: HH:MM:SS) em vez de aceitar formatos flexíveis.
8.  **Vulnerabilidade à Interrupção:** Se o admin fecha a aba por erro, os dados da partida não salvos são perdidos (falta de Auto-save).
9.  **Falta de "Signifiers" de Pliancy:** Botões de ação que parecem labels estáticos (estilo flat exagerado).
10. **Blank Slates:** Dashboard vazio no início da temporada sem guias para "Cadastrar Primeiro Campeonato".

### 4. Quick Wins vs. Mudanças Estruturais

#### Quick Wins (Implementação em dias)
*   **Trocar `window.confirm` por desfazer (Undo):** Permitir que o admin salve e, se errar, use um botão "Desfazer" num toast persistente.
*   **Destaque Visual em Tabelas:** Usar cores de destaque (accent colors) para eventos críticos como Cartão Vermelho.
*   **Inline Validation:** Validar campos conforme o admin muda de input (onBlur), evitando a surpresa de erros ao clicar em "Finalizar".
*   **Proximidade de Botões:** Agrupar botões de "Substituição" perto da lista de jogadores reservas.

#### Mudanças Estruturais (Próximos Sprints)
*   **Adotar Postura Soberana com Painéis Adjacentes:** Dividir a tela de partida em painéis (Sidebar de eventos | Tabela Central | Detalhes do Jogador) para eliminar navegação.
*   **Sistema de Recuperação por Atributos:** Em vez de navegação hierárquica, usar filtros facetados para encontrar jogadores por posição, idade ou status médico rapidamente.
*   **Design Responsivo com Breakpoints:** Criar uma visão de "Card Stack" para mobile em vez de tabelas horizontais longas.
*   **Critério de Aceite Mensurável:** Redução de **40% na taxa de erro** de entrada de dados após implementação de widgets limitados.

**Recomendação de Tendência (2026):** Implementar **Dark Mode** nativo para administradores que trabalham em estádios com alta ou baixa luminosidade, reduzindo a fadiga ocular.

---

## 2. Design system e tokens visuais

_Prompt: [secção 2](notebooklm-prompts-uiux.md#2-design-system-e-tokens-visuais) de notebooklm-prompts-uiux.md_

Para estruturar um **Design System (DS) mínimo e coerente** para os Galáticos, focaremos na transição entre o **Plano de Estrutura** (padrões de interação) e o **Plano de Superfície** (design sensorial). O objetivo é suportar uma **postura soberana** (uso administrativo intensivo) com eficiência e baixo esforço cognitivo.

### 1. Tokens de Design (Prioridade de Documentação)

A documentação deve começar pelos elementos que garantem a **consistência externa e interna**.

*   **Cores Semânticas:** Em vez de documentar apenas "maroon", use nomes baseados na função para facilitar o Dark Mode.
    *   `color-brand`: `brand-maroon`.
    *   `color-surface-base`: `slate-50` (Light) / `slate-950` (Dark).
    *   `color-surface-card`: `white` / `slate-900`.
    *   `color-text-primary`: `slate-900` / `slate-100`.
*   **Espaçamento (Grid Atômico):** Adote um sistema de **8px/4px** para regularizar o posicionamento. Isso melhora a legibilidade e reduz a fadiga visual em apps densos.
*   **Raio (Radius) e Sombras:** Use `rounded-lg` para cards e `rounded-md` para inputs/botões. Sombras devem ser suaves (ex: `shadow-sm`) no modo claro e substituídas por bordas sutis (`border-slate-800`) no modo escuro para evitar "ruído visual".
*   **Tipografia:** Utilize uma fonte sans-serif nítida (como Inter ou Geist). Documente pesos: `font-bold` para cabeçalhos de estatísticas e `font-medium` para labels de formulário.

### 2. Matriz de Variantes (Botões e Alerts)

Utilize os conceitos de **Significadores** de Norman para indicar o que pode ser feito.

| Componente | Variante | Contexto de Uso (Regra de Negócio) | Exemplo Tailwind |
| :--- | :--- | :--- | :--- |
| **Button** | `Primary` | Ação principal da tela (ex: "Salvar Partida"). | `bg-brand-maroon text-white hover:brightness-110` |
| | `Secondary` | Ações de suporte ou caminhos alternativos. | `bg-slate-200 text-slate-800 dark:bg-slate-800` |
| | `Danger` | Ações irreversíveis (ex: "Remover Jogador"). Use com cautela (alavanca de assento ejetor). | `bg-red-600 text-white hover:bg-red-700` |
| | `Ghost` | Ações de baixa ênfase ou dentro de tabelas densas. | `text-slate-600 hover:bg-slate-100 dark:hover:bg-slate-800` |
| **Alert** | `Info` | Feedback de status do sistema ou progresso. | `bg-blue-50 border-blue-200 text-blue-800` |
| | `Success` | Confirmação de operação concluída com sucesso. | `bg-emerald-50 border-emerald-200 text-emerald-800` |

### 3. Consistência em Dark Mode (Badges e Tabelas)

O Dark Mode em 2026 foca no **conforto visual**.

*   **Badges de Status:** Para evitar que badges fiquem "apagados" ou excessivamente brilhantes, use **fundo semi-transparente** com texto colorido em Dark Mode.
    *   *Exemplo:* `bg-emerald-500/10 text-emerald-400 border border-emerald-500/20`. Isso mantém a affordance de status sem quebrar a harmonia escura.
*   **Tabelas de Elenco:** Em uma SPA soberana, as tabelas devem ter **indicadores de pliancy** (mudança de cor no hover da linha) para ajudar o admin a não se perder em dados densos.
    *   *Composição Reagent:* Use `divide-y divide-slate-200 dark:divide-slate-800` e `hover:bg-slate-50 dark:hover:bg-slate-900/50`.

### 4. Nomeação e Documentação "Living"

Buley sugere que a documentação deve ser **inclusiva e modular**, agindo como um "livro de receitas".

*   **Onde Viver:** Crie uma rota interna `/ui-lab` ou use o **Storybook** integrado ao Reagent. Isso serve como a "Imagem do Sistema" para os desenvolvedores.
*   **Nomeação de Componentes:** Siga o padrão de **Objeto-Verbo**.
    *   `[match-card ...]`
    *   `[player-badge ...]`
    *   `[stat-field ...]`
*   **Critérios de Aceite para Componentes:**
    1.  **Feedback Modeless:** O componente indica seu estado sem travar a UI (ex: spinner interno no botão enquanto salva).
    2.  **Acessibilidade:** Suporta navegação por teclado (Tab index) e possui contraste de texto de pelo menos 80%.
    3.  **Memória:** O componente lembra estados de configuração anteriores se o usuário sair e voltar (ex: filtros da tabela).

**Padrão de Composição Reagent + Tailwind:**
```clojure
(defn stat-card [{:keys [label value icon color]}]
  [:div {:class ["bg-app-card" "p-4" "rounded-lg" "border" "border-slate-200" "dark:border-slate-800"
                 "flex" "items-center" "gap-4" "shadow-sm"]}
   [:div {:class [(str "text-" color "-500") "bg-slate-100" "dark:bg-slate-800" "p-2" "rounded-full"]}
    [icon {:size 20}]]
   [:div
    [:p {:class ["text-xs" "text-slate-500" "uppercase" "font-semibold"]} label]
    [:h3 {:class ["text-2xl" "font-bold" "text-slate-900" "dark:text-white"]} value]]])
```
Este padrão garante que o **Modelo Representado** no código corresponda ao **Modelo Mental** do admin: dados claros, ícones funcionais e superfícies que respeitam o tema escolhido.

---

## 3. Tipografia, hierarquia e densidade de dados

_Prompt: [secção 3](notebooklm-prompts-uiux.md#3-tipografia-hierarquia-e-densidade-de-dados) de notebooklm-prompts-uiux.md_

Para melhorar a legibilidade e a hierarquia visual do sistema **Galáticos**, especialmente em um cenário de alta densidade de dados e uso em campo via dispositivos móveis, as recomendações baseadas nas fontes seguem os princípios de visualização de dados de Edward Tufte e as diretrizes de interface soberana de Alan Cooper.

### 1. Escala Tipográfica e Escolha de Fontes
O sistema deve utilizar uma fonte sans-serif nítida (como Inter ou Verdana) para garantir a legibilidade em resoluções variadas.
*   **Títulos de Página (H1):** Devem ser amplos para capturar a atenção imediata e definir o contexto. Recomenda-se 32px+.
*   **Subtítulos (H2):** 20-24px, usados para agrupar campos em formulários pesados.
*   **Corpo de Texto:** Mínimo de **14px** para leitura confortável. Tamanhos menores que 10px devem ser evitados, pois tornam-se "peludos" ou ilegíveis.
*   **Números Tabulares:** Devem utilizar **fontes monoespaçadas** (tabular figures) para garantir que colunas de números alinhem-se perfeitamente, facilitando a comparação visual.

### 2. Densidade: Compacta vs. Confortável
A alternância de densidade deve ser ditada pela **postura da aplicação** e pelo dispositivo.
*   **Postura Soberana (Desktop Admin):** O admin gasta horas no sistema. Aqui, a densidade deve ser **compacta**. Controles e barras de ferramentas podem ser menores, pois o usuário ganha familiaridade e "senso inato de onde as coisas estão". Isso permite visualizar mais estatísticas sem rolagem excessiva.
*   **Postura Transiente (Mobile em Campo):** No telemóvel à beira do campo, a atenção é fragmentada. A interface deve ser **confortável**, com alvos de toque (hit areas) generosos e espaçamento maior para evitar erros de entrada de dados sob pressão.

### 3. Alinhamento e Formatação de Números
Conforme Tufte, a apresentação quantitativa deve responder à pergunta "Comparado a quê?".
*   **Alinhamento:** Números em tabelas (gols, minutos) devem ser **alinhados à direita** para permitir a comparação imediata de magnitudes visuais.
*   **Percentagens:** Devem vir acompanhadas de barras de progresso visuais ou "sparklines" dentro da célula, mostrando a proporção (ex: posse de bola ou acerto de passes) em vez de apenas o número isolado.
*   **Consistência de Precisão:** Use precisão consistente (ex: "90'" em vez de "90:00") para reduzir o ruído visual.

### 4. Destaque Visual para Dados Críticos
Utilize o **Feedback de Modelo Rico (RVMF)** para sinalizar estados sem interromper o fluxo com diálogos.
*   **Placar e Status:** Devem ter maior contraste de valor e cor (ex: Maroon da marca para placares ativos).
*   **Alertas de Regra:** Em vez de `window.confirm`, as células da tabela podem mudar de cor (ex: fundo levemente avermelhado) se um jogador estiver suspenso ou com documentação pendente.
*   **Destaque de Seleção:** O estado selecionado deve ser evidente e inequívoco, usando cores de fundo ou bordas sutis, garantindo que não dependa apenas da cor (para acessibilidade a daltônicos).

---

### Wireframe Textual: Tabela de Estatísticas de Partida (Desktop Soberano)

```text
[Header: Partida #42 - Galáticos vs. Rivais | Status: AO VIVO (RVMF: Pulsar Vermelho)]

| Jogador (Align:L) | Gols | Assist. | Cartões | Minutos | Eficiência (%)     |
|-------------------|------|---------|---------|---------|--------------------|
| 07 - J. Silva     |   2  |    1    | [Y]     |   90'   | [██████░░░░] 75%  |
| 10 - M. Craque    |   0  |    2    |         |   78'   | [████████░░] 85%  |
| 04 - B. Rocha     |   0  |    0    | [R]!    |   45'   | [██░░░░░░░░] 20%  | (Alerta visual no R!)

[Rodapé da Tabela: Média da Equipe: 60% de passes certos]
```

### Critérios de Aceite de Legibilidade
1.  **Contraste de Texto:** Mínimo de **80%** entre texto e fundo para conformidade com acessibilidade.
2.  **Tamanho Mínimo:** Nenhuma informação crítica de estatística deve ter menos de **10px**.
3.  **Alvos de Toque (Mobile):** No telemóvel, cada linha da tabela ou botão de ação deve ter no mínimo ** finger-sized** (aprox. 44px de altura) para evitar o erro do "clique vizinho".
4.  **Hierarquia de Decisão:** Ao "espremer os olhos" (squint test), o placar e o tempo de jogo devem ser os primeiros elementos a saltar visualmente.

---

## 4. Arquitetura de informação e navegação

_Prompt: [secção 4](notebooklm-prompts-uiux.md#4-arquitetura-de-informação-e-navegação) de notebooklm-prompts-uiux.md_

Avaliar a arquitetura de informação (IA) do sistema **Galáticos** exige a transição de um "modelo de implementação" (focado em tabelas do banco de dados) para um **"modelo representado"** que espelhe o modelo mental do administrador do clube.

### 1. Mapa Mental Ideal (Agrupamento por Atividades)

Em vez de listar apenas entidades isoladas, a IA deve ser organizada por **esquemas baseados em tarefas ou assuntos**, distinguindo a operação diária da configuração sazonal.

*   **Operações de Jogo (Foco principal):** Agrupar "Partidas" e "Estatísticas". O admin não quer apenas ver uma lista; ele quer registrar eventos.
*   **Gestão de Elenco:** "Jogadores" e "Times". Embora "Times" seja restrito, eles formam a base do elenco.
*   **Organização Competitiva:** "Campeonatos" e "Temporadas". São estruturas de vida longa que servem de "Hub" para as outras entidades.
*   **Dashboard:** Deve servir como uma **"Focal Entry Point"**, destacando a próxima partida ou jogadores pendentes, reduzindo o trabalho cognitivo.

### 2. Breadcrumbs e Alternativas de Contexto

Para evitar o **"trauma de navegação"** em formulários profundos (como o de partida com 20+ campos), os breadcrumbs são essenciais para o "Wayfinding".

*   **Design de Breadcrumbs:** Devem incluir todos os níveis (ex: `Campeonatos » Paulistão 2026 » Rodada 1 » Registrar Placar`). Todos os itens, exceto o atual, devem ser links.
*   **Lateral Links:** No breadcrumb, permitir que o usuário clique em uma seta ao lado de "Paulistão 2026" para alternar rapidamente entre outros campeonatos sem voltar ao menu principal.
*   **Persistent Headers:** O título da página no header deve ser acompanhado de um **Badge de Contexto** (ex: "Série A") para manter o usuário orientado durante a rolagem.

### 3. Consistência de Nomenclatura (UI e URLs)

O uso de termos em inglês nas URLs (`:player-new`) enquanto a UI está em português cria uma dissonância que pode fazer o utilizador "sentir-se estúpido" ou confuso ao tentar usar o botão "Voltar" ou compartilhar links.

*   **Alinhamento PT-PT:** As URLs devem seguir o **vocabulário controlado** da UI: `#/jogadores/novo` em vez de `#/player-new`.
*   **Eliminação de Redundância:** Unificar `:home` e `:dashboard`. Manter múltiplos caminhos para o mesmo destino gera "excrescência" (excise) desnecessária.
*   **Rótulos Amigáveis:** Use verbos claros na UI (ex: "Inscrever em Campeonato" em vez de "Processar Inscrição").

### 4. Fluxos Transversais (Hub & Spoke)

O sistema deve permitir que o admin pule entre contextos relacionados sem perder o progresso, aplicando o padrão **Hub & Spoke**.

*   **Do Campeonato para a Partida:** Dentro da tela de um Campeonato (Hub), o admin deve poder criar uma "Nova Partida" (Spoke). Ao salvar, o sistema deve oferecer o retorno automático para o Campeonato, não para o Dashboard.
*   **Fudgeability:** Se o admin percebe que um jogador não está cadastrado enquanto preenche a súmula da partida, o sistema deve permitir abrir um "painel lateral" de cadastro rápido, evitando que ele abandone o formulário pesado da partida (o que seria uma falha catastrófica de UX).

---

### Diagrama de IA (Arquitetura Proposta)

```text
[Raiz: Dashboard (Resumo de Ações Críticas)]
 ├── [Hub: Elenco]
 │    ├── Lista de Jogadores (Filtros por posição/status)
 │    │    └── Detalhes do Jogador <-> Histórico de Lesões (Transversal)
 │    └── Merge de Duplicados (Fluxo linear 3 passos)
 ├── [Hub: Partidas]
 │    ├── Calendário / Lista (Próximas vs Passadas)
 │    └── Formulário de Súmula (Postura Soberana: Full-screen)
 └── [Hub: Competições]
      └── Temporada Ativa
           └── Campeonatos 
                └── Tabela de Classificação 
                     └── Gerar Partida (Link direto para Súmula)
```

### Critérios de Aceite Mensuráveis
1.  **Encontrabilidade:** O utilizador deve encontrar as estatísticas de um jogador específico a partir de qualquer tela em **$\le$ 3 cliques**.
2.  **Consistência:** 100% das rotas hash devem utilizar os mesmos termos técnicos presentes nos labels da sidebar.
3.  **Recuperação:** Ao clicar em "Voltar" de uma tela de edição de estatísticas, o utilizador deve retornar ao Hub correto (Partida), nunca à raiz do sistema.

---

## 5. Onboarding, login e sessão

_Prompt: [secção 5](notebooklm-prompts-uiux.md#5-onboarding-login-e-sessão) de notebooklm-prompts-uiux.md_

Para melhorar a experiência de autenticação e a primeira utilização da SPA **Galáticos**, as fontes recomendam uma transição da "postura técnica" (baseada no código) para uma **"etiqueta digital"** que respeite o modelo mental do administrador.

### 1. Primeira Visita e Empty States Pós-Login
O maior erro em sistemas administrativos é apresentar um **"Blank Slate"** (quadro branco), o que intimida o utilizador.
*   **Guided Tours e Overlays:** No primeiro login, utilize um carrossel de cartas explicativas ou um *overlay* semitransparente que aponte para as funções críticas de dia de jogo (ex.: "Comece por aqui para registar a partida").
*   **Modelos e Galerias:** Se não houver dados (ex.: início de época), não mostre tabelas vazias. Ofereça um **"Draft de Campeonato"** ou um botão de "Importar Elenco da Época Anterior" para que o admin sinta que o sistema está a trabalhar para ele, não o contrário.
*   **Fudgeability (Maleabilidade):** Permita que o admin explore a interface antes de completar o perfil do clube. "Pedir perdão, não permissão" significa deixar o utilizador entrar e agir, alertando modelarmente sobre dados em falta mais tarde.

### 2. Mensagens de Erro de Login (Segurança vs. Clareza)
O sistema deve ser **educado, iluminador e prestativo**, nunca fazendo o utilizador "sentir-se estúpido".
*   **Evitar Idiotia no Erro:** Em vez de diálogos modais que interrompem o fluxo, utilize **RVMF (Feedback de Modelo Rico)**. No formulário de login, o erro deve aparecer *inline* (ex.: "Utilizador não reconhecido") em vez de um `window.alert` que exige um clique extra.
*   **Clareza nas Credenciais:** Embora a segurança exija certa opacidade, o sistema deve distinguir se o erro é de formato (ex.: email sem @) através de **validação passiva** (ao perder o foco do campo), poupando o utilizador de submeter um formulário destinado ao fracasso.

### 3. Indicadores de Sessão (Conforto e Fluxo)
Como o token JWT dura 24h, a expiração pode ocorrer durante uma tarefa crítica. 
*   **Feedback Modeless:** Utilize um pequeno indicador de "Saúde da Ligação" ou um temporizador discreto no header. Isso evita a surpresa catastrófica de ser expulso do sistema a meio do registo de um golo.
*   **Recuperação de Interrupção:** Se a sessão expirar, o sistema deve **memorizar o estado do formulário** localmente. Ao re-autenticar, o admin deve ser devolvido exatamente ao campo onde estava, preservando o trabalho ("O computador faz o trabalho, a pessoa faz a reflexão").

### 4. Diferença UX Visitante vs. Autenticado
A interface deve adaptar-se à **postura** do utilizador.
*   **Visitante (Postura Transiente):** O dashboard deve focar em **"Keeping up to date"** (Manter-se atualizado) com estatísticas públicas e tabelas de classificação simples, com navegação óbvia e alvos de toque grandes.
*   **Admin Autenticado (Postura Soberana):** O dashboard transforma-se num **"Focal Entry Point"** focado em fluxos de trabalho. Deve apresentar o "Minimal Working Set" (conjunto mínimo de funções) necessário para o dia de jogo, como botões de ação imediata para a partida em curso.

---

### Jornada de Login Proposta
1.  **Entrada:** O admin vê o cartão de login; o sistema lembra o último nome de utilizador usado (Memória do Sistema).
2.  **Validação:** Erros de formato são mostrados via *hints* visuais antes do clique em "Entrar".
3.  **Transição:** Redirecionamento suave. Se for o primeiro acesso, o sistema lança um *Guided Tour* opcional.
4.  **Dashboard:** O Admin entra numa **Interface Soberana** (full-screen, densidade de dados otimizada).

### Critérios de Aceite Mensuráveis
*   **Autonomia de Erro:** O utilizador deve ser capaz de corrigir um erro de digitação no login em menos de **5 segundos** sem fechar diálogos modais.
*   **Eficiência de Primeiro Uso:** Um novo admin deve completar o registo do primeiro jogador em ≤ **60 segundos** seguindo as pistas visuais (overlays).
*   **Continuidade:** 100% dos dados inseridos num formulário não submetido devem ser recuperáveis após um logout/login acidental.

---

## 6. Estados: loading, vazio, erro e 404

_Prompt: [secção 6](notebooklm-prompts-uiux.md#6-estados-loading-vazio-erro-e-404) de notebooklm-prompts-uiux.md_

Com base nos princípios de **Feedback de Modelo Rico (RVMF)**, **Interface Soberana** e o tratamento ético de **"Blank Slates"** (quadros brancos) presentes nas fontes, seguem as recomendações para os estados da interface do sistema Galáticos.

### 1. Estratégia de Carregamento (Loading)

A escolha do componente depende do tempo de latência e da **postura** da aplicação.

*   **Spinner Global:** Deve ser usado apenas no **boot inicial** da aplicação ou em trocas de contexto "pesadas" onde o Modelo Mental do utilizador é completamente resetado. Evite usá-lo em navegações simples entre rotas para não quebrar o **Fluxo (Flow)**.
*   **Skeleton Screens:** Recomendado para telas de **Postura Soberana** (Lista de Jogadores, Dashboard). O skeleton age como um "Signpost" (sinalizador), mantendo a estrutura da página estável enquanto os dados carregam, o que reduz o esforço cognitivo de reorientação visual.
*   **Spinner Inline/Button:** Para ações de **Postura Transiente** (ex: clicar em "Salvar Golo" numa partida). Cooper sugere que o feedback deve ser modeless e integrado ao controle que gerou a ação.

### 2. Estados Vazios (Empty States/Blank Slates)

Cooper afirma que **"o software deve evitar quadros brancos"**. Em vez de apenas informar a ausência de dados, o sistema deve ser prestativo e educativo.

*   **Jogadores:** Em vez de uma tabela vazia, mostre uma ilustração de um balneário vazio com a copy: *"O plantel está pronto para o treino? Registe o seu primeiro jogador para começar as estatísticas."* [Botão: Adicionar Jogador].
*   **Campeonatos (Filtros):** Se o admin filtrar e não houver resultados, o erro pode ser do sistema ou do utilizador. Sugira: *"Não encontramos campeonatos com este filtro. Tente ajustar os anos ou [Botão: Limpar Filtros]"*.

### 3. Hierarquia de Erros: Recuperável vs. Fatal

O sistema nunca deve fazer o utilizador "sentir-se estúpido".

*   **Erro Recuperável (ex: Validação de campo):** Use **RVMF (Feedback de Modelo Rico)**. O campo deve mudar de cor (ex: borda vermelha) e exibir um *hint* (dica) inline explicando a regra (ex: "A data da partida não pode ser no futuro"). Não use diálogos modais para erros que o utilizador pode corrigir no fluxo.
*   **Erro Fatal (ex: Queda de API/Offline):** Deve ser **Considerado**. Explique o problema de forma iluminadora: *"Não conseguimos sincronizar os dados da partida. Gravamos o seu progresso localmente; clique em 'Tentar Novamente' para reenviar"*.

### 4. Página 404 (Rota e Recurso)

*   **Rota Inexistente:** Deve agir como uma ferramenta de **Wayfinding** (encontrabilidade). Em vez de apenas "404", ofereça links para os "Hubs" principais: Dashboard, Elenco ou Partida Atual.
*   **Recurso não encontrado (ex: Jogador ID 999):** Informe que o recurso pode ter sido removido ou o link está expirado, mantendo a sidebar e o header intactos para não desorientar o admin.

---

### Tabela de Estados e Componentes

| Estado | Componente | Exemplo de Mensagem (PT) | Contexto de Uso |
| :--- | :--- | :--- | :--- |
| **Loading (Soberano)** | Skeleton Table | (Sem texto, apenas blocos cinza pulsam) | Lista de Jogadores ou Campeonatos. |
| **Loading (Ação)** | Button Spinner | "A guardar estatísticas..." | No botão de salvar sumário de jogo. |
| **Empty (Início)** | Welcome Card | "Nenhum campeonato inscrito. Comece aqui." | Início de época, área de Competições. |
| **Error (Inline)** | Alert Banner | "Verifique 2 campos assinalados antes de fechar." | Formulário de Partida com erros. |
| **Error (Toast)** | Toast Notification | "Conexão perdida. Tentaremos reconectar." | Erro de rede em segundo plano. |
| **Not Found** | Empty State Hub | "Jogador não encontrado no plantel atual." | Pesquisa de ID ou link quebrado. |

### Critérios de Aceite por Rota Crítica

#### `/matches/new` (Novo Formulário)
*   **Prevenção de Erros:** O botão "Finalizar" deve estar desativado ou avisar modelarmente quais campos obrigatórios faltam, impedindo a submissão de dados inválidos (padrão *poka-yoke*).
*   **Memória:** Se o admin sair da página acidentalmente, o sistema deve recuperar os dados inseridos ao voltar (Fudgeability).

#### `/players` (Lista de Elenco)
*   **Performance Percebida:** O skeleton deve carregar em < 1 segundo para manter a sensação de resposta.
*   **Acessibilidade:** A tabela deve permitir navegação por Tab e setas para que o admin possa consultar dados rapidamente em campo.
*   **Filtros:** Ao aplicar um filtro que resulte em "zero", o sistema deve oferecer uma ação clara de "Limpar Filtros" imediatamente visível.

---

## 7. Feedback, confirmações e ações destrutivas

_Prompt: [secção 7](notebooklm-prompts-uiux.md#7-feedback-confirmações-e-ações-destrutivas) de notebooklm-prompts-uiux.md_

Para padronizar o feedback e as confirmações no sistema **Galáticos**, utilizaremos os princípios de Alan Cooper sobre a eliminação de "excrescência" (excise) e a filosofia de Don Norman sobre a prevenção de erros e o uso de "forcing funções".

### 1. Modal vs. Inline vs. Undo: Quando usar cada um

A escolha do padrão deve ser baseada no nível de risco e na frequência da tarefa:

*   **Padrão de Desfazer (Undo):** Deve ser a resposta padrão para **Deletes** de partidas ou registros de estatísticas. Em vez de interromper o fluxo com um `window.confirm` ("idiocia do sistema"), o sistema deve realizar a ação imediatamente e oferecer um botão "Desfazer" em um toast persistente ou área de notificação. Isso apoia a exploração e reduz o medo do utilizador.
*   **Modal de Confirmação (Ejector Seat):** Reservado apenas para as "alavancas do assento ejetor" — ações raramente realizadas, altamente deslocantes ou tecnicamente irreversíveis, como **Finalizar Temporada** ou **Merge de Jogadores**.
*   **Avisos Inline (Sensibility Checks):** Use para validação passiva em formulários pesados. Se o admin tenta finalizar um campeonato sem todos os resultados, o sistema deve mostrar uma mensagem inline explicando o que falta antes de permitir o clique no botão de ação.

### 2. Conteúdo do Diálogo (O que mostrar)

Conforme Cooper e Norman, os diálogos devem ser **iluminadores e prestativos**, nunca condescendentes.

*   **Título:** Use verbos claros que identifiquem a ação (ex: "Finalizar Campeonato").
*   **Corpo:** Deve exibir o **objeto e a ação** de forma saliente. Em vez de "Tem certeza?", use: *"Ao finalizar o Paulistão 2026, a tabela de classificação será congelada e não poderá mais ser editada. Deseja prosseguir?"*.
*   **Merge de Jogadores:** Como é um processo de 3 passos, o modal deve atuar como um guia pedagógico, mostrando claramente o que será perdido no merge (ex: *"As estatísticas do Jogador B serão somadas ao Jogador A. O registro do Jogador B será apagado."*).

### 3. Feedback Pós-Ação

*   **Toasts (RVMF - Feedback de Modelo Rico):** Use para sucessos rotineiros que não exigem navegação (ex: "Golo registado com sucesso"). Toasts de erro não devem ser transitórios se a mensagem for crítica; devem ser bloqueantes até o admin tomar conhecimento.
*   **Navegação Automática:** Após ações destrutivas como "Apagar Partida", o sistema deve devolver o utilizador ao **Hub de IA** correspondente (Lista de Partidas), mantendo o contexto.
*   **Permanecer na Página:** Apenas em fluxos de edição contínua onde o admin precisa realizar múltiplas alterações sequenciais.

### 4. Acessibilidade em Modais

Para garantir uma interface inclusiva e funcional em 2026, os modais devem seguir as WCAG:

*   **Gerenciamento de Foco:** Ao abrir, o foco deve ir para o botão de ação menos destrutivo (geralmente "Cancelar"). Ao fechar, o foco deve retornar ao elemento que disparou o modal.
*   **Teclado:** O utilizador deve poder navegar por todos os controles usando `Tab`, ativar com `Enter` e fechar/cancelar com `Esc`.
*   **ARIA e Labels:** Todos os elementos visuais devem ter equivalentes textuais para leitores de ecrã (ex: `aria-labelledby` apontando para o título do modal).

---

### Exemplos de Copy (PT-PT)

| Contexto | Tipo de Feedback | Exemplo de Copy |
| :--- | :--- | :--- |
| **Apagar Partida** | Undo (Toast) | "A partida foi removida. [Botão: Desfazer]" |
| **Merge Jogadores** | Modal | "Merge de 'J. Silva' e 'João Silva'. Esta ação unifica o histórico de 42 partidas." |
| **Erro de API** | Toast Bloqueante | "Erro ao sincronizar placar. Verifique a ligação e tente novamente." |
| **Finalizar Época** | Modal Crítico | "Atenção: Finalizar a Época 2025 impedirá a inscrição de novos jogadores neste período." |

### Critérios de Aceite Mensuráveis

1.  **Delete de Partida:** O admin deve conseguir apagar e recuperar uma partida em menos de **8 segundos** sem enfrentar diálogos de interrupção (Fluxo de Undo).
2.  **Merge de Jogadores:** 100% dos merges devem apresentar um resumo final de "Antes vs. Depois" (Preview) antes da confirmação final, permitindo que o admin verifique se escolheu o registro de referência correto.
3.  **Acessibilidade:** Todo modal de ação irreversível deve passar no teste de navegação **apenas por teclado**, sem uso de rato ou toque.

---

## 8. Formulários e validação na interface

_Prompt: [secção 8](notebooklm-prompts-uiux.md#8-formulários-e-validação-na-interface) de notebooklm-prompts-uiux.md_

Para desenhar formulários administrativos complexos para o sistema **Galáticos**, a abordagem deve transitar do modelo de implementação técnica (o que a API espera) para o **modelo mental** do administrador (o que ele precisa realizar). No contexto de uma aplicação de **postura soberana** (uso intensivo por especialistas), o formulário deve ser desenhado para maximizar a produtividade e a fluidez.

### 1. Layout do Formulário: Estrutura e Sequência

Para lidar com a complexidade de uma partida, o formulário deve ser organizado em unidades funcionais claras para reduzir a carga cognitiva.

*   **Agrupamento Sensato:** Divida o formulário em seções lógicas usando *fieldsets* ou áreas de destaque visual (cards).
    *   **Informação de Contexto (Read-only):** No topo, exiba o Campeonato e o Time (se fixos por rota) para manter o **Wayfinding**. Use controles de exibição não editáveis para evitar que o usuário tente clicar e se sinta frustrado.
    *   **Dados da Partida:** Data, Adversário e Placar. Estes são os "Dados Básicos".
    *   **Súmula Técnica (Stats por Jogador):** Uma tabela densa ou lista de cards de jogadores. Como é a parte mais pesada, deve ocupar a maior área da tela.
*   **Ordem e Caminho Lógico:** Organize os campos seguindo o fluxo natural de leitura (topo-baixo, esquerda-direita). Coloque itens usados juntos de forma adjacente para minimizar o movimento do mouse.

### 2. Validação: O Equilíbrio entre API e UI

O software deve ser um guia prestativo, não um "policial de dados".

*   **Validação Ativa (Formato):** Para campos como placares ou minutos, use **controles limitados** (spinners) que tornam o erro impossível. Rejeite caracteres não numéricos em tempo real no teclado.
*   **Validação Passiva (Regras de Negócio):** Para regras que dependem do servidor (ex: jogador já inscrito), valide no **onBlur** (quando o campo perde o foco) ou use um timer de contagem regressiva de ~0,5s após o usuário parar de digitar. Isso permite que a UI consulte a API de forma assíncrona sem interromper o fluxo do admin.
*   **Avisos Amber (RVMF):** Use **Feedback de Modelo Rico (RVMF)** para sinalizar que não há campeonato ativo ou jogadores inscritos. Isso deve ser exibido como um banner persistente ou "Hint" de destaque, não um modal de erro, permitindo que o usuário continue se sua intenção for apenas explorar a tela.

### 3. Apresentação de Erros e Mensagens

O princípio fundamental é **"Auditar, não editar"**.

*   **Hints por Campo:** Quando a API retornar um 400 (Bad Request), exiba a mensagem de erro (ex: "Data inválida") diretamente abaixo do campo afetado usando **Hints** (pop-ups de texto curtos e coloridos).
*   **Resumo no Topo:** Para formulários muito longos, um resumo no topo ajuda o utilizador a perceber que existem pendências fora da área visível (scroll).
*   **Resiliência aos Erros da API:** Se a API retornar um erro fatal (ex: 409 Conflict), nunca descarte os dados inseridos. O sistema deve ser **imune à perda de dados**, mantendo os campos preenchidos para que o admin apenas corrija o conflito e tente novamente (evite o erro do "professor de geografia").

### 4. Estados do Botão Salvar

O botão de ação principal deve refletir o estado do sistema.

*   **Estado Dirty (Pliant):** O botão deve estar visualmente "pronto para ação" assim que qualquer campo for alterado.
*   **Estado Loading:** Ao clicar, se a resposta da API demorar mais que 0,1s, o botão deve exibir um spinner ou mudar o texto para "A guardar..." para indicar que o sistema está ocupado e evitar cliques repetidos.
*   **Não use Disabled:** Evite desativar o botão "Salvar" sem explicação. É melhor permitir o clique e destacar modelessmente o que falta, orientando o usuário a completar o processo.

---

### Wireframe Textual: Formulário de Partida (Admin)

```text
[Header: Nova Partida | Paulistão 2026 (Contexto Fixo)]

(Amber Alert: RVMF) ⚠️ Aviso: Não há jogadores inscritos no elenco principal. [Ir para Elenco]

----------------------------------------------------------------------
[ Seção 1: Dados Gerais ]
Data: [ 10/10/2026 ] (Hint: Data no futuro!)  Adversário: [ SC Galos    ]
Placar: [ 2 ] x [ 1 ] (Spinner Bounded)        Local:     [ Estádio Municipal ]
----------------------------------------------------------------------
[ Seção 2: Estatísticas de Jogadores ]
| Jogador (Inscrito) | Gols | Assist. | Amarelo | Vermelho | Minutos |
|--------------------|------|---------|---------|----------|---------|
| 10 - M. Craque     | [ 2 ]| [ 0 ]   | [ ]     | [ ]      | [ 90 ]  |
| 07 - J. Silva      | [ 0 ]| [ 2 ]   | [x]     | [ ]      | [ 75 ]  | (onBlur: "Suspenso!")
| ...                | ...  | ...     | ...     | ...      | ...     |
----------------------------------------------------------------------

[ Botão: Cancelar ]          [ Botão: Salvar Partida (Estado: Dirty) ]
(Feedback Modeless: "3 campos com validação pendente")
```

### Critérios de Aceite Mensuráveis
1.  **Recuperação de Erro:** O utilizador deve ser capaz de corrigir um erro de validação da API (ex: 400 JSON) e submeter novamente em menos de **10 segundos**, sem que nenhum campo válido tenha sido limpo pelo sistema.
2.  **Visibilidade de Contexto:** 100% dos campos read-only devem ser visualmente distintos dos campos editáveis para evitar tentativas de entrada de dados desnecessárias.
3.  **Tempo de Resposta Visual:** O estado "Loading" deve ser ativado em menos de **100ms** após o clique em "Salvar".

---

## 9. Listas, busca, filtros e paginação

_Prompt: [secção 9](notebooklm-prompts-uiux.md#9-listas-busca-filtros-e-paginação) de notebooklm-prompts-uiux.md_

Para melhorar os padrões de listagem, busca e filtros do sistema **Galáticos**, utilizaremos os princípios de **Postura Soberana**, **Feedback de Modelo Rico (RVMF)** e as diretrizes de navegação para aplicações densas em dados presentes nas fontes.

### 1. Posicionamento de Busca e Filtros (Toolbar Consistente)

Em uma aplicação administrativa (soberana), o utilizador gasta muito tempo interagindo com listas extensas.
*   **Toolbar Fixa no Topo:** Utilize um cabeçalho persistente para manter o contexto, onde a busca e os filtros fiquem sempre visíveis, mesmo durante a rolagem.
*   **Agrupamento por Proximidade:** Posicione a busca à esquerda (fluxo principal de leitura) e os filtros facetados (posição, status) à direita, utilizando o espaçamento para agrupar controles relacionados e reduzir a "excrescência" (excise) de movimento do mouse.
*   **Controles Delimitados:** Substitua campos de texto livre por **controles delimitados** (dropdowns ou combo boxes) nos filtros de posição, para tornar o erro impossível e acelerar a seleção.

### 2. Feedback durante a Busca

O sistema deve ser **iluminador** e evitar estados de "quadro branco" (blank slates).
*   **RVMF (Rich Visual Modeless Feedback):** Em vez de modais de carregamento, utilize indicadores de progresso integrados, como uma barra de carregamento de um pixel no topo da lista ou spinners discretos dentro do campo de busca.
*   **Tratamento de "Sem Resultados":** Nunca exiba apenas uma tela vazia. Se a busca falhar, sugira ações corretivas, como "Limpar todos os filtros" ou verifique se o nome do jogador está correto, mantendo a estrutura da tabela visível para não desorientar o admin.
*   **Auto-complete e Type-ahead:** Implemente sugestões conforme o admin digita, reduzindo o esforço de memória e garantindo que o termo pesquisado tenha resultados válidos.

### 3. Paginação vs. Infinite Scroll para Admin

Para o contexto administrativo dos Galáticos, a **paginação** é recomendada em detrimento do scroll infinito.
*   **Controle e Previsibilidade:** Admins frequentemente precisam chegar ao fim de uma lista ou retornar a um item específico; o scroll infinito dificulta a localização de itens "abaixo da dobra" e impossibilita o uso de rodapés de página.
*   **Trauma de Navegação:** O scroll infinito muitas vezes perde a posição quando o utilizador clica no botão "Voltar" do navegador, o que é inaceitável em uma ferramenta de produtividade.
*   **Navegação por Teclado:** A paginação oferece um alvo fixo para navegação via teclado (Tab e setas), essencial para a eficiência do admin experto.

### 4. Persistência de Filtros

Uma aplicação "inteligente" deve ter memória para poupar trabalho ao utilizador.
*   **URL Query Strings para Estado Atual:** O estado dos filtros e da página atual deve viver na URL. Isso permite que o admin crie **bookmarks** de visões específicas (ex: "Jogadores lesionados") e compartilhe links diretos com outros membros do clube.
*   **localStorage para Preferências de Longo Prazo:** Utilize o armazenamento local para lembrar preferências que não mudam entre sessões, como a densidade da tabela (compacta vs confortável) ou a ordem de classificação preferida.

---

### Padrão de Toolbar Reutilizável (Componente)

```text
[ Toolbar: Background Slate-100 / Dark: Slate-900 | Border-b ]
---------------------------------------------------------------------------------
[ Ícone: Lupa ] [ Busca Jogador (Auto-complete)      ]  [ Filtro: Posição (v) ] [ Filtro: Status (v) ]
[ RVMF: "124 jogadores encontrados" (Tabular Figures) ]  [ Botão: Limpar Filtros (Ghost) ]
---------------------------------------------------------------------------------
```
*   **Dica de Implementação:** Use fontes **monoespaçadas** para os números de contagem de resultados, garantindo que a interface não "pule" quando os números mudam.

### Critérios de Aceite para Listas (>100 registros)

1.  **Latência de Busca:** O feedback visual de carregamento deve aparecer em menos de **0,1 segundos** após o debounce do teclado.
2.  **Acessibilidade de Navegação:** O utilizador deve ser capaz de percorrer todos os filtros e a lista de resultados utilizando apenas a tecla **Tab** e as **setas**.
3.  **Memória de Contexto:** Ao navegar para o perfil de um jogador e clicar em "Voltar", os filtros aplicados e a posição da scroll/página devem estar **exatamente como o utilizador os deixou**.
4.  **Diferenciação Visual:** Itens críticos na lista (ex: jogadores suspensos) devem ser distinguidos com **ícones gráficos** em vez de apenas texto ou cor, auxiliando utilizadores daltônicos e acelerando o reconhecimento.

---

## 10. Fluxo: jogadores (CRUD, detalhe e merge)

_Prompt: [secção 10](notebooklm-prompts-uiux.md#10-fluxo-jogadores-crud-detalhe-e-merge) de notebooklm-prompts-uiux.md_

Redesenhar a gestão de jogadores dos Galáticos exige uma transição do "modelo de implementação" (o que o banco de dados precisa) para o **"modelo representado"** (como o admin pensa sobre o elenco), focando numa **postura soberana** de alta produtividade.

### 1. Simplificar ou manter o Wizard de Merge?

Embora wizards possam parecer interrogatórios, em ações de alto risco e irreversíveis como o merge, eles são justificáveis como uma **"função de forçamento"**. No entanto, para reduzir a **excrescência de navegação** (excise), a recomendação é uma **Interface de Comparação Soberana** em vez de um wizard modal clássico.

*   **Alternativa Recomendada:** Um layout de **painéis adjacentes** (Split View). Em vez de 3 ecrãs que "atropelam" o admin, utilize uma visão única dividida onde a referência (Master) fica à esquerda e os candidatos à direita.
*   **Porquê:** O cérebro humano processa melhor mudanças quando elas estão **adjacentes no espaço** do que empilhadas no tempo.

### 2. Gatilhos para o Merge (Contexto de Oferta)

O merge não deve ser apenas uma rota isolada, mas uma ferramenta de **etiqueta digital prestativa**.

*   **Na Lista:** Bulk action (selecionar múltiplos jogadores e clicar em "Merge").
*   **No Detalhe:** Através de **RVMF (Feedback de Modelo Rico)**. Se o sistema detetar um nome semelhante (fuzzy match), exibe um banner discreto: *"Encontrámos 2 possíveis duplicados para este jogador. [Verificar]"*.
*   **Pós-criação:** Após salvar um novo jogador, se houver conflito de nome/ID, o sistema não deve bloquear o erro, mas sugerir o merge imediatamente: *"Este jogador já parece existir. Deseja unificar os registos?"*.

### 3. Detalhe do Jogador: Hierarquia de Informação

Para evitar o excesso visual, utilize **camadas de informação** e **divulgação progressiva**.

1.  **Nível 1 (Visceral):** Cabeçalho com Foto, Nome de Guerra, Posição e um "Sparkline" de performance nas últimas 5 partidas. O admin deve reconhecer o jogador instantaneamente.
2.  **Nível 2 (Comportamental/Intermédio):** Tabela de estatísticas da época atual (Golos, Minutos, Cartões). Use números alinhados à direita e fontes monoespaçadas para comparação rápida.
3.  **Nível 3 (Especialista/Histórico):** Histórico de lesões e transferências escondidos sob **tabs ou painéis expansíveis**, pois são consultados com menos frequência.

### 4. Formulário Create/Edit: Campos Obrigatórios vs. Avançados

O software deve ser um guia, não um polícia.

*   **Conjunto Mínimo de Trabalho (Minimal Working Set):** Apenas Nome, Número e Posição devem ser obrigatórios para a criação rápida.
*   **Campos Avançados:** Use **Progressive Disclosure** (ex: botão "Adicionar mais detalhes" que revela dados de contrato e biografia).
*   **Controles Delimitados:** Para a "Posição", use um seletor (dropdown/combo-box) em vez de texto livre para tornar o **erro impossível**.

---

### Jornada de Merge Passo a Passo (Foco na Carga Cognitiva)

1.  **Seleção:** Admin seleciona "João Silva" e "J. Silva" na lista.
2.  **Configuração:** O sistema abre a visão soberana. O admin clica numa estrela para definir qual é a "Referência" (Master).
3.  **Resolução de Conflitos (Passo 3 Otimizado):**
    *   **Audit, don't edit:** O sistema preenche automaticamente os campos onde não há conflito (ex: ambos têm a mesma data de nascimento).
    *   **Escolha Visual:** Onde houver conflito (ex: um tem foto, o outro não), os dois valores aparecem lado a lado. O admin clica diretamente no valor que deseja manter. A borda do valor selecionado torna-se Maroon (cor da marca), eliminando checkboxes e reduzindo o **trabalho visual**.
    *   **Preview:** Um terceiro painel no rodapé mostra como o "Novo João Silva" ficará após o merge.
4.  **Finalização:** Botão "Confirmar Merge".

### Critérios de Aceite Mensuráveis

*   **Eficiência de Escolha:** No passo 3, o admin deve resolver 5 conflitos de campos em menos de **10 segundos** usando manipulação direta (clicar no valor).
*   **Imunidade a Erros:** O sistema deve impedir o merge de jogadores com IDs de federação diferentes (regra de negócio rígida) através de um **alerta amber inline**, não um modal bloqueante.
*   **Reversibilidade:** Deve existir um botão "Desfazer" (Undo) num toast persistente por 10 segundos após a conclusão do merge, movendo o registo apagado para um buffer temporário em vez de o destruir imediatamente.
*   **Consistência de Estado:** O estado de seleção deve ser **inequívoco** (uso de cor + ícone de check) para garantir acessibilidade a utilizadores daltónicos.

---

## 11. Fluxo: partidas (lista, detalhe e formulário)

_Prompt: [secção 11](notebooklm-prompts-uiux.md#11-fluxo-partidas-lista-detalhe-e-formulário) de notebooklm-prompts-uiux.md_

Para redesenhar o fluxo de partidas do sistema **Galáticos**, utilizaremos os conceitos de **Postura Soberana** para o preenchimento denso de dados e **Postura Satélite** para a consulta rápida em campo, eliminando a "excrescência" (excise) de navegação e as interrupções desnecessárias.

### 1. Estrutura: Wizard vs. Página Única
Para um administrador que realiza esta tarefa repetidamente, a recomendação é uma **página única com agrupamento funcional**, em vez de um wizard.
*   **Por que não Wizard?** Wizards são "interrogatórios" que tratam o utilizador como um principiante. O admin do clube é um **intermédio perpétuo** que prefere ver o contexto total para ganhar "fluxo" (flow).
*   **Em Mobile:** Utilize uma única página com **Progressive Disclosure** (acordeões para as estatísticas de cada jogador) para minimizar a troca de ecrãs e a latência percebida.

### 2. Ordem de Campos e Registo Rápido
O formulário deve refletir o **modelo mental** do jogo, não a estrutura da base de dados.
1.  **Contexto Automático (Memória):** O sistema deve lembrar o último campeonato selecionado e preencher a data atual por defeito.
2.  **Agrupamento Sensato:** 
    *   **Cabeçalho:** Placar e Adversário (Dados Viscerais).
    *   **Corpo:** Tabela de Estatísticas (Dados de Trabalho).
3.  **Controles Delimitados:** Substitua inputs de texto por **Steppers (+ / -)** para golos e cartões em mobile, tornando o erro impossível.
4.  **Registo Pós-Jogo:** Implemente o padrão de **"Audit, don't edit"**. Permita salvar a partida mesmo com dados parciais, sinalizando via **RVMF** (ex: badge amarelo no campo de "Minutos") o que falta completar mais tarde.

### 3. Tabela Editável: Eficiência e Teclado
A tabela de estatísticas é o coração da tarefa soberana.
*   **Navegação:** Deve suportar as **setas do teclado** e **Tab** para saltar entre células de estatísticas de diferentes jogadores.
*   **Defaults Inteligentes:** Inicie todos os campos numéricos com "0" em vez de nulos. Para os minutos, se o jogador for titular, o default deve ser "90", reduzindo o trabalho físico do utilizador.
*   **Linhas Vazias e Pickers:** Elimine pickers que carregam o catálogo inteiro. Use **auto-complete/type-ahead** limitado aos jogadores inscritos naquele campeonato.

### 4. Feedback e Performance: Loading Parcial (Skeletons)
Substitua o spinner global por **Skeleton Screens** divididos por secções para manter o utilizador orientado durante o carregamento.
*   **Skeleton do Cabeçalho:** Mostra a estrutura do placar imediatamente.
*   **Skeleton da Tabela:** Mantém a hierarquia visual das colunas enquanto os dados dos jogadores (fotos e nomes) são processados assincronamente.
*   **Eliminação de Confirm:** Remova o `window.confirm` para o delete. Implemente a função **Undo** (Desfazer) num toast persistente, permitindo que o utilizador explore sem medo.

---

### Jornada: "Registar Partida em 5 Minutos" (Mobile)
1.  **Entrada (30s):** Clica em "Nova Partida" no Dashboard. O sistema já selecionou o campeonato ativo e a data de hoje (Memória).
2.  **Placar (30s):** Usa o Stepper para marcar ` x`. O resultado calculado aparece modelessmente no cabeçalho.
3.  **Súmula (3min):** Abre a seção de jogadores. Clica nos Steppers de "Golos" e "Assistências" para os jogadores que pontuaram. Os minutos já estão em "90" para todos.
4.  **Finalização (1min):** Clica em "Salvar". Um toast aparece: *"Partida guardada. [Desfazer]"*. O sistema valida em background se há conflitos de regra de negócio.

### Critérios de Aceite (Mobile)
*   **Alvos de Toque:** Steppers e inputs devem ter no mínimo **44px de altura** para uso com o polegar em campo.
*   **Imunidade a Erros:** O utilizador nunca deve perder os dados inseridos se a ligação cair ou se trocar de app para consultar uma mensagem.
*   **Feedback Visual:** Células com estatísticas alteradas devem ter um **significador visual** (ex: cor de fundo levemente diferente) para indicar que foram modificadas no buffer local antes do save final.

---

## 12. Fluxo: campeonatos e temporadas

_Prompt: [secção 12](notebooklm-prompts-uiux.md#12-fluxo-campeonatos-e-temporadas) de notebooklm-prompts-uiux.md_

Para melhorar a experiência de gestão de campeonatos e temporadas no sistema **Galáticos**, as recomendações focam em transformar regras de negócio complexas em uma **interface soberana** que minimize o esforço cognitivo e previna erros de forma modelar.

### 1. Detalhe de Campeonato: Tabs vs. Scroll vs. Accordion

Para aplicações administrativas de uso intensivo (**postura soberana**), a recomendação é o uso de **Tabs (Separadores)** combinados com **Painéis Adjacentes**.

*   **Por que Tabs?** Campeonatos possuem visões distintas e independentes (ex: Lista de Temporadas vs. Configurações de Regras). O uso de tabs evita o "trauma de navegação" de páginas excessivamente longas que exigem rolagem constante e desorientam o utilizador.
*   **Lazy Load e Estabilidade:** Ao carregar seções pesadas via *lazy load*, utilize o **min-height reservado** (skeleton) para manter a estrutura da página estável e evitar saltos visuais, o que preserva o **fluxo (flow)** do administrador.
*   **Contexto Persistente:** Mantenha um **Header Persistente** com o nome do campeonato e o status global sempre visíveis para manter o contexto durante a navegação entre temporadas.

### 2. Inscrição: Em Massa vs. Individual

A inscrição de jogadores deve ser tratada como uma tarefa de manipulação de grupos de objetos.

*   **Padrão de Seleção em Massa:** Utilize uma **lista com checkboxes** ou um padrão de "Dual List" (Disponíveis vs. Inscritos). Controles delimitados são essenciais para garantir que o utilizador não tente digitar nomes manualmente, o que causaria erros de validação.
*   **RVMF para Limites:** Para a regra de `max-players`, implemente **Rich Visual Modeless Feedback (RVMF)**. Exiba um contador dinâmico (ex: "18/22 jogadores") que muda de cor conforme se aproxima do limite. 
*   **Prevenção de Erros:** Ao atingir o limite, o sistema deve **desativar as checkboxes** de seleção de novos jogadores, tornando o erro impossível em vez de emitir um alerta após a tentativa de salvar.

### 3. Finalização: Fluxo Guiado (Checklist)

A ação de finalizar uma temporada é uma "alavanca de assento ejetor" (irreversível e crítica) e exige proteção extra.

*   **Checklist de Sensibilidade:** Antes de permitir a confirmação final, apresente um **checklist automático** que valide as regras de negócio em tempo real. 
    *   *Exemplo:* "Vencedores definidos? [OK] | Todos os jogos registados? [OK] | Jogadores inscritos dentro do limite? [OK]".
*   **Forcing Function:** O botão "Finalizar" deve permanecer **desativado (disabled)** até que todos os requisitos da checklist sejam satisfeitos. Isso evita que o sistema precise dar uma "reprimenda" no utilizador através de diálogos de erro.
*   **Confirmação Iluminadora:** O diálogo de confirmação não deve perguntar apenas "Tem a certeza?", mas sim explicar as consequências: "Esta ação congelará as estatísticas e não poderá ser desfeita".

### 4. Estados Visuais: Badges e Copy

Utilize o **Plano de Superfície** para comunicar o status da entidade de forma inequívoca.

*   **Ativo (Inscrições/Jogos abertos):** Badge em **Brand-Maroon**. Indica que a entidade é o foco principal de trabalho atual. Copy: "Em curso" ou "Inscrições Abertas".
*   **Concluído (Temporada finalizada):** Badge em tom neutro ou **verde (Emerald)**. Indica um estado de leitura e histórico. Copy: "Finalizado".
*   **Indefinido/Draft (Novo campeonato sem temporadas):** Badge **Âmbar** ou cinza pontilhado. Sinaliza que a configuração está incompleta. Copy: "Pendente" ou "Rascunho".

---

### Wireframe: Detalhe de Campeonato (Desktop Admin)

```text
[Header: Campeonato Nacional 2026 | Status: ATIVO (Maroon Badge) | Ações: Editar, Apagar*]
(*Apagar desativado se existirem partidas)

---------------------------------------------------------------------------------
[ Tab: TEMPORADAS (Active) ] [ Tab: ELENCO INSCRITO ] [ Tab: REGRAS / CONFIG ]
---------------------------------------------------------------------------------

[ Seção: Temporada Atual - Verão 2026 ]
(Status: A Decorrer | 42 Partidas Registadas)

| Rodada | Data       | Adversário    | Placar | Stats |
|--------|------------|---------------|--------|-------|
| 01     | 12/06/2026 | F.C. Porto    | 2 x 1  | [Ver] |
| 02     | 19/06/2026 | S.L. Benfica  | - x -  | [Reg] | (Foco: Ação imediata)

---------------------------------------------------------------------------------
[ Footer: Total de Inscritos: 22/25 (RVMF Progress Bar: [████████░░]) ] [Botão: Finalizar Temporada]
```

### Critérios de Aceite: Inscrição até ao Limite

1.  **Imunidade a Erros:** O sistema impede visualmente a seleção do 26º jogador se o `max-players` for 25.
2.  **Feedback Modeless:** O contador de inscritos atualiza instantaneamente ao clicar em uma checkbox, sem recarregar a página.
3.  **Audit Trail:** Se um jogador for removido da inscrição, o sistema permite o **Undo (Desfazer)** imediato via toast para evitar perda acidental de dados.
4.  **Visibilidade de Regras:** A regra de limite de jogadores deve estar visível no topo da área de seleção para que o utilizador não precise de a memorizar (conhecimento no mundo vs. na cabeça).

---

## 13. Fluxo: times

_Prompt: [secção 13](notebooklm-prompts-uiux.md#13-fluxo-times) de notebooklm-prompts-uiux.md_

Para alinhar a experiência do módulo de **Times** com os de **Jogadores** e **Partidas** no sistema Galáticos, as recomendações focam em manter a **postura soberana** (produtividade administrativa) e a **consistência interna** necessária para reduzir a carga cognitiva do administrador.

### 1. Informação Mínima no Card/Lista de Times
O card de time deve atuar como um **significador** claro, permitindo reconhecimento imediato sem exigir leitura exaustiva.
*   **Identidade Visual:** Nome do time e escudo/logo em destaque (Nível Visceral).
*   **Contagem de Ativos (RVMF):** Número de jogadores atualmente vinculados, usando fontes monoespaçadas para facilitar a comparação visual entre cards.
*   **Status de Próximo Evento:** Uma etiqueta discreta indicando a data da próxima partida agendada, servindo como uma "dica" de fluxo.
*   **Ações Rápidas:** Botões para "Ver Elenco" e "Editar", posicionados consistentemente com o padrão do módulo de jogadores.

### 2. Relação Time ↔ Jogadores na UI
A relação deve seguir o padrão **Hub-and-Spoke**, onde o Time é o centro que conecta os atletas e seus desempenhos.
*   **Navegação Lateral (Organizer-Workspace):** No detalhe do time, utilize um painel à esquerda para informações básicas e a área central para a lista de jogadores. Isso permite que o admin gerencie o elenco sem perder o contexto do time.
*   **Links Contextuais:** O nome do time em qualquer lugar da aplicação (partidas, fichas de atletas) deve ser um link que leva ao seu "Hub" de detalhes.
*   **Breadcrumbs Dinâmicos:** Ao navegar do Time para um Jogador específico, o breadcrumb deve refletir o caminho: `Times » [Nome do Time] » [Nome do Jogador]`, permitindo o retorno ao elenco com um clique.

### 3. Formulário de Time vs. Outros Módulos
Diferente do formulário de partida (altamente complexo), o de time deve ser desenhado sob o princípio de **"Less is More"**, focando no **Minimal Working Set**.
*   **Campos Obrigatórios:** Nome, Sigla e Categoria (ex: Sub-20). Use **controles delimitados** (dropdowns) para a categoria para evitar erros de digitação e inconsistências na base.
*   **Divulgação Progressiva:** Campos secundários (cores da marca, data de fundação, estádio sede) devem estar sob uma seção "Informações Avançadas" para não poluir o fluxo de criação rápida.
*   **Validação de Unicidade:** Implemente a **validação passiva** (onBlur) para verificar se o nome do time já existe antes da submissão final, notificando via mensagem inline amigável.

### 4. Empty State: Onde não há times
O sistema deve evitar o **"quadro branco"** (blank slate), agindo como um guia prestativo para o novo administrador.
*   **Copy Educativa:** Em vez de "Nenhum time encontrado", use: *"Sua organização ainda não tem times cadastrados. O cadastro de um time é o primeiro passo para organizar atletas e registrar partidas."*.
*   **Ação de Forçamento (Forcing Function):** Apresente um botão de destaque "Criar Primeiro Time" ou a opção de "Importar de Temporada Anterior" para acelerar o setup.

---

### Critérios de Aceite e Consistência
1.  **Imunidade de Dados:** Se houver um erro de validação da API ao salvar um time, o formulário **nunca deve limpar os campos já preenchidos**.
2.  **Acessibilidade de Teclado:** O admin deve ser capaz de navegar entre a busca de times e os resultados usando apenas as teclas **Tab e setas**.
3.  **Memória de Filtro:** Ao aplicar um filtro na lista de times (ex: "Times da Série B") e entrar em um detalhe, ao clicar em "Voltar", o filtro deve permanecer aplicado.
4.  **Coerência Visual:** O uso da cor **Brand-Maroon** e tipografia sans-serif deve ser idêntico em todos os botões de ação primária dos módulos de Times e Jogadores.

---

## 14. Dashboard e estatísticas (gráficos)

_Prompt: [secção 14](notebooklm-prompts-uiux.md#14-dashboard-e-estatísticas-gráficos) de notebooklm-prompts-uiux.md_

Para melhorar o **Dashboard** e a página de **Estatísticas** dos Galáticos, as recomendações baseiam-se na transição de um modelo de implementação técnica para um **modelo representado** focado na eficiência do administrador e nos princípios de **Informação Visual** de Edward Tufte e Alan Cooper.

### 1. Hierarquia do Dashboard (O que ver primeiro)

A organização deve seguir o princípio das **"camadas de informação"**, fornecendo visões amplas antes do detalhe.

*   **Nível 1 (Status Visceral):** No topo, **Cards de Métricas Críticas** (ex: Pontos, Posição no Campeonato, Saldo de Golos). Devem usar **Significadores** claros (ex: setas verdes/vermelhas) para indicar tendência.
*   **Nível 2 (Desempenho Coletivo):** Um gráfico de linha ou barras mostrando a evolução da equipa nas últimas 5 partidas. Isso responde à pergunta fundamental: "Como estamos comparados ao último mês?".
*   **Nível 3 (Alertas Operacionais - RVMF):** Blocos de **Feedback de Modelo Rico (RVMF)** para situações de exceção. Exemplo: "3 Jogadores Suspensos para a próxima partida" ou "Inscrições terminam em 2 dias".
*   **Nível 4 (Navegação de Atalho):** Links rápidos para as entidades mais usadas (Próximo Jogo, Lista de Plantel).

### 2. Gráficos: Tipos, Legendas e Acessibilidade

O objetivo do design visual é promover a compreensão rápida sem esforço cognitivo desnecessário.

*   **Tipos Adequados:** Use **gráficos de barras** para comparar jogadores (ex: contribuição de golos) e **gráficos de linha** para tendências temporais (ex: minutos jogados). Evite o "desquantificar" dados; sempre mostre o número exato ao lado da barra.
*   **Legendas Integradas:** Coloque as legendas e rótulos diretamente no gráfico para evitar a **excrescência de navegação** visual (olhar do gráfico para a legenda e voltar).
*   **Cores Acessíveis:** Utilize uma paleta com **contraste de pelo menos 80%**. Não dependa apenas da cor para distinguir métricas; use padrões, formas ou labels textuais para apoiar utilizadores daltónicos.

### 3. Filtros Globais vs. Por Secção

Filtros mal desenhados podem levar a resultados vazios e frustração.

*   **Filtros Globais (Contexto):** Temporada e Equipa devem ser filtros de topo persistentes no header para manter o **Wayfinding**.
*   **Filtros de Secção (Refinamento):** Na página de estatísticas, utilize **Filtros Facetados** para dados específicos (ex: filtrar apenas "Defesas" na tabela de passes certos).
*   **Persistência:** O sistema deve memorizar os últimos filtros aplicados para poupar trabalho ao admin em sessões recorrentes (Memória do Sistema).

### 4. Exportação e Partilha (CSV)

A exportação de dados é uma tarefa que pode introduzir latência, exigindo feedback modelar.

*   **Feedback Modeless:** Ao clicar em "Exportar CSV", em vez de um modal bloqueante, use um **indicador de progresso inline** ou uma notificação discreta no topo.
*   **Confirmação de Sucesso:** Quando o ficheiro estiver pronto, exiba uma mensagem prestativa (ex: "Relatório de Época exportado com sucesso") em vez de apenas o download silencioso do navegador.

---

### Esboço de Layout

#### Desktop (Postura Soberana)
```text
[ Sidebar Navegação ] [ Header: Dashboard 2026 | Season: 2025/26 (v) | Team: Principal (v) ]
                      -----------------------------------------------------------------
                      [ Metrics: Pts: 45 (▲) | Gols: 22 | Disciplina: [!!] ] (Cards)
                      -----------------------------------------------------------------
                      [ Main: Performance Chart (Recharts: Line)           ] [ Sidebar ]
                      [ (Trend das últimas 10 partidas)                    ] [ Alerts  ]
                      -----------------------------------------------------------------
                      [ Table: Top Contribuidores (Gols+Assists)           ] [ Links   ]
```

#### Mobile (Postura Transiente)
```text
[ Header: Dash | (≡) ]
----------------------
[ Metric Card: Pts   ]
[ Metric Card: Gols  ]
----------------------
[ Chart: Trend (v)   ] <- Carousel de gráficos
----------------------
[ List: Top Players  ] <- Card stack
----------------------
[ Button: New Match  ] <- Alvo de toque 44px+
```

### Critérios de Aceite para Eficiência
1.  **Compreensão Instantânea:** Um admin experiente deve identificar o jogador com maior desgaste físico (minutos/jogo) em menos de **30 segundos** sem mudar de página.
2.  **Imunidade a Erros:** Ao aplicar filtros cruzados nas estatísticas, o sistema nunca deve exibir uma página em branco (blank slate); deve sugerir "Limpar filtros".
3.  **Acessibilidade:** 100% dos gráficos devem ser legíveis em **Dark Mode** e passar no teste de contraste de 80%.
4.  **Feedback de Latência:** Qualquer agregação de dados no servidor que demore mais de **1 segundo** deve disparar um skeleton screen ou spinner localizado.

---

## 15. Componentes reutilizáveis (UI kit interno)

_Prompt: [secção 15](notebooklm-prompts-uiux.md#15-componentes-reutilizáveis-ui-kit-interno) de notebooklm-prompts-uiux.md_

Para estruturar um UI Kit interno para a SPA Galáticos, utilizaremos a hierarquia de vocabulário de interação de Alan Cooper (Primitivos $\rightarrow$ Compostos $\rightarrow$ Idiomas) e os princípios de design de Don Norman para garantir affordance e feedback modelar.

### 1. Lista Priorizada de Componentes

A extração deve seguir o modelo da pirâmide invertida, garantindo que a base seja sólida antes de construir padrões complexos.

*   **Nível 1: Primitivos (Alta Prioridade)**
    *   **Button:** Implementar estados de *hover*, *focus* e *loading* (spinner interno) para fornecer feedback visual modeless.
    *   **Input-Field:** Incluir suporte nativo para *hints* (dicas contextuais) e validação passiva no *onBlur*.
    *   **Badge:** Para sinalizar status (ativo, suspenso) de forma rápida (nível visceral).
    *   **Tooltip:** Essencial para "inflectir" a interface, escondendo detalhes para peritos enquanto ajuda intermediários.

*   **Nível 2: Compostos (Médio Prazo)**
    *   **Stat-Card:** Bloco para métricas do dashboard com suporte a tendências (sparklines).
    *   **Alert/Toast:** Padronizar o sistema de alertas para evitar interrupções modais desnecessárias.
    *   **Skeleton-Loader:** Para manter a estabilidade visual durante o carregamento de dados densos (tabelas).

*   **Nível 3: Idiomas (Específicos do Galáticos)**
    *   **Data-Table:** Componente soberano com suporte a navegação por teclado e ordenação.
    *   **Player-Card:** Abstração que combina foto, badge de posição e ações rápidas.

### 2. API de Props Consistente

A API deve ser previsível para reduzir o trabalho cognitivo do desenvolvedor (etiqueta digital interna).

*   **`variant`:** `:primary`, `:secondary`, `:danger` (ejector seat), `:ghost`.
*   **`disabled`:** Função de forçamento (*poka-yoke*) para evitar que o utilizador submeta dados inválidos.
*   **`class` (merge):** Permitir a injeção de classes Tailwind extras sem quebrar o estilo base (exclusividade vs. consistência).
*   **`loading?`:** Propriedade booleana que desativa o clique e mostra feedback visual imediato.

### 3. O que não componentizar (Evitar Over-abstraction)

*   **Layouts de Página Únicos:** Não tente criar um "componente de página" rígido. Buley e Cooper sugerem que a estrutura deve ser modular e adaptável, não uma camisa de força.
*   **Textos Estáticos Puros:** Evite componentizar labels que não possuem comportamento ou estilo semântico variável; o HTML/Hiccup puro é mais transparente.
*   **Lógica de Negócio Pesada:** O componente deve ser um "burro de carga" visual; a lógica de agregação de estatísticas deve residir no `app-state` e nos `effects`, não no UI Kit.

### 4. Documentação para ClojureScript

Conforme o princípio de que "documentação não resolve problemas, definição sim", o foco deve ser a clareza:

*   **Rationale (Porquê):** Documentar por que certas decisões de design foram tomadas (ex: por que o botão de delete exige 2 cliques ou tem undo).
*   **Exemplos Vivos:** Uma rota `#/ui-lab` no ClojureScript que renderiza todos os átomos do kit em seus diversos estados (dark/light).
*   **Assinaturas de Função:** Utilizar docstrings que expliquem as chaves esperadas no mapa de propriedades.

---

### Exemplo de Assinatura de Componente (Reagent)

```clojure
(defn button
  "Primitivo de ação. 
   Props: {:variant [:primary :danger :ghost] 
           :loading? boolean 
           :on-click fn 
           :class string}"
  [{:keys [variant loading? on-click class] :as props} label]
  [:button 
   {:on-click (when-not loading? on-click)
    :class ["rounded-md px-4 py-2 transition-all"
            (variant-classes variant)
            (when loading? "cursor-wait opacity-70")
            class]}
   (if loading? [spinner] label)])
```

**Critérios de Aceite:** Um novo ecrã de "Inscrição de Torneio" deve ser construído reutilizando obrigatoriamente os primitivos `button`, `input-field` e `badge`, garantindo que 100% das cores e espaçamentos venham do Design System, não de classes Tailwind arbitrárias.

---

## 16. Acessibilidade (WCAG) e teclado

_Prompt: [secção 16](notebooklm-prompts-uiux.md#16-acessibilidade-wcag-e-teclado) de notebooklm-prompts-uiux.md_

Para definir um plano de acessibilidade pragmático para o sistema Galáticos, as fontes recomendam uma abordagem de **Design Inclusivo**, onde melhorias para utilizadores com deficiências acabam por beneficiar todos os utilizadores (ex: admin a trabalhar sob luz solar intensa ou com pressa).

### 1. Prioridade WCAG 2.x (Nível Alvo: AA)

O objetivo realista e recomendado é o **Nível AA**. Este nível é frequentemente o requisito legal em várias regiões e garante que o sistema seja utilizável pela maioria das pessoas com deficiências visuais, motoras ou cognitivas.

*   **Contraste:** Manter um rácio de contraste de pelo menos **80%** (especialmente crítico para o *brand-maroon* em Dark Mode).
*   **Não depender apenas da cor:** Nunca utilize apenas a cor (ex: badge vermelho) para comunicar um estado de erro ou suspensão; acompanhe sempre com ícones ou texto descritivo.

### 2. Padrões de Interação: Teclado e Leitores de Ecrã

O sistema deve permitir a navegação e execução de tarefas sem o uso do rato.

*   **Tabelas e Listas:** Utilize a tecla **Tab** para entrar na tabela e as **teclas de setas** para navegar entre células de estatísticas. O leitor de ecrã deve anunciar o cabeçalho da coluna ao focar numa célula de dados.
*   **Formulários:** Cada campo deve ter um label textual claro e breve. Utilize **hints** (dicas) pop-up para validação em vez de alertas modais que interrompem o fluxo.
*   **Modais:** Ao abrir, o foco deve ser movido para o modal. Este deve obrigatoriamente ter botões de terminação claros (ex: "Confirmar" ou "Cancelar") e ser fechável com a tecla **Esc**.

### 3. Checklist Rápida por Página Crítica

*   **Dashboard:**
    *   [ ] As métricas principais têm descrições textuais (ex: ARIA labels para gráficos).
    *   [ ] A hierarquia visual é clara ao fazer o "squint test" (teste de semicerrar os olhos).
*   **Formulário de Partida:**
    *   [ ] Todos os inputs numéricos (placares, minutos) utilizam controlos delimitados (spinners) acessíveis por teclado.
    *   [ ] Erros de validação da API aparecem como texto próximo ao campo, não apenas como uma mudança de cor.
*   **Merge de Jogadores:**
    *   [ ] O progresso do wizard de 3 passos é anunciado por leitores de ecrã.
    *   [ ] Os elementos visuais de comparação (Antes vs. Depois) têm equivalentes textuais.

### 4. Estratégia de Testes Viáveis

1.  **Testes Automáticos (Baixo Custo):** Utilize ferramentas de auditoria de browser (como Axe ou Lighthouse) para verificar automaticamente rácios de contraste, tags ARIA em falta e IDs duplicados.
2.  **Testes Manuais (Essenciais):**
    *   **Navegação por Teclado:** Tente registar uma partida completa utilizando apenas o teclado (Tab, Setas, Enter).
    *   **Leitor de Ecrã:** Utilize o VoiceOver (iOS/Mac) ou TalkBack (Android) em rotas críticas para garantir que a navegação não fica presa em loops de foco.

---

### Tabela de Auditoria de Acessibilidade

| Página | Requisito | Estado (OK/Gap) | Recomendação das Fontes |
| :--- | :--- | :--- | :--- |
| **Login** | Contraste de Texto | Gap | Garantir 80% de contraste no botão *brand-maroon*. |
| **Global** | Alternância de Tema | OK | Manter o toggle de modo claro/escuro acessível. |
| **Partidas** | Navegação em Tabelas | Gap | Implementar navegação por setas entre células de stats. |
| **Jogadores** | Busca (Debounce) | OK | Garantir que o status "A carregar..." é anunciado. |
| **Modais** | Gestão de Foco | Gap | Forçar o foco para o botão primário ao abrir o modal. |
| **Geral** | Labels de Ícones | Gap | Todos os ícones da sidebar (Lucide) precisam de `aria-label`. |

**Nota de Implementação:** Para os modais customizados que falham no foco, a solução pragmática é garantir que o primeiro elemento interativo receba o foco automaticamente ao abrir, protegendo o utilizador da interrupção desorientadora de perder a posição no ecrã.

---

## 17. Mobile, responsivo e touch

_Prompt: [secção 17](notebooklm-prompts-uiux.md#17-mobile-responsivo-e-touch) de notebooklm-prompts-uiux.md_

Para otimizar a experiência móvel do sistema **Galáticos** para administradores em campo, devemos realizar a transição da **postura soberana** (desktop) para uma **postura standalone/transiente** (mobile), onde a eficiência de entrada e a legibilidade sob distração são críticas.

### 1. Breakpoints e Hierarquia de Colapso

A interface deve ser desenhada com uma grade modular que se adapta a pontos de interrupção (*breakpoints*) específicos para manter o fluxo do utilizador.

*   **Desktop (>1024px):** Sidebar fixa para acesso imediato a todas as funções (Postura Soberana).
*   **Tablet (768px - 1024px):** A sidebar deve colapsar para uma versão de ícones ou tornar-se um painel de índice adjacente em *landscape*.
*   **Telemóvel (<768px):** 
    1.  **Sidebar:** Deve ser movida para um **Bottom Tab Bar** com as 4-5 ações mais frequentes (Dashboard, Jogadores, Partidas, Escanear/QR) para facilitar o uso com o polegar.
    2.  **Menu Secundário:** Itens menos usados (Times, Configurações) devem ser movidos para um controlo "**More...**" ou para o *drawer* (hamburger menu).

### 2. Alvos de Toque (Hit Areas)

Utilizadores em campo frequentemente operam o dispositivo com uma mão ou enquanto caminham, o que exige alvos de toque generosos.

*   **Tamanho Mínimo:** Todos os botões e elementos interativos devem ter no mínimo **44px a 48px (ou ~20mm)** de altura/largura.
*   **Espaçamento:** É crítico separar botões de "Salvar" de funções perigosas (como "Apagar") para evitar o erro da "alavanca de assento ejetor".
*   **Significadores:** Use ícones com rótulos textuais para reduzir a carga cognitiva, já que a Postura Standalone exige que a interface seja autoexplicativa.

### 3. Formulário de Partida em Mobile (/matches)

O formulário pesado de desktop deve ser transformado em um **Stack Vertical** de elementos simplificados.

*   **Layout de Cards Accordion:** Em vez de uma tabela gigante, cada jogador deve ser um "Card" colapsável. Ao expandir, o admin vê os campos de estatísticas.
*   **Controlos Delimitados:** Substitua a digitação por **Steppers (+ / -)** para golos e cartões e **Barrel Controls** (seletores de tambor) para tempo de jogo, tornando o erro impossível.
*   **Inputs Inteligentes:** Use campos que aceitam formatos flexíveis e autocompletar (*type-ahead*) para reduzir a necessidade de usar o teclado virtual, que obstrui metade da tela.

### 4. Tabelas: Cards vs. Scroll (/players)

O notebook é enfático: **rolagem horizontal de texto é um erro de usabilidade grave** pois destrói a continuidade da leitura.

*   **Recomendação:** Converta a lista de jogadores em uma **Pilha de Cards (Vertical Stack)** ou **Swimlanes**.
*   **Informação Progressiva:** O card do jogador mostra apenas Nome, Foto e Status no nível primário. Detalhes adicionais (ID de federação, idade) são revelados por um toque (*Tap-to-reveal*).
*   **Ações Rápidas:** Implemente o gesto de **Swipe Left/Right** no card do jogador para ações comuns como "Marcar Lesionado" ou "Ver Estatísticas", similar ao padrão do iOS Mail.

---

### Critérios de Aceite para Uso com Uma Mão

1.  **Zona de Toque Confortável:** 100% das ações de "Salvar" e "Navegação Principal" devem estar localizadas na metade inferior do ecrã (alcance do polegar).
2.  **Imunidade a Interrupções:** O sistema deve salvar o estado do formulário de partida localmente em cada alteração (Auto-save), prevenindo perda de dados se o admin receber uma chamada ou precisar trocar de app em campo.
3.  **Feedback Visual Imediato:** Cada toque deve disparar uma resposta visual (mudança de cor ou animação de micro-interação) em menos de **0,1s** para confirmar a intenção do utilizador.
4.  **Legibilidade:** O contraste do texto crítico (placares, nomes) deve ser de pelo menos **80%** para ser legível sob luz solar direta.

---

## 18. Performance percebida e microinterações

_Prompt: [secção 18](notebooklm-prompts-uiux.md#18-performance-percebida-e-microinterações) de notebooklm-prompts-uiux.md_

Para melhorar a performance percebida no sistema **Galáticos** sem alterar a stack tecnológica, as fontes recomendam a transição de um modelo de "bloqueio técnico" (spinners globais) para um modelo de **Feedback de Modelo Rico (RVMF)** e **Interface Soberana** que preserva o **Fluxo (Flow)** do administrador.

### 1. Skeleton Patterns por Conteúdo
O uso de **Skeleton Screens** atua como um "sinalizador" (signpost), mantendo a estabilidade visual e reduzindo o esforço cognitivo de reorientação.

*   **Cards (Dashboard/Equipas):** Devem refletir o **Modelo Representado** final. Use um bloco para a imagem (escudo/foto) e duas linhas cinzentas pulsantes para o título e estatística principal.
*   **Tabelas (Plantel/Partidas):** Substitua a tabela inteira por um esqueleto de linhas fixas (ex: 5 linhas). Isso evita que o rodapé da página "salte" quando os dados chegam, preservando a posição ocular do utilizador.
*   **Formulários (Súmula de Jogo):** Implemente o **carregamento por secções**. O esqueleto do cabeçalho (dados gerais) deve aparecer instantaneamente, enquanto a tabela pesada de estatísticas de jogadores utiliza um esqueleto próprio em segundo plano.

### 2. Microinterações: Úteis vs. Distração
Toda a animação deve ter um propósito: focar a atenção, mostrar relações ou manter o contexto.

*   **Hover (Dicas de Pliancy):** Utilize mudanças sutis de cor ou sombras leves (`rounded-lg shadow-sm`) em cards de partidas. Isso confirma que o objeto é manipulável sem interromper a tarefa.
*   **Transições Purificadas:** Use apenas para mudanças de estado (ex: expandir estatísticas de um jogador na lista). Evite transições de página "cinematográficas" que duram > 1s, pois elas impedem o **utilizador experto** de agir rapidamente.
*   **Feedback de Clique:** O sistema deve responder em **< 0,1s**. Se a ação de salvar um golo for processada, o botão deve mudar visualmente (ex: brilho ou ícone de check) instantaneamente, mesmo que a API demore mais.

### 3. Optimistic UI no Domínio Galáticos
A interface deve ter a "coragem das suas convicções" e agir antes da confirmação do servidor em ações de baixo risco.

*   **Onde aplicar:** 
    *   **Marcação de Presença/Status:** Ao trocar um jogador para "Lesionado" ou "Ativo", a UI deve refletir a mudança no badge imediatamente.
    *   **Contadores de Placar:** Incrementar o golo na súmula visual antes da resposta da API.
*   **Gestão de Erro:** Caso a API falhe, utilize o padrão de **Desfazer (Undo)**. Em vez de um alerta de erro bloqueante, o sistema reverte o estado visual e exibe um toast: *"Não foi possível salvar. Tentar novamente?"*.

### 4. Mensagens de Progresso e Jobs Assíncronos
O software deve ser **educado e prestativo**, nunca parando os procedimentos com "idiocia".

*   **Feedback Modeless:** Substitua o "Redirecionando..." por uma **barra de progresso de um pixel** no topo da página (estilo GitHub/Chrome).
*   **Recálculo de Estatísticas:** Processos pesados (ex: recalcular média de golos da temporada) devem ocorrer em background (**Idle Cycles**). Informe o utilizador modelessmente: um ícone de "Sincronização" discreto no header indica que os dados estão a ser atualizados.
*   **Audit, don't edit:** Permita que o admin continue a preencher o formulário enquanto as validações de negócio rodam de forma assíncrona (onBlur), destacando erros apenas como **Hints** visuais.

---

### Critérios de Aceite por Rota

| Rota | Critério de Aceite (Performance Percebida) | Referência UX |
| :--- | :--- | :--- |
| **Login** | O formulário de entrada deve ser interativo em **< 1.5s** (LCP percebido via skeleton). | |
| **Dashboard** | Blocos de métricas aparecem sequencialmente conforme carregam, sem "travarem" a UI. | |
| **Partidas/New** | Entrada de dados na tabela de stats deve ser **instantânea (< 0,1s)** para o utilizador. | |
| **Plantel** | Filtros de busca exibem feedback "A filtrar..." modelessmente em **< 0,5s**. | |
| **Global** | Remoção total de `window.alert` e `window.confirm` em fluxos de performance crítica. | |

**Nota sobre a stack:** Estas melhorias focam-se na coordenação da **Interface Soberana** e no uso inteligente de ciclos ociosos do processador, sem exigir refatoração do ClojureScript ou da API.

---

## 19. Microcopy, i18n e tom de voz

_Prompt: [secção 19](notebooklm-prompts-uiux.md#19-microcopy-i18n-e-tom-de-voz) de notebooklm-prompts-uiux.md_

Para definir o tom de voz e os padrões de microcopy do sistema **Galáticos**, utilizaremos os princípios de **Etiqueta Digital** de Alan Cooper, a **Vocabulário Controlado** da Arquitetura de Informação e a **Psicologia do Design** de Don Norman. O objetivo é tratar o administrador como um "intermédio experto" que precisa de clareza e eficiência, não de distrações técnicas.

### 1. Tom de Voz: Profissional, Prestativo e Deferente

O sistema deve agir como um "assistente atencioso" e não como uma "máquina burocrática".

*   **Deferente (O Admin no Comando):** O software deve submeter-se ao utilizador. Evite termos como "Submeter" ou "Enviar", pois invertem a relação de poder; prefira termos que descrevam a ação do admin, como "Guardar Partida" ou "Registar Golo".
*   **Profissional, mas Relatável:** Use uma linguagem simples que descreva resultados, não processos técnicos. Evite jargão de programador ("Null pointer") e foque no domínio desportivo ("Dados em falta").
*   **Positivo e Iluminador:** Em vez de proibir, oriente. Em vez de "Não pode apagar", use "Esta partida possui estatísticas vinculadas e deve ser mantida".

### 2. Glossário Consistente (Vocabulário Controlado)

A consistência interna é vital para reduzir a carga cognitiva. Recomenda-se unificar todos os termos para o domínio em português:

| Termo em Inglês (Técnico) | Termo Recomendado (UI) | Contexto de Uso |
| :--- | :--- | :--- |
| **Season** | **Temporada** | Ciclo anual de competições. |
| **Match** | **Partida** | Evento individual de jogo. |
| **Roster / Players** | **Plantel / Elenco** | Conjunto de jogadores do clube. |
| **Registration / Sign-up** | **Inscrição** | Ato de incluir jogador num campeonato. |
| **Finalize / Close** | **Encerrar / Concluir** | Terminar uma temporada ou partida. |
| **Opponent** | **Adversário** | O outro clube na partida. |

### 3. Templates de Microcopy por Estado

#### Erro (400/409) - O "Último Recurso"
O erro deve ser educado e propor uma solução.
*   **Template:** `[O que aconteceu de forma clara] + [Como resolver].`
*   **Exemplo (409 Conflict):** "Este jogador já está inscrito na Temporada 2026. [Ver Ficha do Jogador]."

#### Sucesso - "Não reporte a normalidade"
Evite interromper o fluxo para dizer que "tudo correu bem" com diálogos modais.
*   **Template:** Use Toasts discretos ou RVMF (mudança visual modeless).
*   **Exemplo:** "Golo registado." (Toast que desaparece) ou um simples check visual na célula.

#### Confirmação - "Função de Forçamento"
Remova o "Tem a certeza?" e use perguntas sobre o objeto.
*   **Template:** `[Ação + Objeto]? + [Botão de Ação Crítica].`
*   **Exemplo:** "Apagar partida contra o [Adversário]? Esta ação removerá 12 registos de stats." [Apagar] [Cancelar].

#### Empty State - "Evite quadros brancos"
Transforme o vazio em educação e ação.
*   **Template:** `[Contexto educativo] + [Chamada para ação].`
*   **Exemplo:** "Ainda não existem partidas nesta temporada. Comece por registar o calendário de jogos." [Botão: Nova Partida].

### 4. Unificação de Idioma: Rotas e Document Title

As fontes recomendam que os rótulos correspondam exatamente aos destinos.
*   **Recomendação:** Unifique o `document.title` e as rotas para português. URLs como `#/jogadores/novo` são mais previsíveis para o admin do que misturar idiomas. Isso evita a sensação de "idiocia do sistema" onde a interface fala uma língua e a barra de endereços outra.

---

### 10 Exemplos: Antes vs. Depois

| Contexto | Antes (Técnico/Negativo) | Depois (Humano/Prestativo) |
| :--- | :--- | :--- |
| **Login** | Erro ao fazer login: Credenciais Inválidas. | Utilizador ou senha não reconhecidos. Tente novamente. |
| **Salvar** | Documento gravado com sucesso! (Modal) | (Toast discreto) Partida guardada. [Desfazer] |
| **409 API** | Erro 409: Duplicate Entry (team_id). | Este clube já foi cadastrado nesta temporada. |
| **400 API** | Bad Request: score_home cannot be null. | Por favor, insira o placar da equipa da casa. |
| **Delete** | Tem certeza que deseja excluir? | Apagar este jogador removerá todo o seu histórico. |
| **Empty** | No data available in table. | O plantel está vazio. Adicione o seu primeiro jogador. |
| **Geral** | Submeter dados. | Guardar Alterações. |
| **Busca** | Searching... | A procurar jogadores... |
| **Season** | Season iniciada. | Temporada 2026 ativa. |
| **Finalizar** | Proibido finalizar: stats pendentes. | Complete os minutos dos 11 titulares para encerrar. |

### Critérios de Aceite de Clareza (Erros 400/409)

1.  **Identificação do Objeto:** O erro deve citar o nome do jogador ou partida em conflito, não apenas um ID.
2.  **Linguagem de Domínio:** 0% de termos como "JSON", "String" ou "Database" em mensagens para o admin.
3.  **Caminho de Recuperação:** Erros de conflito (409) devem fornecer um link direto para o recurso duplicado para que o admin possa decidir entre editar ou fazer merge.
4.  **Polidez:** A mensagem não deve culpar o utilizador (evitar "Você errou"); deve relatar a incapacidade do sistema de processar aquela forma específica.

---

## 20. Priorização e roadmap UX

_Prompt: [secção 20](notebooklm-prompts-uiux.md#20-priorização-e-roadmap-ux) de notebooklm-prompts-uiux.md_

Com base nos princípios de **Interação Soberana**, **Etiqueta Digital** e na hierarquia dos **Cinco Planos da Experiência do Utilizador**, este roadmap UX incremental para o sistema **Galáticos** foca na redução da "excrescência" (excise) e no aumento da imunidade de dados.

### 1. Matriz Impacto × Esforço

Esta matriz prioriza o módulo de **Partidas** (maior impacto de negócio) seguindo a diretriz de "não fazer o utilizador sentir-se estúpido".

| Item | Descrição UX | Impacto | Esforço | Dependência |
| :--- | :--- | :--- | :--- | :--- |
| **1. Undo em Deletes** | Trocar `window.confirm` por padrão de "Desfazer". | Alto | Baixo | - |
| **2. Bounded Controls** | Steppers e spinners para golos/minutos (evita erro). | Alto | Baixo | - |
| **3. Cabeçalho Fixo** | Manter contexto em formulários longos. | Médio | Baixo | - |
| **4. Números Tabulares** | Tipografia monoespaçada em tabelas de stats. | Médio | Baixo | - |
| **5. Skeletons (Match)** | Carregamento parcial por secções (Debt fix). | Alto | Médio | Item 3 |
| **6. Contextual Picker** | Picker de jogadores limitado ao elenco inscrito. | Alto | Médio | - |
| **7. Auto-save Local** | Salvar rascunho da súmula no `localStorage`. | Alto | Médio | Item 5 |
| **8. Checklist de Época** | Forcing function para finalizar campeonatos. | Médio | Médio | - |
| **9. Split View Merge** | Interface adjacente para unificar jogadores. | Médio | Alto | - |
| **10. Mobile Card Stack** | Converter tabelas largas em cards no telemóvel. | Médio | Alto | - |

---

### 2. Ondas de Trabalho (Sprints de 2 semanas)

#### Onda 1: Segurança e Etiqueta (Foco: Partidas)
*   **Objetivo:** Reduzir erros de entrada e interrupções modais.
*   **Tarefas:** 
    *   Implementar **Bounded Controls** (placares e minutos) para tornar o erro impossível.
    *   Substituir alertas de eliminação por **Toasts com Undo**.
    *   Fixar o cabeçalho do formulário de partida (**Wayfinding**).
*   **Critério de Aceite:** O admin completa o placar sem usar o teclado alfanumérico.

#### Onda 2: Performance Percebida e IA (Foco: Jogadores/Geral)
*   **Objetivo:** Eliminar o "trauma de navegação" e a latência percebida.
*   **Tarefas:** 
    *   Extrair **Skeleton Patterns** para a tabela de estatísticas.
    *   Implementar o **Picker de Jogadores** com busca técnica (debounce) mas resultados limitados ao contexto.
    *   Adicionar **Breadcrumbs** com links laterais para navegação rápida entre campeonatos.
*   **Critério de Aceite:** A transição entre lista e edição de partida não exibe um "ecrã branco" global.

#### Onda 3: Funções de Forçamento e Mobile (Foco: Campeonatos/Teams)
*   **Objetivo:** Garantir integridade de negócio e uso em campo.
*   **Tarefas:** 
    *   Criar o fluxo de **Finalização de Temporada** com checklist (forcing function).
    *   Desenvolver a visão de **Card Stack** para mobile na rota `/players`.
    *   Implementar **RVMF (Feedback Modeless)** em badges de inscrição de jogadores.
*   **Critério de Aceite:** Admin consegue validar o limite de inscritos sem abrir um modal de erro.

---

### 3. O que NÃO fazer agora (Anti-patterns)

*   **Redesign Estético (Lipstick on a pig):** Não mudar cores ou sombras antes de resolver a hierarquia de campos nas partidas.
*   **Wizards para Especialistas:** Evitar transformar o formulário de partida em 10 passos sequenciais; o admin é um **intermediário perpétuo** que precisa de visão total.
*   **Gráficos 3D ou WebGL:** O dashboard deve ser conservador e funcional (Postura Soberana); evitar ruído visual desnecessário.
*   **Redesign da Rota de Login:** É uma interface transiente; o esforço deve estar nas ferramentas de produção (Soberanas).

---

### 4. Métricas de Sucesso UX

Para medir a eficácia das mudanças incrementais, recomenda-se o uso de **métricas de proxy** baseadas no desempenho da tarefa:

1.  **Tempo de Conclusão de Tarefa (TCT):** Tempo médio para registar uma súmula completa (Meta: < 3 minutos).
2.  **Taxa de Recuperação de Erros:** Quantas vezes a API retorna 400 e os dados são mantidos para correção imediata (Meta: 100%).
3.  **Frequência de Uso de "Undo":** Monitorizar se o admin utiliza o desfazer em vez de re-navegar após um delete acidental.
4.  **Taxa de Cliques de Navegação:** Redução no número de cliques para chegar a um jogador a partir do dashboard (Meta: $\le$ 3 cliques).

**Dependência Crítica:** A implementação do **Auto-save Local** (Onda 1 ou 2) é fundamental para suportar a **Imunidade de Dados** em conexões instáveis de estádio.

---

---
## 21. Opcionais (Wireframe por rota)

Com base nos princípios de **Interação Soberana** (Desktop) e **Postura Standalone** (Mobile) descritos nas fontes, abaixo está a especificação dos wireframes textuais para a rota de criação de nova partida, cobrindo todos os estados da interface.

### 1. Wireframe Desktop (Postura Soberana)
Foco em densidade de dados e eficiência de preenchimento para o admin experto.

#### Estrutura Global
*   **Sidebar (Fixed):** Navegação principal (Dashboard, Jogadores, Partidas, Campeonatos).
*   **Header (Persistent):** Título da página "Registar Partida", perfil do utilizador e seletor de tema.
*   **Breadcrumbs:** `Campeonatos » [Nome do Campeonato] » Nova Partida`.

#### Estados da Interface
*   **Estado Loading:** Utilização de **Skeleton Screens** em vez de spinners globais para manter o fluxo.
    *   Espaços reservados para cards de resumo e uma grelha pulsante para a tabela de estatísticas.
*   **Estado Erro:** Mensagens **iluminadoras e prestativas**.
    *   Erro 400/409: Alerta persistente no topo do formulário: "Conflito de dados: Esta partida já foi registada para a data selecionada. [Ver Partida]".
    *   Validação Inline: Erros aparecem como **Hints** vermelhos abaixo de cada campo (ex: "Data inválida") .
*   **Estado Vazio (Blank Slate):** Se não houver jogadores inscritos no campeonato.
    *   Área central exibe: "Não é possível criar uma partida sem elenco inscrito. [Botão: Inscrever Jogadores]".
*   **Estado Sucesso:** **Feedback Modeless**.
    *   Toast discreto no canto inferior: "Partida guardada com sucesso. [Botão: Desfazer]".

#### Layout do Conteúdo (Sucesso/Edição)
```text
[ Seção 1: Dados da Partida (Header do Form) ]
| Data: [ Input Date ] | Adversário: [ Seletor ] | Placar: [ 0 ] x [ 0 ] (Bounded Spinners) |
-----------------------------------------------------------------------------------------
[ Seção 2: Tabela de Estatísticas (Main Workspace) ]
| Foto | Jogador       | Gols | Assist. | Amarelo | Vermelho | Minutos | Ações      |
|------|---------------|------|---------|---------|----------|---------|------------|
| [img]| 10-M. Craque  | [ 2 ]| [ 1 ]   | [ ]     | [ ]      | [ 90 ]  | [Detalhe]  |
| [img]| 07-J. Silva   | [ 0 ]| [ 0 ]   | [x]     | [ ]      | [ 75 ]  | [Detalhe]  |
-----------------------------------------------------------------------------------------
[ Footer: Total de Gols Calculados: 2 ]                    [ Botão: Guardar Partida ]
```

---

### 2. Wireframe Mobile (Postura Standalone)
Otimizado para uso com uma mão e entrada rápida de dados em campo.

#### Estrutura Global
*   **Navigation:** Hamburger menu (Drawer) no canto superior esquerdo ou Bottom Tab Bar para acesso rápido.
*   **Context:** Título compacto "Nova Partida - [Nome do Camp.]".

#### Estados da Interface
*   **Estado Loading:** Skeleton cards simples (um card para cada 5 jogadores previstos).
*   **Estado Erro:** Banners de erro simplificados no topo da tela com alvos de toque de 44px+ para correção.
*   **Estado Vazio:** Ilustração simples de um campo vazio: "O plantel está vazio. [Inscrever]".
*   **Estado Sucesso:** Notificação temporária no fundo da tela (Snack-bar) que não interrompe a visão do admin.

#### Layout do Conteúdo (Sucesso/Edição)
As tabelas largas são convertidas em uma **Pilha de Cards (Stack)** para evitar scroll horizontal.

```text
[ Header: Nova Partida | Paulistão ]
------------------------------------
[ Card: Info Geral ]
Data: [ 10/10 ] | Adv: [ SC Galos ]
Placar: [ - 2 + ] x [ - 1 + ] (Steppers)
------------------------------------
[ Seção: Estatísticas do Elenco ]
(Busca rápida de jogador...)
------------------------------------
[ Card Jogador: 10 - M. Craque ]
Gols: [ - 2 + ]  Assist: [ - 1 + ]
Cartões: [ Amarelo ] [ Vermelho ]
Minutos: [ 90 ] (Barrel Control)
------------------------------------
[ Card Jogador: 07 - J. Silva ]
Gols: [ - 0 + ]  Assist: [ - 0 + ]
...
------------------------------------
[ Botão Flutuante (FAB): Guardar ]
```

### Critérios de Aceite Mensuráveis
1.  **Imunidade de Dados:** Ao encontrar um erro de validação (ex: 400 Bad Request), o formulário mantém 100% dos dados introduzidos para correção imediata.
2.  **Acessibilidade:** Todos os botões e steppers possuem altura mínima de **44px** em mobile e suporte a navegação por **Tab** em desktop.
3.  **Performance Percebida:** O tempo de transição entre o clique em "Nova Partida" e a exibição dos esqueletos (Loading) é inferior a **100ms** .
4.  **Wayfinding:** O admin identifica o campeonato e adversário em menos de **2 segundos** através do header persistente.
---

## Decisões consolidadas

Preencher após várias secções (ou ao concluir 1–20). Uma linha por tema acordado para o Galáticos.

| Tema | Decisão Galáticos | Secção fonte |
|------|-------------------|--------------|
| | | |
| | | |
| | | |

---

## Como derivar planos

Use este guia para transformar respostas em planos de implementação (Cursor Plan, issue ou doc em `docs/backlog/`). **Não** é checklist de tarefas — é o processo.

1. **Extrair ações** — Por secção em [notebooklm-response-uiux.md](notebooklm-response-uiux.md), listar recomendações com verbo + superfície (rota Reitit, namespace `galaticos.components.*`, ficheiro CSS).
2. **Classificar** — Esforço: S (&lt;½ dia), M (1–2 dias), L (&gt;2 dias). Impacto: alto (dia de jogo / formulário crítico), médio, baixo (cosmético).
3. **Agrupar épicos** — Ex.: `Formulário de partida`, `Design system`, `Merge de jogadores`, `A11y`, `Navegação e IA`.
4. **Criar plano** — Título + objetivo + ficheiros tocados + critérios de aceite copiados da resposta NotebookLM; referenciar `notebooklm-response-uiux.md#N`.
5. **Cruzar débito existente** — Antes de duplicar trabalho, ver [action-backlog.md](../../../backlog/performance/action-backlog.md) (skeletons, pickers, nested tables). Performance ≠ UX, mas tarefas abertas podem fundir-se no mesmo plano.
6. **Validar no código** — Confirmar rotas em [page-inventory.md](../../../reference/performance/page-inventory.md) e mensagens/regras em [business-rules.md](../../../reference/domain/business-rules.md).

**Prompt de síntese (opcional, no NotebookLM):** _Liste contradições entre suas recomendações e priorize 10 mudanças para 2 semanas._ Colar resultado aqui ou em **Notas de sessão**.

**Exemplo de entrada de plano (título):** `feat(ui): skeleton no formulário de partida` — fonte: resposta §11 + action-backlog § Matches forms.
---

---
## 22. Opcionais( Critérios de aceite)

Com base nos princípios de **Interação Soberana**, **Feedback de Modelo Rico (RVMF)** e na redução de **excrescência (excise)** apresentados nas fontes, seguem os critérios de aceite para o fluxo de merge de jogadores em 3 passos.

### Passo 1: Definição da Referência (Master)
**Cenário: Seleção do registro principal para unificação**
*   **Given (Dado que)** o administrador iniciou o fluxo de merge a partir da lista de jogadores ou de um alerta de duplicado detectado pelo sistema.
*   **When (Quando)** o administrador visualiza os jogadores selecionados no Passo 1.
*   **Then (Então)** o sistema deve permitir que ele clique em um marcador visual (ex: estrela) para definir qual registro será a "Referência" (Master), destacando-o visualmente com a cor da marca (*brand-maroon*).

### Passo 2: Seleção de Candidatos (Fuzzy Matching)
**Cenário: Refinamento dos registros a serem absorvidos**
*   **Given** que o registro Master foi definido no passo anterior.
*   **When** o administrador avança para o Passo 2 e visualiza a lista de candidatos sugeridos por busca técnica (*fuzzy matching*).
*   **Then** o sistema deve exibir checkboxes pré-marcados para os registros de maior similaridade, permitindo a desmarcação manual.
*   **Then** um contador dinâmico (RVMF) no rodapé deve indicar o impacto total da ação: *"X partidas e Y gols serão consolidados no registro principal"*.

### Passo 3: Resolução de Conflitos (Interface Adjacente)
Este passo aplica o padrão de **Audit, don't edit** para reduzir a carga cognitiva, focando apenas no que é diferente.

**Cenário: Escolha de valores em campos divergentes**
*   **Given** que o administrador está no Passo 3 resolvendo campos com valores diferentes (ex: nomes de guerra distintos ou fotos diferentes).
*   **When** o sistema apresenta os valores em conflito lado a lado (Split View).
*   **Then** o administrador deve poder selecionar o valor desejado clicando diretamente sobre o elemento visual, eliminando a necessidade de checkboxes adicionais (manipulação direta).
*   **Then** todos os campos idênticos entre os registros devem ser omitidos ou exibidos como "já resolvidos" para diminuir o ruído visual.

**Cenário: Validação de regras de negócio (Imunidade de Dados)**
*   **Given** que dois jogadores selecionados possuem IDs de federação diferentes.
*   **When** o administrador tenta finalizar o merge.
*   **Then** o sistema deve exibir um alerta "Amber" modeless (não bloqueante) avisando sobre o risco de integridade, mantendo todos os dados já selecionados no formulário para correção.

### Finalização e Reversibilidade
**Cenário: Conclusão da tarefa e suporte a erro**
*   **Given** que o administrador clicou em "Confirmar Merge".
*   **When** o processo é concluído e o banco de dados é atualizado no servidor.
*   **Then** o sistema deve redirecionar para a ficha detalhada do jogador unificado e exibir um Toast de sucesso com a opção **Desfazer (Undo)** visível por 10 segundos, permitindo reverter a exclusão dos registros secundários sem perda de dados.

### Critérios de Aceite de Performance Percebida
1.  A transição entre o Passo 2 e o Passo 3 (geração da matriz de conflitos) não deve exibir um ecrã branco; deve utilizar um **Skeleton Screen** que preserve a estrutura do formulário enquanto os dados são calculados.
2.  Toda interação de seleção de valor no Passo 3 deve ter resposta visual instantânea (< 0,1s) na área de **Preview** do registro final.
---

---
## 23. Opcionais (Contradições)

Ao analisar as recomendações fornecidas para o sistema **Galáticos** com base nas fontes, emergem tensões naturais entre a necessidade de **densidade de dados** para gestão e a **simplicidade de uso** sob pressão. Abaixo, elenco as principais contradições e proponho resoluções focadas no perfil do administrador esportivo.

### 1. Densidade Soberana vs. "Menos é Mais"
*   **A Contradição:** Para o admin (Postura Soberana), recomendei ser **generoso com o espaço** e usar múltiplas barras de ferramentas para eficiência. Por outro lado, as fontes citam que o número ideal de itens numa barra é **zero** e que "menos interface é melhor".
*   **Resolução para o Galáticos:** Utilize a **Inflexão da Interface**. A tela de súmula deve ser densa (Soberana) durante o preenchimento pós-jogo no balneário, mas o Dashboard deve ser minimalista, agindo como um **Ponto de Entrada Focal** que esconde ferramentas avançadas até serem necessárias.

### 2. Fluxos Guiados (Wizards) vs. Contexto Total
*   **A Contradição:** Recomendei um **Wizard de 3 passos** para o merge de jogadores como uma "função de forçamento". Contudo, para a criação de partidas, desaconselhei o wizard em favor de uma **página única**, alegando que o admin é um "intermédio perpétuo" que não quer ter "rodinhas de treino" no sistema.
*   **Resolução para o Galáticos:** Reserve wizards apenas para **ações raras e destrutivas** (como o merge ou encerramento de época), onde a segurança supera a velocidade. Para tarefas rotineiras (partidas), use **Divulgação Progressiva** em uma página única, permitindo que o admin veja o contexto total sem ser interrompido por trocas de ecrã.

### 3. Interface "Mágica" vs. "Auditar, Não Editar"
*   **A Contradição:** Uma recomendação sugere que o software deve ser **"mágico"**, antecipando necessidades e agindo de forma autônoma. No entanto, o princípio de **auditoria** diz que o sistema deve aceitar o que o utilizador introduz (Imunidade de Dados) e apenas destacar suspeitas, sem intervir magicamente.
*   **Resolução para o Galáticos:** No campo (Mobile), a interface deve ser **mágica e proativa** (ex: sugerir o elenco titular com base no último jogo). No escritório (Desktop), deve ser um **auditor silencioso**, permitindo que o admin force dados "inválidos" (ex: um jogador de outra categoria) e alertando modelarmente sobre isso através de **Feedback de Modelo Rico (RVMF)**.

### 4. Alvos de Toque: 2px vs. 44px
*   **A Contradição:** Para aplicações soberanas (Desktop), as fontes permitem alvos de apenas **2 pixels** devido à estabilidade do mouse. Já para mobile/campo, o requisito é de no mínimo **44-48px** para uso com o polegar.
*   **Resolução para o Galáticos:** Implemente um **Design Responsivo de Comportamento**, não apenas de layout. Quando o sistema deteta um dispositivo touch, ele deve expandir automaticamente as *hit areas* das estatísticas (toggles de cartões e golos), mesmo que isso exija mais scroll, priorizando a **precisão sob distração** no campo em detrimento da densidade absoluta.

### Tabela de Resolução de Conflitos de UI

| Conflito | Princípio A | Princípio B | Resolução Esportiva |
| :--- | :--- | :--- | :--- |
| **Navegação** | Breadcrumbs profundos | Navbar de "zero itens" | **Hub-and-Spoke:** Dashboard minimalista para hubs complexos. |
| **Erro** | Bloquear erros na fonte | "Pedir perdão, não permissão" | Bloquear apenas o impossível; permitir o improvável com aviso. |
| **Feedback** | Transitório (Toasts) | Bloqueante para confirmação | **Undo:** Agir imediatamente (Toast) com opção de desfazer. |
| **Configuração** | Permitir customização | Rigidez para consistência | Configuração apenas para **Estatísticas Críticas** na visão desktop. |

**Conclusão alinhada ao Admin:** O sistema deve ter **Postura Standalone** em campo (simples, botões grandes, feedback imediato) e **Postura Soberana** no escritório (densidade, atalhos, teclado, múltiplas abas). A coerência é mantida pelo **Glossário Único** e pela **Identidade Visual Maroon** consistente entre plataformas.

---

## Notas de sessão

- **Data da sessão NotebookLM:**
- **Contradições ou dúvidas entre secções:**
- **Decisões que divergem do código actual (registar antes de implementar):**
- **Planos criados a partir deste ficheiro:** _(links para docs/backlog ou PRs)_

---

## Referências

- Prompts: [notebooklm-prompts-uiux.md](notebooklm-prompts-uiux.md)
- Rotas e componentes: [page-inventory.md](../../../reference/performance/page-inventory.md)
- Regras de negócio: [business-rules.md](../../../reference/domain/business-rules.md)
- Performance / UX técnico: [action-backlog.md](../../../backlog/performance/action-backlog.md)
