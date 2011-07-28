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
(def ^:dynamic *current-connection-config* nil)

(def *default-pool-size* 5)
(def *default-port* 389)
(def *default-ssl-port* 636)

(defn conn []
  *current-connection*)

(defn config
  ([]
     *current-connection-config*)
  ([kwd]
     (get *current-connection-config* kwd)))

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

(defn user-dn [username]
  (if-let [suffix (config :user-dn-suffix)]
    (format "uid=%s,%s" username suffix)
    (raise "Error: no :user-dn-suffix in LDAP config: %s" (config))))

(defn bind-dn [config]
  (or (:bind-dn config)
      (format "uid=%s,%s"
              (:bind-user config)
              (:user-dn-suffix config))))

(defn simple-bind-request
  ([]
     (SimpleBindRequest.))
  ([opts]
     (SimpleBindRequest. (bind-dn opts) (:bind-pass opts))))

(defn create-pooled-connection [config]
  (LDAPConnectionPool.
   (create-server-set config)
   (simple-bind-request)
   (get config :pool-size *default-pool-size*)))

(defn create-single-connection [config]
  (let [{host :host port :port} (server-info (:host config))]
    (LDAPConnection. host port)))

(defn create-connection [config]
  (println "Creating new connection....\n")
  (if (:pooled? config)
    (create-pooled-connection config)
    (create-single-connection config)))

(defn ldap-connect! [conn-name]
  (if-let [cfg (get @*connection-registry* conn-name)]
    (or (:connection @cfg)
        (do
          (swap! cfg assoc :connection (create-connection (:config @cfg)))
          cfg))
    (raise-unregistered-connection! conn-name)))

(defn ldap-disconnect! [conn-name]
  (if-let [cfg (get @*connection-registry* conn-name)]
    (do
      (when-let [conn (:connection @cfg)]
        (println "Closing connection..")
        (.close conn))
      (println "Unsetting :connection in config..")
      (swap! cfg assoc :connection nil))
    (raise-unregistered-connection! conn-name)))

(defn- with-ldap* [conn-name body-fn]
  (let [cfg (ldap-connect! conn-name)]
    (binding [*current-connection*        (:connection @cfg)
              *current-connection-config* (:config @cfg)]
      (try
       (body-fn)
       (catch Exception ex
         (printf "Caught exception during execution, msg=%s\n" (.getMessage ex)))
       (finally
        (ldap-disconnect! conn-name))))))

(defmacro with-ldap [conn-name & body]
  `(with-ldap* ~conn-name (fn [] ~@body)))

(defmacro defop [name args & body]
  `(defn ~name ~args
     (if-not (conn)
       (raise "Error: %s must be called in context of the with-ldap macro or by manually binding *current-connection*" ~name)
       (do ~@body))))

(defop bind [user password]
  (.bind (conn) (user-dn user) password))

(comment

  (reregister-ldap!
   :dapper {:host "localhost"
            :user-dn-suffix "ou=users,dc=relayzone,dc=com"
            :pooled? true
            :pool-size 3})

  (with-ldap :dapper
    (bind "jcrean" "jcjcjc"))

  (get @*connection-registry* :dapper)
  (ldap-connect! :dapper)
  )