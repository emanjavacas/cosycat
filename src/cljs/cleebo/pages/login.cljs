(ns cleebo.pages.login
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [taoensso.sente :as sente]
            [taoensso.timbre :as timbre]))

(declare login-form join-form)

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/ws" {:type :auto :packer :edn})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state   state))

(defmulti event-msg-handler :id)
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))
(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event]}]
  (timbre/info event))
(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (timbre/info "Channel socket successfully established!")
    (timbre/info (str "Channel socket state change: %s" ?data))))
(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (timbre/info (str "Push event from server: %s" ?data)))
(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (timbre/info (str "Handshake: %s" ?data))))

(def tabs-def 
  [{:id :login :label "Login" :say-this "Login with your account"}
   {:id :join :label "Join" :say-this "Create a new account"}])

(defn dispatch-panel [id]
  (case id
    :login [login-form]
    :join  [join-form]))

(defn by-id [id]
  (.getElementById js/document id))

(defn join []
  (let [name (re-frame/subscribe [:name])
        new-name (.-value (by-id "join-first"))]
    (timbre/info @chsk-state)
    (chsk-send! [:some/request {:had-a-callback? @name}] 5000
      (fn [cb-reply] (timbre/info "Callback reply: %s" cb-reply)))
    (re-frame/dispatch [:new-name new-name])))

(defn login []
  (let [user-id (.-value (by-id "login-username"))]
    (sente/ajax-call
     "/login"
     {:method :post
      :params {:user-id (str user-id)
               :csrf-token (:csrf-token @chsk-state)}}
     (fn [ajax-res]
       (sente/chsk-reconnect! chsk)))))

(defn login-form []
  [:div.panel
   [:div.panel-body
    [:form.form-horizontal {:id :login}
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :login-username :type "text" :placeholder "Username/Email"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-key"]]]
      [:input.form-control {:id :login-password :type "password" :placeholder "Password"}]]
     [:div.form-group.pull-right
      [:button.btn.btn-secondary
       {:type "button" :style {:margin-right "15px"} :on-click login} "Login"]]]]
      [:div.pull-right {:style {:margin-right "14px" :font-size "11px"}}
       [:a {:href "forgot"} "forgot password?"]]])

(defn join-form []
  [:div.panel
   [:div.panel-body
    [:form.form-horizontal {:id :join}
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :join-first :type "text" :placeholder "First name"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :join-last :type "text" :placeholder "Last name"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-email"]]]
      [:input.form-control {:id :join-email :type "text" :placeholder "Email"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-key"]]]
      [:input.form-control {:id :join-password :type "password" :placeholder "Password"}]]
     [:div.form-group.pull-right
      [:button.btn.btn-secondary
       {:on-click join :type "button" :style {:margin-right "15px"}} "Join"]]]]])

(defn login-panel []
  (let [selected? (reagent/atom (:id (first tabs-def)))
        re-select (fn [id] (reset! selected? id))]
    (fn []
      [re-com/v-box :margin "45px"
       :children
       [[re-com/h-box :gap "1"
         :children
         [[:br]
          [re-com/v-box :width "350px"
           :children 
           [[re-com/box :child 
             [re-com/horizontal-tabs
              :tabs tabs-def
              :model selected?
              :on-change re-select]]
            (dispatch-panel @selected?)]]
          [:br]]]]])))

(def router (atom nil))
(defn stop-router! [] (when-let [stop-fn @router] (stop-fn)))
(defn start-router! []
  (stop-router!)
  (reset! router (sente/start-chsk-router! ch-chsk event-msg-handler*)))
(start-router!)
