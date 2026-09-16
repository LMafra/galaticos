(ns galaticos.ui-copy
  "Centralized Portuguese UI microcopy. See docs/reference/ui/ui-decisions.md."
  (:require [clojure.string :as str]))

;; --- Undo / deletes (UX-PLAN-03) ---

(def undo-window-hint "Desfazer nos próximos 10 segundos.")

(defn undo-removed
  "Toast após remoção optimista; commit API após janela de undo."
  [subject]
  (str subject " removido. " undo-window-hint))

(def player-removed (undo-removed "Jogador"))
(def match-removed (undo-removed "Partida"))
(def team-removed (undo-removed "Time"))
(def championship-removed (undo-removed "Campeonato"))

(defn roster-player-removed
  [player-name]
  (str player-name " removido do elenco. " undo-window-hint))

(def delete-commit-error
  "Não foi possível concluir a remoção. Tente novamente ou recarregue a página.")

;; --- Merge (UX-PLAN-13) ---

(def merge-undo-toast
  (str "Unificação em 10 s. Desfazer cancela a operação."))

(def merge-success "Registos unificados.")
(def merge-cancelled "Unificação cancelada.")
(def merge-load-error "Não foi possível carregar os jogadores para unificar. Tente novamente.")

;; --- Formulários / API ---

(def form-save-error
  "Não foi possível guardar. Revise os campos assinalados.")

(def form-field-summary-title "Corrija os campos assinalados:")

(defn finalize-season-error
  [detail]
  (str "Não foi possível encerrar a temporada. "
       (or detail "Verifique o checklist e tente novamente.")))

;; --- Empty states ---

(def empty-roster
  "O plantel está vazio. Adicione o primeiro jogador — nome, número e posição bastam para começar.")

(def empty-roster-filter
  "Nenhum jogador corresponde a estes filtros. Limpe a pesquisa ou altere os critérios.")

(def empty-matches-season
  "Ainda não há partidas nesta temporada. Registe a primeira partida para começar o calendário.")

(def empty-dashboard-auth
  "Ainda não há dados no dashboard. Comece por criar um campeonato ou registar um jogador.")

(def empty-dashboard-guest
  "Não há dados públicos de dashboard neste momento.")

;; --- Enrollment / Inscrições (RVMF + poka-yoke) ---

(defn enrollment-limit-reached
  "What happened — championship at max-players."
  [max-players]
  (str "Este campeonato atingiu o limite máximo de "
       max-players
       " jogadores inscritos."))

(def enrollment-limit-how-to-fix
  "Remova um jogador da lista abaixo ou aumente o limite nas configurações do campeonato.")

(defn enrollment-limit-placeholder
  "Disabled search field placeholder when n >= max."
  [enrolled max-players]
  (str "Limite de inscrições atingido (" enrolled "/" max-players ")"))

(defn enrollment-counter-label
  "Accessible label for n/max badge."
  [label]
  (str label " inscritos"))

(def empty-championship-roster
  "Nenhum jogador inscrito neste campeonato ainda. Utilize o campo de busca acima para inscrever atletas.")

(def batch-enroll-button
  "Inscrever em lote")

(def batch-enroll-title
  "Inscrever em lote")

(def batch-enroll-confirm
  "Confirmar inscrições")

(def batch-enroll-search-placeholder
  "Buscar não inscritos...")

(def batch-enroll-empty
  "Não há jogadores disponíveis para inscrição.")

(def batch-enroll-none-match
  "Nenhum jogador corresponde à busca.")

(defn batch-enroll-success
  [n]
  (cond
    (<= n 0) "Nenhum jogador inscrito."
    (= n 1) "1 jogador inscrito no campeonato."
    :else (str n " jogadores inscritos no campeonato.")))

(defn batch-enroll-partial-failure
  "What happened + who failed — roster list stays intact for successes."
  [ok-n fail-labels]
  (let [fails (str/join ", " fail-labels)
        ok-part (cond
                  (zero? ok-n) "Nenhuma inscrição concluída."
                  (= ok-n 1) "1 inscrição concluída."
                  :else (str ok-n " inscrições concluídas."))
        fail-part (if (str/blank? fails)
                    "Algumas inscrições falharam."
                    (str "Falharam: " fails "."))]
    (str ok-part " " fail-part)))

;; --- Wave 3: forcing functions, reason, persistent banners ---

(def no-active-season-banner
  "Este campeonato não tem temporada ativa. Ative uma temporada abaixo para inscrições e partidas.")

(def no-active-season-how-to-fix
  "Crie ou ative uma temporada na secção Temporadas.")

(def sensitive-reason-label
  "Motivo (obrigatório)")

(def sensitive-reason-hint
  "Explique brevemente porquê (mín. 3 caracteres). Fica no registo de auditoria.")

(def sensitive-reason-invalid
  "Indique um motivo com pelo menos 3 caracteres.")

(def finalize-checklist-title
  "Checklist de finalização")

(def merge-reason-placeholder
  "Ex.: Duplicado criado no import da época 2025")

(def finalize-reason-placeholder
  "Ex.: Época encerrada após final do torneio")

;; --- Wave 4: error microcopy pattern ---

(defn what-happened-how-to-fix
  "Uniform PT error: [o que aconteceu] + [como corrigir]."
  [what how]
  (str (str/trim (str what))
       (when-not (str/blank? (str how))
         (str " " (str/trim (str how))))))

(def network-error-what
  "Não foi possível contactar o servidor.")

(def network-error-how
  "Verifique a ligação e tente novamente.")

(def network-error
  (what-happened-how-to-fix network-error-what network-error-how))

(def session-expired-what
  "A sessão expirou ou não está autenticado.")

(def session-expired-how
  "Inicie sessão novamente para continuar.")

(def session-expired
  (what-happened-how-to-fix session-expired-what session-expired-how))

;; --- Login navigation ---

(def login-back
  "Voltar")

(def login-back-aria
  "Voltar ao Dashboard")

(def login-continue-as-guest
  "Continuar como visitante")
