(ns galaticos.delete-undo
  "Delayed commit delete with undo toast (UX-PLAN-03)."
  (:require [galaticos.state :as state]
            [galaticos.ui-copy :as ui-copy]))

(def default-undo-ms 10000)

(defn schedule!
  "Remove from UI immediately (`on-remove`), commit API delete after `undo-ms`,
   or cancel via undo (`on-rollback`). `on-commit` receives `on-success` and `on-error`."
  [{:keys [message on-remove on-commit on-rollback undo-ms]
    :or {undo-ms default-undo-ms}}]
  (when on-remove (on-remove))
  (let [committed? (atom false)
        timer (js/setTimeout
               (fn []
                 (when-not @committed?
                   (reset! committed? true)
                   (on-commit
                    (fn [_] nil)
                    (fn [err]
                      (state/push-toast!
                       (str ui-copy/delete-commit-error " " err)
                       {:variant :error :ttl nil})))))
               undo-ms)]
    (state/toast-with-undo!
     message
     (fn []
       (js/clearTimeout timer)
       (when-not @committed?
         (when on-rollback (on-rollback)))))))
