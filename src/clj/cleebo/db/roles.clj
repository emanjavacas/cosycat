(ns cleebo.db.roles)

(defn app-roles [role]
  (let [roles {:admin ::admin
               :user ::user}]
    (get roles role nil)))

(derive ::admin ::user)
