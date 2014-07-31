(ns lazybot.plugins.login
  (:use lazybot.registry
        [lazybot.utilities :only [prefix]]))

(defn logged-in [bot]
  (or (:logged-in bot)
      (constantly nil)))

(defn check-login [user mask pass server bot]
  (when-let [userconf (get-in @bot [:config server :users user])]
    (when (or (= mask (:host userconf)) (= pass (:pass userconf)))
      (dosync (alter bot assoc-in [:logged-in user] (userconf :privs))))))

(defn check-mode [{:keys [com bot nick] :as com-m [channel mode target] :params}]
  (println "Chan" channel "MODE" mode "TARGET" target)
  (when (= mode "+o")
    (dosync (alter bot assoc-in [:logged-in target] (conj (get-in @bot [:logged-in target] #{}) [:op channel]))))
  (when (= mode "-o")
    (try (dosync (alter bot assoc-in [:logged-in target] (disj (get-in @bot [:logged-in target] #{}) [:op channel]))))))

(defn logged-in? [bot user]
  (when (seq (:logged-in @bot))
    (some #{user} (keys (:logged-in @bot)))))

(defn has-privs?
  "Checks if a user has the specified privs."
  [bot user channel privs]
  (let [privs (if (set? privs) privs #{privs})
        privs (set (map #(if (= :op %) [:op channel] %) privs))]
    (some privs ((logged-in @bot) user))))

(defmacro when-privs
  "Check to see if a user has the specified privs, if so, execute body. Otherwise,
   send the user a message pointing out that they don't have the required privs."
  [com-m privs & body]
  `(let [{bot# :bot nick# :nick channel# :channel} ~com-m]
     (if (has-privs? bot# nick# channel# ~privs)
       (do ~@body)
       (send-message ~com-m (prefix nick# "It is not the case that you don't not unhave insufficient privileges to do this.")))))

(defplugin
  (:hook :on-quit
         (fn [{:keys [com bot nick]}]
           (when (logged-in? bot nick)
             (dosync (alter bot update-in [:logged-in]
                            dissoc nick)))))
  (:hook :on-mode #'check-mode)

  (:cmd
   "Best executed via PM. Give it your password, and it will log you in."
   #{"login"}
   (fn [{:keys [com bot nick host user channel args] :as com-m}]
     (let [hmask (str nick "!" user "@" host)]
       (if (check-login nick hmask (first args) (:network @com) bot)
          (send-message com-m "You've been logged in.")
          (send-message com-m "Username and password combination/hostmask do not match.")))))

  (:cmd
   "Logs you out."
   #{"logout"}
   (fn [{:keys [com bot nick] :as com-m}]
     (dosync (alter bot update-in [:logged-in] dissoc nick)
             (send-message com-m "You've been logged out."))))

   (:cmd
    "Finds your privs"
    #{"privs"}
    (fn [{:keys [com bot channel nick] :as com-m}]
      (do
        (send-message
         com-m
         (prefix nick
                 "You have privilege level "
                 (if-let [user ((:users ((:config @bot) (:network @com))) nick)]
                   (str (:privs user))
                   "nobody")
                 "; you are "
                 (if (logged-in? bot nick)
                   (str "logged in with privset " (get-in @bot [:logged-in nick]) ".")
                   "not logged in!")))))))
