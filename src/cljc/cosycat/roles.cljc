(ns cosycat.roles)

(def project-user-roles-descs
  {"guest" "Can read annotations and suggest changes but nothing more."
   "user" "Can read annotations, suggest changes, insert annotations and edit their own annotations."
   "project-lead" "Person in charge of the global research goals. Can edit everyone's annotations."
   "creator" "Almighty creator of the project. Nothing lies beyond their power."})

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
   :read   #{"owner" "creator" "project-lead" "user" "guest"}})

(defn check-role [roles-map action role]
  (boolean (some #{role} (get roles-map action))))

(defn check-project-role [action role]
  (check-role project-roles action role))

(defn check-annotation-role [action role]
  (check-role annotation-roles action role))
