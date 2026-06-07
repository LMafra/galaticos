(ns galaticos.ui-copy
  "Centralized Portuguese UI microcopy. See docs/reference/ui/ui-decisions.md.")

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
