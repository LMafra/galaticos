(ns galaticos.finalization
  "Pure finalization checklist + sensitive-action reason helpers.
   No app-state, window, or HTTP."
  (:require [clojure.string :as str]
            [galaticos.enrollment :as enrollment]))

(defn parse-titles-count
  "Non-negative int, or 0 when blank/invalid."
  [v]
  (cond
    (nil? v) 0
    (number? v) (if (js/isNaN v) 0 (max 0 (long v)))
    (string? v)
    (if (str/blank? v)
      0
      (let [n (js/parseInt v 10)]
        (if (js/isNaN n) 0 (max 0 n))))
    :else 0))

(defn reason-valid?
  "Mandatory reason for sensitive admin actions (min 3 chars after trim)."
  [reason]
  (let [t (str/trim (str (or reason "")))]
    (>= (count t) 3)))

(defn normalize-reason
  [reason]
  (str/trim (str (or reason ""))))

(defn- status-active?
  [status]
  (let [s (str/lower-case (str (or status "")))]
    (or (= s "active") (= s "ativo"))))

(defn finalization-checklist
  "Forcing-function items for championship finalize.
   Each item: {:id :label :ok?}.
   Context keys: :status :finished-at :enrolled-count :max-players
                 :titles-award-count :winner-ids :match-count :reason."
  [{:keys [status finished-at enrolled-count max-players
           titles-award-count winner-ids match-count reason]}]
  (let [n (or enrolled-count 0)
        titles (parse-titles-count titles-award-count)
        winners (or winner-ids #{})
        winner-n (count winners)
        need-winners? (pos? titles)
        mx (enrollment/normalize-max-players max-players)
        within-limit? (or (nil? mx) (<= n mx))]
    [{:id :active
      :label "Campeonato ativo"
      :ok? (status-active? status)}
     {:id :not-finished
      :label "Ainda não finalizado"
      :ok? (nil? finished-at)}
     {:id :has-roster
      :label "Há jogadores inscritos"
      :ok? (pos? n)}
     {:id :within-limit
      :label "Inscritos dentro do limite"
      :ok? within-limit?}
     {:id :has-matches
      :label "Há partidas registadas"
      :ok? (pos? (or match-count 0))}
     {:id :winners
      :label (if need-winners?
               "Vencedores definidos"
               "Vencedores (opcional — 0 títulos)")
      :ok? (or (not need-winners?) (pos? winner-n))}
     {:id :reason
      :label "Motivo registado"
      :ok? (reason-valid? reason)}]))

(defn checklist-complete?
  [items]
  (every? :ok? (or items [])))

(defn checklist-summary-label
  "e.g. \"4/7 requisitos cumpridos\"."
  [items]
  (let [xs (or items [])
        ok (count (filter :ok? xs))
        total (count xs)]
    (str ok "/" total " requisitos cumpridos")))
