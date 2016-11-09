(ns cosycat.roles)

(defn app-roles [role]
  (let [roles {:admin ::admin
               :user ::user}]
    (get roles role nil)))

(derive ::admin ::user)

(def project-user-roles-descs
  {"guest" "Can read the annotations but nothing more."
   "user" "Can read the annotation and suggests changes, but not edit them."
   "project-lead" "Person in charge of the global research goals. Can edit but is not allowed to delete annotations."
   "creator" "Almighty creator of the project. Nothing lies beneath his/her power."})

(def project-user-roles
  ["guest" "user" "project-lead"])

(def project-roles
  "a map from project-related actions to required roles"
  {:delete #{"creator" "project-lead" "user"}  ;remove project (see cosycat.db.projects)
   :write  #{"creator" "project-lead"}  ;update metadata
   :update #{"creator" "project-lead" "user"}  ;push update
   :read   #{"creator" "project-lead" "user" "guest"}});retrieve project

(def annotation-roles
  "a map from annotation actions to required roles"
  {:update #{"owner" "creator" "project-lead"}
   :delete #{"owner" "creator" "project-lead"}
   :write  #{"owner" "creator" "project-lead" "user"}
   :read   #{"owner" "creator" "project-lead" "user" "guest"}});retrieve project

(defn check-role [roles-map action role]
  (boolean (some #{role} (get roles-map action))))

(defn check-project-role [action role]
  (check-role project-roles action role))

(defn check-annotation-role [action role]
  (check-role annotation-roles action role))
