(ns cleebo.roles)

(defn app-roles [role]
  (let [roles {:admin ::admin
               :user ::user}]
    (get roles role nil)))

(derive ::admin ::user)

(def project-roles
  "a map from project-related actions to required roles"
  {:delete #{"creator"}                 ;remove project
   :write  #{"creator" "project-lead"}  ;update metadata
   :update #{"creator" "project-lead" "user"}  ;push update
   :read   #{"creator" "project-lead" "user"} ;retrieve project
   })

(def annotation-roles
  "a map from project-related actions to required roles"
  {:update #{"creator" "project-lead"}
   :delete #{"creator"}})

(defn check-role [roles-map action role]
  (boolean (some #{role} (get project-roles action))))

(defn check-project-role [action role]
  (check-role project-roles action role))

(defn check-annotation-update-role
  [action role]
  (check-role annotation-roles action role))
