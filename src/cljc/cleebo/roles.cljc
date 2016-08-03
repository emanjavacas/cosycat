(ns cleebo.roles)

(defn app-roles [role]
  (let [roles {:admin ::admin
               :user ::user}]
    (get roles role nil)))

(derive ::admin ::user)

(def project-user-roles
  {"guest" "Can read the annotations but nothing more."
   "user" "Can read the annotation and suggests changes, but not edit them."
   "project-lead" "Person in charge of the global research goals. Can edit but is not allowed to delete annotations."
   ;; "creator" "Almighty creator of the project. Nothing lies beneath his/her power."
   })

(def project-roles
  "a map from project-related actions to required roles"
  {:delete #{"creator" "project-lead" "user"}  ;remove project (see cleebo.db.projects)
   :write  #{"creator" "project-lead"}  ;update metadata
   :update #{"creator" "project-lead" "user"}  ;push update
   :read   #{"creator" "project-lead" "user" "guest"} ;retrieve project
   })

(def annotation-roles
  "a map from project-related actions to required roles"
  {:delete #{"creator"}
   :update #{"creator" "project-lead"}
   :write  #{"creator" "project-lead" "user"}
   :read   #{"creator" "project-lead" "user" "guest"}});retrieve project

(defn check-role [roles-map action role]
  (boolean (some #{role} (get project-roles action))))

(defn check-project-role [action role]
  (check-role project-roles action role))

(defn check-annotation-role [action role]
  (check-role annotation-roles action role))
