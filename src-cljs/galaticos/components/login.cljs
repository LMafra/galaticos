(ns galaticos.components.login
  "Login page component"
  (:require [reagent.core :as r]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [reitit.frontend.easy :as rfe]))

(defn login-page []
  (let [username (r/atom "")
        password (r/atom "")
        error (r/atom nil)
        loading (r/atom false)]
    (fn []
      (let [{:keys [authenticated]} @state/app-state]
        ;; Redirect if already authenticated
        (when authenticated
          (rfe/push-state :dashboard))
        [:div {:style {:max-width "400px"
                      :margin "100px auto"
                      :padding "40px"
                      :background-color "white"
                      :border-radius "8px"
                      :box-shadow "0 2px 10px rgba(0,0,0,0.1)"}}
         [:h2 {:style {:margin-bottom "30px"
                       :text-align "center"
                       :color "#333"}}
          "Login - Galáticos"]
         (when @error
           [:div {:style {:background-color "#fee"
                         :border "1px solid #fcc"
                         :padding "10px"
                         :margin-bottom "20px"
                         :border-radius "4px"
                         :color "#c00"}}
            @error])
         [:form {:on-submit (fn [e]
                             (.preventDefault e)
                             (reset! error nil)
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
                                         (reset! error (str "Erro ao fazer login: " err)))))}
          [common/input-field "Usuário" @username #(reset! username %) {:placeholder "Digite seu usuário"}]
          [common/input-field "Senha" @password #(reset! password %) {:type "password" :placeholder "Digite sua senha"}]
          [common/button "Entrar"
           (fn [_e] nil)
           {:type "submit"
            :disabled @loading
            :style {:width "100%"
                   :margin-top "10px"
                   :padding "12px"
                   :background-color "#4CAF50"
                   :color "white"
                   :border "none"
                   :font-size "16px"
                   :font-weight "bold"}}]
          (when @loading
            [:div {:style {:text-align "center"
                          :margin-top "15px"
                          :color "#666"}}
             "Autenticando..."])]]))))

