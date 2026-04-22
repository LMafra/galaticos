(ns galaticos.components.login
  "Login page component"
  (:require [reagent.core :as r]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [reitit.frontend.easy :as rfe]
            ["lucide-react" :refer [Sun Moon]]))

(defn login-page []
  (let [username (r/atom "")
        password (r/atom "")
        loading (r/atom false)]
    (fn []
      (let [{:keys [authenticated]} @state/app-state
            theme (get-in @state/app-state [:ui :theme])]
        (when authenticated
          (js/requestAnimationFrame #(rfe/push-state :dashboard)))
        [:main#main-content
         {:class "relative flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-slate-950"}
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
           [:form {:class "space-y-4"
                   :on-submit (fn [e]
                                (.preventDefault e)
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
                                             (state/toast-error! (str "Erro ao fazer login: " err)))))}
            [common/input-field "Usuário" @username #(reset! username %) :placeholder "Digite seu usuário"]
            [common/input-field "Senha" @password #(reset! password %) :type "password" :placeholder "Digite sua senha"]
            [common/button (if @loading "Autenticando..." "Entrar")
             (fn [_e] nil)
             :type "submit"
             :disabled @loading
             :variant :primary :class "w-full"]]]]]))))

