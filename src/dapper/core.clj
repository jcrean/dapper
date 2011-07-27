(ns dapper.core
  (:require
   [clojure.string    :as str])
  (:use
   [clj-etl-utils.lang-utils  :only [raise]])
  (:import
   [com.unboundid.ldap.sdk
    LDAPConnection LDAPConnectionPool RoundRobinServerSet
    SimpleBindRequest SingleServerSet]))


(defonce *connection-registry* (atom {}))

(def ^:dynamic *current-connection* nil)

(def *default-pool-size* 5)
(def *default-port* 389)
(def *default-ssl-port* 636)

(defn- raise-unregistered-connection! [conn-name]
  (raise "Error: no LDAP connection registered with name [%s], registered names are [%s]"
         conn-name (str/join "," (keys @*connection-registry*))))

(defn register-ldap! [kwd cfg]
  (if (get @*connection-registry* kwd)
    (raise "LDAP Connection already registered under %s" kwd)
    (swap! *connection-registry* assoc kwd (atom {:connection nil :config cfg}))))

(defn unregister-ldap! [kwd]
  (if-let [conf (get @*connection-registry* kwd)]
    (do
      (when-let [conn (:connection @conf)]
        (.close conn))
      (swap! *connection-registry* dissoc kwd))
    (raise-unregistered-connection! kwd)))

(defn reregister-ldap! [kwd cfg]
  (when (get @*connection-registry* kwd)
    (unregister-ldap! kwd))
  (register-ldap! kwd cfg))


(defn configured-hosts [config]
  (concat
   (if (vector? (:host config))
     (:host config)
     (vector (:host config)))
   (:hosts config)))

(defn server-info [host-str]
  (let [host        (str/replace host-str #"^ldaps?://" "")
        [host port] (str/split host #":")
        port        (str/replace (or port *default-port*) #"\D*$" "")]
    {:host host
     :port (if (empty? port)
             *default-port*
             (Integer/parseInt port))}))

(defn round-robin-server-set [config]
  (let [servers (map server-info (configured-hosts config))]
    (RoundRobinServerSet.
     (into-array String (map :host servers))
     (int-array (map :port servers)))))

(defn single-server-set [config]
  (let [info (server-info (:host config))]
   (SingleServerSet.
    (:host info)
    (:port info))))

(defn create-server-set [config]
  (cond (or (:hosts config)
            (vector? (:host config)))
        (round-robin-server-set config)

        (string? (:host config))
        (single-server-set config)

        :else
        (raise "Error: Don't know how to create server-set from config: %s" config)))

(defn bind-dn [config]
  (or (:bind-dn config)
      (format "uid=%s,%s" (:bind-user config) (:user-dn-suffix config))))

(defn simple-bind-request
  ([]
     (SimpleBindRequest.))
  ([opts]
     (SimpleBindRequest. (bind-dn opts) (:bind-pass opts))))

(defn create-connection-pool [config]
  (LDAPConnectionPool.
   (create-server-set config)
   (simple-bind-request)
   (get config :pool-size *default-pool-size*)))

(defn create-connection [config]
  (println "Creating new connection....")
  (if (:pooled? config)
    (create-connection-pool config)
    (raise "Unimplented! non-pooled connections not yet implemented.")))

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

  (reregister-ldap!
   :dapper {:host "localhost"
            :user-dn-suffix "ou=users,dc=relayzone,dc=com"
            :pooled? true
            :pool-size 3})

  (with-ldap :dapper
    (printf "current-connection: %s" *current-connection*))

  (get @*connection-registry* :dapper)
  (ldap-connect! :dapper)
  )