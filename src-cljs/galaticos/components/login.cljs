(ns galaticos.components.login
  "Login page component"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [reitit.frontend.easy :as rfe]
            ["lucide-react" :refer [Sun Moon]]))

(defn- validate-username [username]
  (let [u (str/trim (or username ""))]
    (cond
      (str/blank? u) "Informe o usuário."
      (< (count u) 2) "Usuário deve ter pelo menos 2 caracteres."
      :else nil)))

(defn login-page []
  (let [username (r/atom "")
        password (r/atom "")
        loading (r/atom false)
        login-error (r/atom nil)
        username-error (r/atom nil)
        touched-username? (r/atom false)]
    (fn []
      (let [{:keys [authenticated]} @state/app-state
            theme (get-in @state/app-state [:ui :theme])]
        (when authenticated
          (js/requestAnimationFrame #(rfe/push-state :dashboard)))
        [:main#main-content
         {:class "relative flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-slate-950"}
         (when (:auth-loading? @state/app-state)
           [:div {:class "pointer-events-none absolute inset-x-0 top-0 z-20 h-0.5 overflow-hidden bg-brand-maroon/15"
                  :role "progressbar"
                  :aria-label "A carregar"}
            [:div {:class "h-full w-1/3 animate-pulse bg-brand-maroon"}]])
         [:div {:class "absolute right-4 top-4 z-10"}
          [:button {:type "button"
                    :class "inline-flex rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                    :on-click #(state/set-theme! (if (= theme "dark") "light" "dark"))
                    :aria-label "Alternar tema claro/escuro"}
           (if (= theme "dark")
             [:> Sun {:size 18}]
             [:> Moon {:size 18}])]]
         [common/card
          [:div {:class "w-full max-w-md space-y-6"}
           [:div {:class "text-center"}
            [:div {:class "mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-maroon text-white font-bold"} "G"]
            [:h2 {:class "mt-4 text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Login - Galáticos"]
            [:p {:class "mt-1 text-sm text-slate-500 dark:text-slate-400"} "Acesse sua plataforma de gestão de elenco"]]
           (when @login-error
             [common/error-message @login-error])
           [:form {:class "space-y-4"
                   :on-submit (fn [e]
                                (.preventDefault e)
                                (reset! login-error nil)
                                (let [u-err (validate-username @username)]
                                  (reset! username-error u-err)
                                  (reset! touched-username? true)
                                  (cond
                                    u-err nil
                                    (str/blank? (str/trim @password))
                                    (reset! login-error "Informe a senha.")
                                    :else
                                    (do
                                      (reset! loading true)
                                      (api/login @username @password
                                                 (fn [data]
                                                   (reset! loading false)
                                                   (let [user (:username data)
                                                         token (:token data)]
                                                     (when token
                                                       (state/set-user! user token)
                                                       (rfe/push-state :dashboard))))
                                                 (fn [err]
                                                   (reset! loading false)
                                                   (reset! login-error
                                                            (str "Não foi possível entrar. Verifique usuário e senha."
                                                                 (when err (str " (" err ")"))))))))))}
            [common/input-field "Usuário" @username
             #(do (reset! username %)
                  (when @touched-username?
                    (reset! username-error (validate-username %))))
             :placeholder "Digite seu usuário"
             :error (when @touched-username? @username-error)
             :id "login-username"
             :on-blur #(do (reset! touched-username? true)
                           (reset! username-error (validate-username @username)))]
            [common/input-field "Senha" @password #(reset! password %)
             :type "password" :placeholder "Digite sua senha" :id "login-password"]
            [common/submit-button "Entrar"
             (fn [_e] nil)
             :type "submit"
             :saving? @loading
             :saving-label "Autenticando…"
             :class "w-full min-h-11"]]]]]))))
