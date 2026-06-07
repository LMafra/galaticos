(ns galaticos.components.toast
  "Toast notifications container. Consome `:toasts` em `state/app-state`
   e renderiza cada item como overlay fixo (bottom-right).
   Auto-dismiss é gerenciado por `state/push-toast!` via `setTimeout`;
   aqui lidamos apenas com render + fechar manual."
  (:require [galaticos.state :as state]
            [galaticos.components.common :as common]))

(defn- variant-styles [variant]
  (case variant
    :success "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-800/50 dark:bg-emerald-950/80 dark:text-emerald-200"
    :warning "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-800/50 dark:bg-amber-950/80 dark:text-amber-200"
    :info    "border-sky-200 bg-sky-50 text-sky-800 dark:border-sky-800/50 dark:bg-sky-950/80 dark:text-sky-200"
    "border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-800/50 dark:bg-rose-950/80 dark:text-rose-200"))

(defn- role-attrs [variant]
  (if (= variant :error)
    {:role "alert" :aria-live "assertive"}
    {:role "status" :aria-live "polite"}))

(defn- toast-item [{:keys [id variant message on-undo undo-label] :as _toast}]
  [:div (merge {:class (common/merge-classes
                        "pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-lg"
                        "motion-safe:transition-all motion-safe:duration-150"
                        (variant-styles variant))
                :data-toast-id (str id)}
               (role-attrs variant))
   [:div {:class "flex-1 leading-snug"}
    message
    (when on-undo
      [:button {:type "button"
                :class "ml-2 font-semibold underline underline-offset-2 hover:opacity-80"
                :on-click #(do (on-undo) (state/dismiss-toast! id))}
       (or undo-label "Desfazer")])]
   [:button {:type "button"
             :on-click #(state/dismiss-toast! id)
             :class "-mr-1 shrink-0 rounded-md p-1 text-current/70 hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-current/40"
             :aria-label "Fechar notificação"}
    "×"]])

(defn toast-container
  "Container fixo. Deve ser montado uma única vez (ex. em `layout`)."
  []
  (let [toasts (:toasts @state/app-state)]
    (when (seq toasts)
      [:div {:class "pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-end gap-2 px-4 md:bottom-6 sm:right-6 sm:left-auto sm:px-0"
             :aria-label "Notificações"}
       (for [t toasts]
         ^{:key (:id t)} [toast-item t])])))
