> **Archived** — historical NotebookLM material (may be Portuguese). For current docs see [docs/README.md](../../README.md) and [reference/](../../reference/).

# NotebookLM Responses – Programação Funcional (Galáticos)

Respostas do notebook **Clojure / ClojureScript**. Preencher após colar cada prompt de [notebooklm-prompts-fp.md](notebooklm-prompts-fp.md).

**Workflow:** uma resposta por secção → extrair tarefas para [fp-improvement-checklist.md](../../backlog/fp-improvement-checklist.md) → actualizar [fp-design-improvements.md](../../backlog/fp-design-improvements.md) → revisar [fp-improvement-checklist.md](../../backlog/fp-improvement-checklist.md).

### Instruções (acção manual)

1. Abrir o notebook **Clojure / ClojureScript** no NotebookLM
2. Copiar o prompt da secção N de [notebooklm-prompts-fp.md](notebooklm-prompts-fp.md)
3. Colar no chat; aguardar resposta
4. Colar a resposta na secção N abaixo
5. Repetir para secções 1–10
6. Preencher **Decisões consolidadas** e seguir [Como extrair tarefas](../../backlog/fp-improvement-checklist.md#como-extrair-tarefas-das-respostas)

---

## 1. Arquitectura idiomática (substituir Service/Repository/DI)

_Prompt: secção 1 de notebooklm-prompts-fp.md_

Para estruturar sua API de forma idiomática em Clojure, evitando padrões orientados a objetos e facilitando a testabilidade, você pode adotar uma arquitetura baseada em **abstrações de dados e protocolos**, inspirada no conceito de **Onion Architecture** ou **Stratified Design**.

Abaixo estão as recomendações baseadas nas fontes fornecidas:

### 1. Layout de Namespaces e Nomenclatura
Em vez de "Services" e "Repositories", a comunidade Clojure tende a organizar o código por propósito ou domínio. Uma estrutura comum para sistemas que crescem é a separação em fatias verticais ou camadas horizontais funcionais:

*   **`galaticos.domain.*`**: Contém apenas dados (records) e funções puras que representam as regras de negócio essenciais (cálculos de estatísticas, validações de estado), sem saber nada sobre o banco de dados.
*   **`galaticos.db.protocol`**: Define o **contrato** de persistência usando `defprotocol` (ex: `ChampionshipStore`).
*   **`galaticos.db.mongodb`**: Implementação concreta do protocolo usando Monger.
*   **`galaticos.logic.*` (ou `model`)**: Onde vive a orquestração (ex: `championships.clj`). Estas funções recebem uma instância que satisfaz o protocolo de banco de dados como argumento.
*   **`galaticos.web.handlers`**: Funções Ring puras que extraem dados do `request`, chamam a camada de lógica e retornam o `response`.

### 2. Passagem de Dependências sem `with-redefs`
A maneira mais recomendada para evitar o "acoplamento global" e facilitar testes é **passar a dependência explicitamente como argumento**. Em vez de `ns-resolve`, utilize o padrão de **Protocolos e Reify**:

**Definição do Protocolo:**
```clojure
(ns galaticos.db.protocol)
(defprotocol ChampionshipStore
  (exists? [this id])
  (delete-by-id [this id]))
```

**Uso na Lógica de Negócio:**
```clojure
(defn delete-championship! [db id] ;; 'db' é a dependência injetada
  (if (protocol/exists? db id)
    (do (protocol/delete-by-id db id) {:message "Deleted"})
    (errors/not-found! "Not found")))
```

**Nos Testes:**
Você pode usar `reify` para criar um "mock" leve sem afetar o estado global:
```clojure
(deftest delete-test
  (let [mock-db (reify ChampionshipStore
                  (exists? [_ _] true)
                  (delete-by-id [_ _] nil))]
    (is (= "Deleted" (:message (delete-championship! mock-db "123"))))))
```

### 3. Orquestração de Múltiplas Coleções
A orquestração (ex: criar campeonato + temporada) deve viver na camada de **Lógica/Componente**. Esta camada é responsável por coordenar as chamadas atômicas ao banco de dados para garantir a consistência do domínio.

Se o seu banco suportar (como versões recentes do MongoDB), você pode passar um objeto de "transação" ou "sessão" através das chamadas de protocolo, garantindo que a orquestração seja atômica.

### 4. Ordem de Refatoração Segura
Dado que você já possui testes de integração/contrato, eles servirão como sua rede de segurança durante o processo:

1.  **Extrair Cálculos Puros**: Mova as regras de agregação de estatísticas para funções puras que operam apenas em mapas/vetores, separando-as de IO.
2.  **Introduzir o Protocolo**: Crie o protocolo de persistência e faça o seu namespace `db/*` atual implementá-lo.
3.  **Injeção no Handler**: Altere o handler para que ele receba a implementação do DB (via um componente de sistema ou fechamento de escopo) e a passe para as funções de lógica.
4.  **Remover `ns-resolve`**: Substitua o padrão `repo-call` pela chamada direta dos métodos do protocolo.
5.  **Migrar Testes**: Substitua o uso de `with-redefs` por `reify` ou pela passagem de mocks de dados nos testes unitários.

Essa estrutura garante que seu sistema seja composto por **peças pequenas e testáveis**, onde o banco de dados é apenas um detalhe de implementação trocável.

---

## 2. Regras de negócio como funções puras

_Prompt: secção 2_

Para separar regras de negócio de efeitos colaterais de forma idiomática em Clojure, você deve adotar o padrão de um **núcleo puro (Cálculos)** envolvido por uma **camada imperativa (Ações)**. Em vez de validar e salvar no mesmo bloco, a lógica de negócio deve apenas *decidir* o que deve acontecer com base nos dados recebidos.

Abaixo estão as recomendações baseadas nas fontes:

### 1. Expressando Pré-condições como Funções Puras
Transforme suas validações em funções que recebem apenas **dados** (mapas/vetores) e retornam um **valor de erro** (mapa) ou o dado processado para o sucesso. Evite que essas funções conheçam o banco de dados.

```clojure
(ns sports-roster.domain.championships)

;; Uma "Cálculo" puro que apenas decide se a finalização é permitida
(defn finalization-decision 
  "Retorna {:ok data} para prosseguir ou {:error {:type :validation :message ...}}"
  [{:keys [finished-at enrolled-player-ids]} {:keys [award-count winner-ids]}]
  (let [not-enrolled (remove (set enrolled-player-ids) winner-ids)]
    (cond
      finished-at         {:error {:type :conflict :message "Already finalized"}}
      (nil? award-count)  {:error {:type :validation :message "Must be a number"}}
      (neg? award-count)  {:error {:type :validation :message "Must be non-negative"}}
      (and (pos? award-count) (empty? winner-ids))
      {:error {:type :validation :message "Winners required"}}
      (seq not-enrolled)  {:error {:type :validation :message "Winners must be enrolled"}}
      :else {:ok {:winner-ids winner-ids :finalized-at (java.util.Date.)}})))
```

### 2. Boundary vs. Domain: Onde usar `ex-info`
*   **Domínio (Núcleo Puro):** Retorne **valores de erro** (como mapas com `:error`). Isso mantém a transparência referencial e facilita o encadeamento de funções.
*   **Fronteira (Handlers/API):** Use `ex-info` na camada de "Ação" (imperativa) para interromper o fluxo quando uma decisão de erro for recebida do domínio. Isso permite que um middleware de exceção converta o mapa em um status HTTP.

```clojure
(defn finalize! [db id winners-data]
  ;; 1. Busca dados (Efeito)
  (let [championship (db/find-by-id db id)
        ;; 2. Chama lógica pura (Cálculo)
        decision (finalization-decision championship winners-data)]
    ;; 3. Age com base na decisão (Efeito)
    (if-let [error (:error decision)]
      (throw (ex-info (:message error) 
                      {:status (if (= (:type error) :conflict) 409 400)
                       :code (:type error)}))
      (db/save-finalization! db id (:ok decision)))))
```

### 3. Testando Regras sem MongoDB ou `with-redefs`
Como sua lógica de decisão é agora uma função pura que aceita mapas simples, você pode testar todas as variações de regras de negócio passando apenas **dados fictícios**. Não há necessidade de mocks complexos ou de redefinir funções globais com `with-redefs`.

```clojure
(deftest finalization-rules-test
  (testing "Rejeita se já estiver finalizado"
    (let [championship {:finished-at #inst "2023-01-01"}
          params {:award-count 1 :winner-ids ["p1"]}
          result (finalization-decision championship params)]
      (is (contains? result :error))
      (is (= "Already finalized" (get-in result [:error :message])))))

  (testing "Aprova se dados estiverem coerentes"
    (let [championship {:enrolled-player-ids ["p1" "p2"]}
          params {:award-count 1 :winner-ids ["p1"]}
          result (finalization-decision championship params)]
      (is (contains? result :ok)))))
```

### 4. Convenções de Nomenclatura
Para clareza visual e semântica, siga estas convenções comuns na comunidade Clojure:

*   **Funções de Decisão Pura (Cálculos):** Use nomes descritivos como substantivos ou frases verbais simples (ex: `finalization-error`, `eligible-for-enrollment?`). Use o ponto de interrogação `?` se o retorno for booleano.
*   **Funções de Comando/Efeito (Ações):** Use o sufixo `!` (bang) para funções que realizam I/O, alteram o banco de dados ou lançam exceções (ex: `finalize!`, `save-player!`, `delete-championship!`).

### Resumo da Estrutura Recomendada
1.  **`sports.logic.*`**: Namespaces com funções puras que recebem mapas e retornam `{:ok ...}` ou `{:error ...}`.
2.  **`sports.db.*`**: Protocolos de persistência.
3.  **`sports.api.handlers`**: Onde as "Ações" vivem; elas buscam dados do DB, passam para a `logic`, e decidem se lançam `ex-info` ou persistem o resultado.

---

## 3. Erros e fronteiras HTTP

_Prompt: secção 3_

Para implementar uma abordagem funcional idiomática para o tratamento de erros em uma API Ring, você deve focar na representação de **erros como dados** no núcleo da sua aplicação e reservar as exceções para falhas inesperadas ou para controle de fluxo na "fronteira" do sistema (middleware).

Abaixo estão as recomendações detalhadas baseadas nas fontes:

### 1. Handlers: Result/Either vs. Throwing
Em Clojure, a prática recomendada é que a lógica de domínio retorne **valores** (mapas) que indiquem explicitamente o sucesso ou a falha, em vez de lançar exceções. Isso mantém as funções referencialmente transparentes e fáceis de testar sem blocos `try/catch`.

*   **Lógica de Domínio:** Deve retornar mapas como `{:ok data}` ou `{:error {:type :not-found :message "..."}}`.
*   **Handlers:** Devem apenas orquestrar a chamada e retornar o mapa resultante. O Ring trata qualquer mapa retornado como uma resposta, desde que contenha `:status`.

### 2. Quando usar `ex-info` vs. Mapas Explícitos
*   **Use Mapas (`{:error ...}`):** Para erros de negócio **previsíveis e recuperáveis** (ex: validação, recurso não encontrado). Isso permite que você use funções de alta ordem para compor a lógica.
*   **Use `ex-info`:** Na camada de "fronteira" (Action/Handler) quando você deseja interromper o processamento imediatamente e deixar o **middleware** capturar a falha, ou para erros técnicos inesperados onde o contexto (via `ex-data`) é valioso para depuração.

### 3. Eliminando o `try/catch` por Handler
Para remover a repetição, você deve confiar no **Middleware** para capturar tanto as `ExceptionInfo` lançadas deliberadamente quanto as exceções inesperadas. Se você padronizar que sua camada de serviço retorna mapas de erro, o middleware também pode ser estendido para transformar esses mapas em respostas HTTP JSON automaticamente.

### 4. Railway-Oriented Programming (ROP) Simples
Você pode implementar o padrão de "ferrovia" usando macros de encadeamento simples ou pequenos utilitários, sem bibliotecas pesadas. A macro `some->` do Clojure já fornece um comportamento básico de "curto-circuito" em `nil`.

Para um ROP mais robusto com erros detalhados, use uma função `bind` (semelhante ao `flatMap` do Scala) ou uma macro personalizada.

### Exemplos Práticos

**Lógica de Domínio (Pura):**
```clojure
(defn validate-championship [champ]
  (if (seq (:matches champ))
    {:error {:status 409 :code :has-matches :message "Cannot delete..."}}
    {:ok champ}))
```

**Middleware de Erro Centralizado:**
Este middleware elimina a necessidade de `try/catch` nos handlers.
```clojure
(defn wrap-api-errors [handler]
  (fn [request]
    (try
      (let [response (handler request)]
        ;; Se o handler retornar um mapa com :error, trata como erro
        (if-let [err (:error response)]
          (resp/error (:message err) (:status err))
          response))
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [status message]} (ex-data e)]
          (resp/error (or message (.getMessage e)) (or status 500))))
      (catch Exception e
        (resp/server-error "Internal server error")))))
```

**Handler Limpo (Usando Composição):**
Utilizando uma macro simples de "ferrovia" (exemplo conceitual):
```clojure
(defn delete-championship [request]
  (let [id (get-in request [:params :id])]
    (-> (service/find-id id)      ;; Retorna {:ok ...} ou {:error ...}
        (result-bind service/validate-deletion)
        (result-bind service/execute-delete!))))

;; O middleware 'wrap-api-errors' cuidará de transformar o {:error ...} final em JSON.
```

Essa estrutura garante que seus handlers permaneçam focados na intenção do código ("o quê") em vez de na mecânica do tratamento de erros ("como"). O uso de **middleware** como uma fábrica de handlers permite que você empilhe essas preocupações de forma modular.

---

## 4. Validação na fronteira

_Prompt: secção 4_

Para validar e normalizar corpos de requisição HTTP em Clojure de forma idiomática, a tendência é mover-se de validações manuais imperativas para abordagens **orientadas a dados** ou **composições de funções puras**.

Com base nos materiais, aqui está a comparação e as recomendações para o seu caso de uso:

### 1. Comparação de Abordagens

#### **Prismatic Schema (Representando Malli/Spec nas fontes)**
Embora o Malli não esteja explicitamente detalhado, as fontes cobrem extensivamente o **Prismatic Schema**, que compartilha a mesma filosofia de "dados como esquema".
*   **Vantagens:** Define metadados como dados, automatiza descrições e permite **coerção** automática.
*   **Coerção:** É ideal para o seu caso (ex: converter strings de JSON para `Long` ou `Keywords`).
*   **Uso:** Você define o esquema uma vez e o utiliza tanto para validar quanto para transformar os dados na fronteira.

#### **Validateur (Validação Manual Composível)**
As fontes sugerem o uso da biblioteca **Validateur** como uma alternativa funcional às validações manuais.
*   **Vantagens:** Permite criar conjuntos de validação (`validation-set`) que são tratados como funções.
*   **Composição:** Você pode usar `compose-sets` para combinar pequenos validadores reutilizáveis (ex: um validador de e-mail e um de senha) em um validador complexo.

#### **Validação Manual com Mapas**
O padrão básico em Clojure é usar mapas de "mensagem e função".
*   **Implementação:** Uma função `validate` reduz sobre as regras e acumula mensagens de erro em um mapa onde as chaves são os campos inválidos.
*   **Recomendação:** Use esta abordagem apenas se o seu sistema for muito pequeno ou se você quiser evitar dependências externas, mas prefira esquemas de dados para APIs públicas.

### 2. Integração com HTTP 400
A melhor prática é utilizar um **middleware** para interceptar falhas de validação e retornar um status 400 padronizado, evitando `try/catch` repetitivos nos handlers.

```clojure
;; Exemplo de middleware para capturar erros de Schema ou manuais
(defn wrap-validation-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= (:type data) :schema.core/error) ;; Se usar Schema
            (-> (ring.util.response/response {:errors (:error data)})
                (ring.util.response/status 400))
            (throw e)))))))
```

Nos handlers, se a validação falhar, você retorna uma resposta de erro explicitamente:
```clojure
(defn create-championship [body]
  (if-let [errors (validate-body body)]
    (-> (ring.util.response/response errors)
        (ring.util.response/status 400))
    (logic/save! body)))
```

### 3. Pipelines de Normalização Pura
Em vez de normalização *inline*, utilize a **composição de funções** (`comp`) ou **macros de encadeamento** (`->>`) para criar um pipeline claro.

*   **Pipelines de Transformação:** Use `map-keys` e `map-vals` para transformar estruturas inteiras (ex: tornar todas as chaves em keywords).
*   **Transducers:** Se você processa grandes volumes de dados (como estatísticas de jogadores), os **transducers** permitem desacoplar a transformação da estrutura de dados, tornando o pipeline reutilizável e performante.

```clojure
;; Pipeline de normalização composível
(def normalize-championship
  (comp
    #(update % :max-players (fn [v] (if (string? v) (Long/parseLong v) v)))
    #(update % :enrolled-player-ids (partial mapv ->object-id))
    keywordize-keys)) ;; Utilitário comum em Clojure
```

### 4. Coerção de IDs na Fronteira vs. DB
As fontes recomendam fortemente realizar a coerção na **fronteira da API**.
*   **Por que na fronteira:** Garante que a lógica de negócio e as funções de domínio operem apenas com tipos de dados ricos e validados (como `ObjectIds` ou `Instants`), e não com strings brutas do HTTP.
*   **Schema Coercion:** Se usar Schema, utilize o `json-coercion-matcher`. Ele tentará converter tipos automaticamente antes da validação final.

### Resumo da Estrutura Recomendada
1.  **Definição:** Use **Schema** ou **Malli** para definir a "forma" dos dados como dados.
2.  **Fronteira:** O handler chama um **coercer** (que transforma strings em tipos ricos) e valida o resultado.
3.  **Fluxo:** Se inválido, retorne status 400 via middleware. Se válido, passe os dados normalizados para a camada de lógica.

---

## 5. Testabilidade sem mocks OO

_Prompt: secção 5_

Para testar sua lógica de negócio em Clojure sem depender de `with-redefs` ou bancos de dados reais, a estratégia idiomática é desacoplar as **Ações** (efeitos colaterais) dos **Cálculos** (regras puras). Em vez de mocks de estilo OO, utiliza-se a passagem explícita de dependências e a abstração por dados ou protocolos.

Aqui estão as recomendações detalhadas:

### 1. Mapas de Funções vs. Protocols vs. Records
A escolha depende da complexidade da interface de persistência:
*   **Mapas de Funções:** São ideais para orquestrações simples. Você passa um mapa como `{:exists? (fn [id] true)}` para a função de negócio. Isso é altamente flexível e fácil de compor.
*   **Protocols (`defprotocol`):** É a forma mais robusta de definir um "contrato" de comportamento. Eles permitem que diferentes implementações (MongoDB real vs. em-memória) sejam tratadas de forma polimórfica.
*   **Records (`defrecord`):** Devem ser usados para implementar protocolos quando você precisa de alta performance ou dados estruturados associados ao "repositório" (como configurações de conexão).

**Recomendação:** Use **Protocols** para definir a interface do banco de dados e **`reify`** em testes para criar implementações "dummy" rápidas e locais ao teste.

### 2. Divisão de Testes (Arquitetura Onion)
De acordo com a **Onion Architecture**, você deve estratificar seus testes:
*   **Domínio (Cálculos Puros):** Teste funções que recebem apenas dados simples (mapas/vetores) e retornam decisões. Estes testes não precisam de I/O e rodam em milissegundos.
*   **Integração (MongoDB):** Teste a implementação real do protocolo contra uma instância real de MongoDB (usando contêineres ou instâncias de teste) para garantir que as queries Monger estão corretas.
*   **Contrato (HTTP):** Use `ring-mock` para testar os handlers. Foque em verificar se o status HTTP e o envelope JSON estão corretos, passando uma implementação `reify` do banco de dados para o handler.

### 3. Anti-padrões a evitar
*   **`with-redefs` em produção:** Alterar raízes de vars globais não é thread-safe e torna os testes frágeis e difíceis de rodar em paralelo.
*   **Global DB Atom:** Depender de um átomo global para o banco de dados dificulta a limpeza do estado entre testes e impede o isolamento.
*   **`ns-resolve` para DI:** É um mecanismo de busca global que quebra a transparência referencial e torna a lógica dependente do carregamento de namespaces.

### 4. Fakes em Memória e Fixtures
Para MongoDB, como ele não possui uma versão "in-memory" oficial tão leve quanto o SQLite, as estratégias comuns são:
*   **Implementação em Átomo:** Crie uma versão do seu protocolo que armazena os dados em um `atom`. É excelente para testes de sistema rápidos.
*   **Fixtures de Transação/Limpeza:** Use `use-fixtures` para limpar as coleções antes de cada teste ou rodar o teste dentro de uma transação com rollback (se o driver e a versão do Mongo suportarem).

### Exemplo: Regra "Não excluir campeonato com partidas"

**Lógica de Domínio (Pura):**
```clojure
(ns galaticos.domain.championships)

(defn can-delete? 
  "Decisão pura baseada em dados."
  [championship matches-count]
  (cond
    (nil? championship) {:error :not-found}
    (pos? matches-count) {:error :conflict :message "Cannot delete championship with matches"}
    :else {:ok true}))
```

**Teste da Regra (Sem DB):**
```clojure
(deftest delete-rule-test
  (testing "Rejeita exclusão se houver partidas"
    (let [result (can-delete? {:id "123"} 5)]
      (is (= :conflict (:error result))))))
```

**Handler/Orquestração (Usando Protocolo):**
```clojure
(ns galaticos.logic.championships
  (:require [galaticos.domain.championships :as domain]))

(defprotocol ChampionshipStore
  (find-by-id [this id])
  (count-matches [this id])
  (delete! [this id]))

(defn delete-championship! [db id]
  (let [champ (find-by-id db id)
        m-count (count-matches db id)
        decision (domain/can-delete? champ m-count)]
    (if-let [err (:error decision)]
      (throw (ex-info (:message decision) {:status (if (= err :not-found) 404 409)}))
      (delete! db id))))
```

**Teste de Integração/Mock com `reify`:**
```clojure
(deftest service-delete-test
  (let [mock-db (reify ChampionshipStore
                  (find-by-id [_ _] {:id "123"})
                  (count-matches [_ _] 10) ;; Simula que existem partidas
                  (delete! [_ _] (throw (Exception. "Should not be called"))))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot delete"
          (delete-championship! mock-db "123")))))
```

Esta abordagem garante que seus testes sejam determinísticos, rápidos e não poluam o estado global da aplicação.

---

## 6. Camada de dados e fronteira de efeitos (Monger)

_Prompt: secção 6_

Para estruturar sua camada de acesso a dados em Clojure com Monger 3.x, separando transformações puras de efeitos colaterais, você deve seguir o princípio de **"Núcleo Funcional, Casca Imperativa"**. Isso envolve tratar o banco de dados como uma dependência explícita e isolar a construção de queries da sua execução.

Abaixo estão as recomendações detalhadas baseadas nas fontes:

### 1. DB como Argumento Explícito vs. Implícito
A prática recomendada em Clojure é passar a conexão (ou o objeto `db`) como o **primeiro argumento** das funções.

*   **Trade-offs do Implícito (db):** Embora pareça mais limpo inicialmente, o uso de um `(db)` global ou dinâmico cria um "acoplamento forte" e torna o código difícil de modularizar e testar. Isso introduz uma entrada implícita que quebra a transparência referencial.
*   **Vantagens do Explícito:** Segue o padrão de bibliotecas como `clojure.java.jdbc`. Facilita a injeção de dependências e permite que você use `partial` para criar versões da função com a conexão pré-preenchida, reduzindo a repetição sem perder a modularidade.

**Exemplo:**
```clojure
(ns galaticos.db.players
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]))

(defn find-by-id [db id] ;; 'db' é passado explicitamente
  (mc/find-one-as-map db "players" {:_id id}))

;; Uso com partial para evitar passar o db toda hora no mesmo contexto
(let [find-player (partial find-by-id current-db)]
  (find-player player-id))
```

### 2. Onde converter `ObjectId`
A conversão de strings para `ObjectId` deve ocorrer na **fronteira da aplicação** (nos Handlers ou na camada de validação/coerção).

*   **Por quê:** Funções de lógica de domínio e de banco de dados devem operar com tipos de dados ricos e validados. Ao converter na fronteira, você garante que as funções internas não precisem lidar com strings brutas do JSON ou realizar normalizações defensivas repetitivas.
*   **Localização:** Namespaces de `util` ou middleware de coerção são ideais para centralizar essa lógica de transformação.

### 3. Convenções de Nomenclatura (Bang `!`)
Em Clojure, é idiomático usar o sufixo `!` para funções que causam efeitos colaterais observáveis, como alterar o estado do banco de dados ou realizar I/O.

*   **Com `!` (Ações):** Funções que executam comandos de escrita (`mc/insert`, `mc/update`, `mc/remove`) ou que lançam exceções. Ex: `update-stats!`, `delete-player!`.
*   **Sem `!` (Cálculos/Queries):** Funções que apenas buscam dados (queries) ou transformam dados na memória. Ex: `calculate-aggregations`, `find-active-seasons`.

### 4. Testando Lógica de Agregação In-Memory
Para testar lógicas complexas de agregação sem depender do MongoDB, você deve separar a **descrição do pipeline** da sua **execução**.

**Estratégia Recomendada:**
1.  **Construção Pura:** Crie uma função que apenas gera o vetor do pipeline (um dado puro).
2.  **Processamento In-Memory:** Crie uma função que simula a transformação que o Mongo faria, aceitando uma coleção de mapas Clojure simples (simulando os dados do Mongo).

**Exemplo de Refatoração de Agregação:**
```clojure
;; 1. Função pura que define o pipeline (Testável como dado)
(defn stats-pipeline [player-id]
  [{"$match" {:player-id player-id}}
   {"$group" {:_id "$player-id" :total {"$sum" "$score"}}}])

;; 2. Função de lógica pura que processa o resultado (Testável in-memory)
(defn summarize-stats [rows]
  (map (fn [r] (assoc r :updated-at (java.util.Date.))) rows))

;; 3. Função de efeito colateral (Ação) que orquestra tudo
(defn update-player-cache! [db pid]
  (let [pipeline (stats-pipeline pid)
        raw-rows (mc/aggregate db "matches" pipeline) ;; IO
        clean-stats (summarize-stats raw-rows)]      ;; Lógica Pura
    (mc/update db "players" {:_id pid} {:$set {:aggregated-stats clean-stats}})))
```

Esta estrutura permite que você valide a lógica de `summarize-stats` passando apenas vetores de mapas fictícios em seus testes unitários, sem precisar de `with-redefs` ou instâncias reais do banco de dados.

---

## 7. Analytics: derivados como funções puras + materialização

_Prompt: secção 7_

Para modelar análises derivadas e em cache de forma funcional em Clojure, você deve seguir o princípio de separar **Cálculos** (funções puras) de **Ações** (efeitos colaterais como I/O de banco de dados). Em vez de misturar a lógica de agregação com chamadas ao Monger, trate os dados como o "alicerce" e a transformação como a "substância" do programa.

Aqui estão as recomendações baseadas nas fontes:

### 1. Cálculos em Clojure vs. Agregação no Mongo
A regra geral é: use o MongoDB para **filtrar e reduzir o volume de dados** (via `$match` e `$project`) e use funções puras em Clojure para **calcular métricas complexas**. 

*   **Vantagem do Clojure:** Funções puras são referencialmente transparentes, o que facilita o teste, a composição e a reutilização em diferentes partes do sistema (como jobs de reconciliação e handlers de API).
*   **Vantagem do Mongo:** Processar milhões de documentos diretamente no servidor evita o custo de transferência de rede e desserialização BSON no cliente.

### 2. Invariante de Teste: Recomputação Total vs. Cache
Modele seu sistema para que o cache seja uma **projeção pura do histórico de eventos** (partidas). 
*   **Propriedade:** `(compute-stats all-matches) == cached-stats`.
*   Use **testes baseados em propriedades** (como `test.check`) para verificar se, dada qualquer sequência aleatória de partidas, a recomputação total sempre produz o mesmo resultado que a atualização incremental do cache.

### 3. Transducers e Rollups em Clojure
Para realizar rollups (agrupamentos), prefira o uso de **Transducers** ou a combinação de `group-by` e `reduce`.
*   **Transducers:** São ideais porque desacoplam a transformação (o cálculo da estatística) da fonte dos dados (uma coleção, um canal `core.async` ou um cursor de banco de dados), realizando tudo em uma única passagem sem criar sequências intermediárias na memória.

### 4. Organização de Namespaces
As fórmulas de métricas devem residir em um namespace de domínio puro, como `sports.domain.analytics` ou `sports.logic.metrics`.
*   Este namespace não deve ter dependências de banco de dados.
*   As funções de "Ação" (que buscam dados no Mongo e salvam o resultado) devem viver em `sports.db.analytics-cache` ou similar, chamando as funções puras do domínio.

### 5. Evolução de Métricas sem Bugs de Escrita Dupla
Para evitar inconsistências, adote o padrão de **Update Modifiers** do MongoDB (como `$inc`) para atualizações incrementais. 
*   Em vez de ler o documento, alterar em Clojure e salvar de volta (o que causa condições de corrida), envie o comando de atualização atômica diretamente.
*   **Fluxo Seguro:** O job incremental calcula apenas o "delta" da nova partida e aplica `$inc` no cache do jogador.

### Exemplos de Implementação

**Métrica Derivada Pura (Cálculo):**
```clojure
(ns sports.domain.analytics)

(defn goal-contribution 
  "Cálculo puro: soma de gols e assistências."
  [{:keys [goals assists] :or {goals 0 assists 0}}]
  (+ goals assists))

(defn discipline-index
  "Fórmula para o índice de disciplina por jogo."
  [{:keys [yellow-cards red-cards games] :or {yellow-cards 0 red-cards 0 games 0}}]
  (if (pos? games)
    (/ (+ yellow-cards (* 3 red-cards)) (double games))
    0))
```

**Rollup de Partidas (Cálculo com Transducer):**
```clojure
(defn summarize-player-stats
  "Transforma uma sequência de estatísticas de partidas em um mapa agregado."
  [match-stats]
  (let [initial {:goals 0 :assists 0 :yellow-cards 0 :red-cards 0 :games 0}
        ;; Redutor que acumula os campos básicos
        rollup (reduce 
                 (fn [acc ms] 
                   (-> acc
                       (update :goals + (:goals ms 0))
                       (update :assists + (:assists ms 0))
                       (update :games inc)))
                 initial
                 match-stats)]
    ;; Adiciona métricas derivadas ao final
    (assoc rollup 
      :goal-contribution (goal-contribution rollup)
      :discipline-index (discipline-index rollup))))
```

Essa abordagem garante que suas regras de negócio (as fórmulas) estejam isoladas e sejam facilmente testáveis com dados puramente em memória, enquanto o MongoDB é usado como um mecanismo eficiente de persistência e cache de alto desempenho.

---

## 8. Jobs e efeitos assíncronos (backend)

_Prompt: secção 8_

For a single JVM deployment with minimal dependencies, Clojure provides powerful concurrency primitives that allow you to move away from OO Observer patterns. The idiomatic approach is to treat background work as a **pipeline of data** or a **succession of states** rather than a set of event listeners.

Below are recommendations based on the sources for structuring background tasks:

### 1. Separating Intent from Execution
Instead of passing a `Runnable` that encapsulates both the logic and the execution, separate the **Intent** (the "what") as pure data from the **Execution** (the "how").
*   **Intent:** A simple map like `{:op :recalc-stats :player-ids  :match-id "abc"}`.
*   **Execution:** A thin interpreter function that receives this data and performs the side effects.
This separation allows you to log, queue, and test the "intent" without actually triggering the recalc.

### 2. core.async vs. Agents vs. ThreadPoolExecutor
For your scale, the choice depends on your specific needs for coordination and backpressure:
*   **Agents:** These are ideal for **serialized access to a shared resource** (like your job status store). They bridge the gap between managing state and managing execution. Use `send-off` for these tasks because they involve I/O (Mongo writes) and shouldn't block the fixed CPU thread pool.
*   **core.async:** Best if you need to build a **transformation pipeline** or manage **backpressure**. By choosing a fixed-size buffer, you can force the producer (the HTTP write) to block or drop tasks if the background worker is overwhelmed, preventing system crashes.
*   **ThreadPoolExecutor:** While Clojure can use these directly, agents and `core.async` are generally preferred because they provide higher-level abstractions like parking (freeing up threads while waiting) and integrated state management.

### 3. Pure Plan + Effectful Run (Testability)
To ensure testability, the background handler should be a **Calculation** that returns a plan.
*   **Calculation:** `(defn plan-recalc [match-data] ...)` returns a sequence of "step" maps.
*   **Action:** A loop (via `go-loop` or an agent) that takes these steps and executes them.
This allows you to unit-test the logic of *who* needs a recalc and *what* formulas to apply using simple data assertions, without ever needing a mock executor.

### 4. Idempotency and Observability
In a functional style, observability is achieved via **Watchers** and **State Snapshots**:
*   **Observability:** Use `add-watch` on an atom or agent that tracks job statuses. Whenever the job state changes (e.g., `:pending` to `:success`), the watcher can log the change or notify a management API.
*   **Idempotency:** Since background tasks can be retried, ensure they are **idempotent**. Before writing a recalc result, check a "snapshot" of the match version or timestamp in the database to ensure you aren't overwriting newer data with an older job result.

### 5. Simple vs. Message Queue
*   **Keep it simple (In-process):** Stay with agents or `core.async` while you are on a single JVM. Clojure's references are highly efficient for in-memory coordination.
*   **Introduce an MQ (RabbitMQ/SQS):** Move to a message queue only when you require **durability across restarts** (since in-memory refs are lost if the JVM dies) or if you need to scale horizontally across multiple machines.

### Example: Agent-based serialized logging and job status
```clojure
;; A stateful agent to track job status and serialize execution
(def job-worker (agent {:active-jobs 0 :history []}))

(defn perform-recalc! [state player-ids]
  ;; This runs in a separate thread pool (send-off)
  (try
    (db/recalculate-stats! player-ids)
    (update state :history conj {:ids player-ids :status :success})
    (catch Exception e
      ;; Functional error handling: agents store their error state
      (update state :history conj {:ids player-ids :status :failed :error (.getMessage e)}))))

;; Triggered from the HTTP handler
(defn handle-match-write [request]
  (let [match (db/save-match! (:body request))
        p-ids (extract-player-ids match)]
    (send-off job-worker perform-recalc! p-ids) ;; Non-blocking async dispatch
    (resp/success {:match-id (:id match) :job :queued})))
```

---

## 9. Frontend CLJS: estado e efeitos funcionais

_Prompt: secção 9_

Para estruturar seu frontend em ClojureScript e Reagent de forma funcional e evitar o acúmulo de funções "setter" e efeitos colaterais espalhados, você deve adotar uma **arquitetura reativa** onde o estado é tratado como uma sucessão de valores imutáveis e a interface é uma projeção pura desses dados.

Abaixo estão as recomendações baseadas nos princípios de programação funcional e nas capacidades do Reagent/ClojureScript presentes nas fontes:

### 1. Evento → Redutor → Estado (Padrão Unificado)
Em vez de múltiplos setters como `set-players!`, utilize o **modelo de atualização unificada** do Clojure. Trate todas as interações como "eventos" (dados) e use uma única função redutora para transformar o estado.

*   **Vantagem:** Isso separa a *intenção* da mudança da *mecânica* da mudança, permitindo que você rastreie o histórico de transições de estado facilmente.
*   **Implementação:**
    ```cljs
    ;; Um único "redutor" puro (Cálculo)
    (defn app-reducer [state [event-type payload]]
      (case event-type
        :load-players-success (assoc state :players payload :loading? false)
        :set-filter (assoc-in state [:filters :category] payload)
        state))

    ;; Um despachante centralizado (Ação)
    (defn dispatch! [event]
      (swap! app-state app-reducer event))
    ```

### 2. Estado Derivado com Reactions
Para evitar redundância e "flags" de carregamento manuais, use **Reactions** (`reagent.ratom/reaction`). Elas funcionam como fórmulas de planilhas: recalculam automaticamente apenas quando os átomos que elas observam mudam.

*   **Uso:** Em vez de passar o `app-state` inteiro para os componentes, passe apenas a "reação" necessária.
*   **Exemplo para Carregamento/Erro:**
    ```cljs
    (require '[reagent.ratom :refer [reaction]])

    (def players-loading? (reaction (:loading? @app-state)))
    (def active-players (reaction 
      (filter :active (:players @app-state))))

    (defn player-list []
      (if @players-loading?
        [:div "Carregando..."]
        [:ul (for [p @active-players] ^{:key (:id p)} [player-row p])]))
    ```

### 3. Renderização Pura e Isolação de Efeitos
Mantenha suas funções de componente (Views) como **Cálculos puros**: elas recebem dados e retornam vetores Hiccup. Toda a comunicação com o mundo exterior (API via `cljs-http`) deve viver em um namespace de `effects`.

*   **Transparência Referencial:** Um componente é mais fácil de testar e reutilizar quando não depende de variáveis globais diretamente, mas recebe o que precisa via argumentos.
*   **Isolando Efeitos:** Use `core.async` dentro do seu namespace de efeitos para gerenciar a natureza assíncrona do HTTP sem bloquear a interface.

### 4. Padrões para Dashboard de Analytics
Para dashboards complexos, utilize **selectors** memoizados e organize o estado de forma que as transformações sejam eficientes.

*   **Selectors Memoizados:** Se um cálculo de métrica for caro (ex: agregação de estatísticas de jogadores), use a função `memoize` ou reações aninhadas para evitar reprocessamento desnecessário.
*   **Inputs de Gráficos como Dados:** Trate filtros de data e seletores de métricas como chaves no seu `app-state`. O gráfico será apenas uma View que dereferencia uma reação contendo os dados já filtrados e transformados.
*   **Transducers:** Para processar grandes volumes de dados de partidas no frontend antes de renderizar gráficos, use **transducers** para compor transformações (map, filter, rollup) de forma performática e sem criar coleções intermediárias.

### Resumo da Estrutura Recomendada
1.  **`app.state`**: Um único `r/atom` global.
2.  **`app.events`**: Funções puras que descrevem como o estado muda (redutores).
3.  **`app.effects`**: Onde as chamadas `cljs-http` e interações com o `localstorage` residem.
4.  **`app.subs`** (Reações): Onde vivem os "filtros" de estado que os componentes assinam.
5.  **`app.views`**: Componentes Reagent que apenas recebem dados e emitem Hiccup.

Essa separação garante que sua lógica de negócio (cálculos de métricas) não se misture com a lógica de renderização ou com os detalhes de transporte do HTTP.

---

## 10. Ordem de refactor FP (full-stack)

_Prompt: secção 10_

Com base nos princípios de Programação Funcional (FP) e arquitetura de sistemas Clojure presentes nas fontes, aqui está uma recomendação de plano de refatoração para migrar do modelo orientado a objetos (OO) para um modelo de **Ações, Cálculos e Dados**.

### 1. Ordem de Refatoração Recomendada
A sequência ideal deve priorizar a **estabilidade dos dados** (alicerce) antes da visualização (UI):

1.  **Matches (Partidas):** Refatore a lógica de partidas para extrair fórmulas de pontuação e estatísticas como **Cálculos puros**. Partidas são a "fonte da verdade" para o analytics.
2.  **Analytics Data Layer:** Implemente a lógica de agregação. Em vez de um "Repository", use funções que recebem uma coleção de partidas e retornam o estado agregado (Data).
3.  **Analytics API:** Crie os handlers que orquestram a leitura do MongoDB (Ação) e passam os dados para as fórmulas de analytics (Cálculo).
4.  **Analytics UI (CLJS):** Estruture o frontend usando o padrão **Evento → Redutor → Estado**, utilizando *Reactions* para métricas derivadas.

### 2. O que Simplificar no Piloto de Championships
O "piloto" OO serve como prova de conceito, mas deve ser simplificado antes de ser replicado:

*   **Remover Injeção via `ns-resolve`:** Substitua o padrão de "Repository" por funções que aceitam explicitamente o objeto `db` como argumento.
*   **Dissolver "Services":** Transforme os métodos de serviço em **Cálculos** (lógica de decisão pura) e **Ações** (interação com o mundo exterior).
*   **Manter a Validação na Fronteira:** A validação e os erros de domínio da Fase 1 são ativos valiosos. Mantenha o uso de esquemas de dados (Prismatic Schema ou similar) para garantir que as funções internas recebam apenas dados válidos.

### 3. Estratégia de Migração Segura
Dado que testes de contrato existem, use-os como sua rede de segurança principal:

1.  **Regra de Ouro:** Não mude o comportamento (visto pelos testes de contrato) enquanto refatora a estrutura interna.
2.  **Passo a Passo:** Em uma PR de refatoração, apenas mova a lógica de um "Service" para uma função pura (Cálculo) e teste-a via REPL ou `deftest` simples.
3.  **Encapsulamento de Transição:** Se necessário, mantenha a interface do "Service" antiga, mas faça-a delegar internamente para o novo núcleo funcional puro até que todas as chamadas sejam migradas.

### 4. Definition of Done (DoD) em Termos Funcionais
Uma fatia vertical está pronta quando satisfaz os seguintes critérios:

*   **Cálculos Isolados:** Toda a lógica de negócio (regras de partidas, limites de jogadores, cálculos de métricas) está em funções puras, sem chamadas a `mc/find` ou `java.util.Date.`.
*   **Efeitos nas Bordas:** As funções que tocam o MongoDB (Ações) são "rasas" e servem apenas para buscar dados e passar para os Cálculos.
*   **Dados como Dados:** As entidades são representadas por mapas simples de Clojure, não escondidas atrás de classes ou interfaces proprietárias.
*   **Testabilidade:** Os Cálculos possuem 100% de cobertura com testes baseados em dados (inputs fixos → outputs fixos), sem necessidade de mocks de banco de dados.

### 5. Decisão sobre o Piloto de Championships
**Recomendação:** Não refatore o piloto agora.
Aplique os novos princípios funcionais às fatias de **Matches** e **Players**. Use essas novas fatias como o "padrão-ouro". Uma vez que o time esteja confortável com a separação de Ações e Cálculos, refatore a fatia de Championships para remover a complexidade incidental do modelo OO original.

---

### Checklist de Implementação (Phased Plan)

*   **Fase A: Núcleo de Domínio (Matches & Analytics)**
    *   [ ] Definir funções de cálculo puro para métricas (ex: `goal-contribution`, `discipline-index`).
    *   [ ] Implementar transformações de rollup (Matches → AggregatedStats) usando `reduce` ou `transducers`.
*   **Fase B: Camada de Persistência (Monger)**
    *   [ ] Criar funções de persistência que aceitam `db` e utilizam operadores atômicos como `$inc` para o cache de analytics.
*   **Fase C: API e Handlers (Onion Architecture)**
    *   [ ] Configurar middleware de erro global para capturar falhas de validação/domínio.
    *   [ ] Implementar handlers que buscam dados (Ação), aplicam métricas (Cálculo) e retornam JSON.
*   **Fase D: Reagent Analytics Dashboard**
    *   [ ] Centralizar o estado em um único `r/atom`.
    *   [ ] Criar `reactions` para as métricas do dashboard para evitar reprocessamento desnecessário.
    *   [ ] Garantir que os componentes de UI sejam funções puras de seus argumentos.

---

## Decisões consolidadas

| Tema | Decisão Galáticos | Secção fonte |
|------|-------------------|--------------|
| Layout de namespaces | `domain/*` (puro) + `logic/*` (orquestração) + `handlers/*` + `db/*` + `db.protocol/*` | 1, 10 |
| Injeção de dependências | `defprotocol` + impl em `db/*`; testes com `reify`. Proibido `repo-call`/`ns-resolve` | 1, 5 |
| Erros: ex-info vs Result | Puro: `{:ok}`/`{:error}`; `ex-info` só em `logic/*`; HTTP via middleware | 2, 3 |
| Validação | Manter `validation/entity.clj` + pipelines `comp`; coerção na fronteira; sem Malli/spec por agora | 4 |
| Analytics | Mongo filtra volume; rollups/fórmulas em `domain/analytics`; invariante `recompute == cache` | 7 |
| Jobs | Intent map `{:op :recalc-stats :player-ids [...]}`; executor in-process (evoluir actual); agents opcional | 8 |
| CLJS | Event → reducer → `dispatch!`; `reaction` para derived state; efeitos em `effects.cljs` | 9 |
| Ordem de implementação | 02 championships FP → 03 matches → 04 rollout → 05–07 analytics | 10 |

Documentação derivada: [functional-architecture.md](../../reference/architecture/functional-architecture.md), [fp-improvement-checklist.md](../../backlog/fp-improvement-checklist.md).

---

## Notas de sessão

- Respostas completas (prompts 1–10) no notebook Clojure/CLJS.
- Prompt 10 recomendava não refactorar championships de imediato; **decisão do projecto:** documentar migração FP completa e remoção de `service/*`/`repository/*` nos planos 02–07 (implementação em fase de código separada).
- Validar símbolos contra `deps.edn` antes de codificar (ver [ai-assisted-code-audit.md](../../reference/quality/ai-assisted-code-audit.md)).
