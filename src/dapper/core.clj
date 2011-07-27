(ns dapper.core
  (:require
   [clojure.string    :as str])
  (:use
   [clj-etl-utils.lang-utils  :only [raise]])
  (:import
   [com.unboundid.ldap.sdk LDAPConnection LDAPConnectionPool]))


(defonce *connection-registry* (atom {}))

(def ^:dynamic *current-connection* nil)

(defn- raise-unregistered-connection! [conn-name]
  (raise "Error: no LDAP connection registered with name [%s], registered names are [%s]"
         conn-name (str/join "," (keys @*connection-registry*))))

(defn register-ldap! [kwd cfg]
  (if (get @*connection-registry* kwd)
    (raise "LDAP Connection already registered under %s" kwd)
    (swap! *connection-registry* assoc kwd (atom {:connection nil :config cfg}))))

;; NB: need to actually make the connection, just testing registration for now
(defn create-connection [config]
  (println "Creating new connection....")
  (LDAPConnection.))

(defn ldap-connect! [conn-name]
  (if-let [cfg (get @*connection-registry* conn-name)]
    (or (:connection @cfg)
        (do
          (swap! cfg assoc :connection (create-connection (:config @cfg)))
          (:connection @cfg)))
    (raise-unregistered-connection! conn-name)))

(defn ldap-disconnect! [conn-name]
  (if-let [cfg (get @*connection-registry* conn-name)]
    (do
      (when-let [conn (:connection @cfg)]
        (println "Closing connection...")
        (.close conn))
      (println "Unsetting :connection in config...")
      (swap! cfg assoc :connection nil))
    (raise-unregistered-connection! conn-name)))

(defn- with-ldap* [conn-name body-fn]
  (binding [*current-connection* (ldap-connect! conn-name)]
    (try
     (body-fn)
     (catch Exception ex
       (printf "Caught exception during execution, msg=%s" (.getMessage ex)))
     (finally
      (ldap-disconnect! conn-name)))))


(defmacro with-ldap [conn-name & body]
  `(with-ldap* ~conn-name (fn [] ~@body)))


(comment

  (register-ldap!
   :dapper {:host "ec2-50-19-176-178.compute-1.amazonaws.com"
            :user-dn-suffix "ou=users,dc=relayzone,dc=com"})

  (with-ldap :dapper
    (printf "current-connection: %s" *current-connection*))

  (get @*connection-registry* :dapper)
  (ldap-connect! :dapper)
  )