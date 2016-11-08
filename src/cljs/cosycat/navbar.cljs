(ns cosycat.navbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.routes :refer [nav!]]
            [cosycat.components :refer [user-thumb]]
            [cosycat.app-utils :refer [function?]]
            [cosycat.utils :refer [nbsp format]]
            [cosycat.ajax-interceptors
             :refer [add-interceptor csrf-interceptor ajax-header-interceptor debug-interceptor]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

(defn icon-label [icon label]
  [:span [:i {:class (str "zmdi " icon)      
              :style {:line-height "20px"
                      :font-size "15px"
                      :margin-right "5px"}}]
   label])

(defn user-brand-span [username active-project]
  (fn [username active-project]
    [:span
     (when @active-project {:style {:cursor "pointer"} :onClick #(nav! (str "/project/" @active-project))})
     username
     (when @active-project [:span.truncate {:style {:white-space "nowrap"}} (str "@" @active-project)])]))

(defn user-brand-component [active-project]
  (let [user (re-frame/subscribe [:me])]
    (fn [active-project]
      (let [{username :username {href :href} :avatar} @user]
        (when username                    ;wait until loaded
          [bs/navbar-brand
           [user-brand-span username active-project]
           [:span [user-thumb href {:style {:margin-left "10px"} :height "30px" :width "30px"}]]])))))

(defn navlink [{:keys [target href label icon]}]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-item
       {:eventKey target
        :class (if (= @active target) "active")
        :href (if (function? href) (href) href)}
       [icon-label icon label]])))

(defn navdropdown [target label icon & {:keys [children]}]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn [target label icon & {:keys [children]}]
      [bs/nav-dropdown
       {:eventKey target
        :id "dropdown"
        ;; deref active to force rerender and rerun of (href)
        :class (if (= @active target) "active") 
        :title (reagent/as-component [icon-label icon label])}
       (for [[idx {:keys [label href on-select style] :as args}] (map-indexed vector children)
             :let [k (str label idx)]]
         ^{:key k}
         [bs/menu-item
          (merge {:eventKey k
                  :style style
                  :href (if (function? href) (href) href)
                  :onSelect on-select}
                 (dissoc args :style :href :onSelect))
          label])])))

(defn merge-target-project-url
  "computes url for project, adding subpath /query if origin was in query itself
   `cosycat#/project/project1/query` -> `cosycat#/project/project2/query`
   instead of `cosycat#/project/project1/query` -> `cosycat#/project/project2`"
  [project-name]
  (let [prefix (str "#/project/" project-name)
        origin (.-lastToken_ cosycat.routes/history)]
    (if-not origin
      prefix
      (cond (.endsWith origin "query") (str prefix "/query")
            (.endsWith origin "settings") (str prefix "/settings")
            :else prefix))))

(defn projects-dropdown [projects active-project]
  (fn [projects active-project]
    [navdropdown :no-panel "Project" ""
     :children
     (doall
      (for [[project-name {:keys [project]}] @projects]
        {:label project-name
         :href #(merge-target-project-url project-name)
         :style (when (= project-name @active-project)
                  {:background-color "#e7e7e7"
                   :color "black"})}))]))

(defn navbar [active-panel]
  (let [active-project (re-frame/subscribe [:session :active-project])
        roles (re-frame/subscribe [:me :roles])
        projects (re-frame/subscribe [:projects])]
    (fn [active-panel]
      [bs/navbar
       {:inverse false
        :responsive true
        :fixedTop true
        :fluid true}
       [bs/navbar-header [user-brand-component active-project] [bs/navbar-toggle]]
       [bs/navbar-collapse
        [bs/nav {:pullRight true}
         ;; projects
         (when (and @active-project (not (empty? @projects)))
           [projects-dropdown projects active-project])
         ;; query
         (when (and @active-project (not= @active-panel :front-panel))
           [navlink {:target :query-panel
                     :href #(str "#/project/" @active-project "/query")
                     :label "Query"
                     :icon "zmdi-search"}])
         ;; settings
         [navlink {:target :settings-panel
                   :href #(if @active-project
                            (str "#/project/" @active-project "/settings")
                            "#/settings")
                   :label "Settings"
                   :icon "zmdi-settings"}]
         ;; home
         [navlink {:target :front-panel
                   :href "#/"
                   :label "Home"
                   :icon "zmdi-home"}]
         ;; exit
         [navlink {:target :exit
                   :href "#/exit"
                   :label "Exit"
                   :icon "zmdi-power"}]
         ;; admin
         (when (contains? @roles "admin")
           [navlink {:target :admin
                     :href "#/admin"
                     :label "Admin"
                     :icon "zmdi-bug"}])]]])))
