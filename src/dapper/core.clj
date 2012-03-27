(ns dapper.core
  (:require
   [dapper.filters    :as f]
   [clojure.string    :as str])
  (:use
   dapper.bindings
   dapper.dsl
   [clj-etl-utils.lang-utils  :only [raise]])
  (:import
   [com.unboundid.ldap.sdk
    LDAPConnection LDAPConnectionPool RoundRobinServerSet
    SimpleBindRequest SingleServerSet]
   [com.unboundid.ldap.sdk.extensions
    PasswordModifyExtendedRequest
    PasswordModifyExtendedResult]))


(defonce *connection-registry* (atom {}))

(def *default-pool-size* 5)
(def *default-port*      389)
(def *default-ssl-port*  636)

(defn pooled? [cfg]
  (:pooled? (:config @cfg)))

(defn- raise-unregistered-connection! [conn-name]
  (raise "Error: no LDAP connection registered with name [%s], registered names are [%s]"
         conn-name (str/join "," (keys @*connection-registry*))))

(defn return-connection-to-pool [cfg]
  (when-not (:pool @cfg)
    (raise (format "Attempt to return connection to pool failed, no pool in config [%s]" @cfg)))
  (println " - Returning connection to pool ")
  (.releaseConnection (:pool @cfg) (:connection @cfg)))

(defn close-connection [cfg]
  (if (pooled? cfg)
    (return-connection-to-pool cfg)
    (.close (:connection @cfg))))

(defn lookup-ldap [kwd]
  (get @*connection-registry* kwd))

(defn register-ldap! [kwd cfg]
  (if (lookup-ldap kwd)
    (raise "LDAP Connection already registered under %s" kwd)
    (swap! *connection-registry* assoc kwd (atom {:connection nil :config cfg}))))

(defn unregister-ldap! [kwd]
  (if-let [conf (lookup-ldap kwd)]
    (do
      (when-let [conn (:connection @conf)]
        (close-connection conf))
      (when-let [pool (:pool @conf)]
        (.close pool))
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
      (format "%s=%s,%s"
              (:user-id-attr config)
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

(defn ensure-pool [cfg]
  (if-not (:pool @cfg)
    (do
      (println " Creating pooled connection")
      (swap! cfg assoc :pool (create-pooled-connection (:config @cfg))))
    (println " Pool already created, nothing to do")))

(defn ensure-connection [cfg]
  (println "ensure-connection")
  (if-not (:connection @cfg)
    (do
      (println " -- no connection yet, will create one")
      (swap! cfg assoc :connection
             (if (:pooled? (:config @cfg))
               (do
                 (println " * pooled connections requested, ensuring pool exists..")
                 (ensure-pool cfg)
                 (.getConnection (:pool @cfg)))
               (do
                 (println " * regular (non-pooled) connections requested, creating connection..")
                 (create-single-connection (:config @cfg))))))
    (println " -- connection already exists, nothing to do"))
  cfg)

(defn ldap-connect! [conn-name]
  (println (format "ldap-connect! [%s]" conn-name))
  (if-let [cfg (lookup-ldap conn-name)]
    (ensure-connection cfg)
    (raise-unregistered-connection! conn-name)))

(defn ldap-disconnect! [conn-name]
  (println "ldap-disconnect!")
  (if-let [cfg (get @*connection-registry* conn-name)]
    (do
      (when-let [conn (:connection @cfg)]
        (println "Closing connection..")
        (close-connection cfg))
      (println "Unsetting :connection in config..")
      (swap! cfg assoc :connection nil))
    (raise-unregistered-connection! conn-name)))

(defn with-ldap* [conn-name body-fn]
  (let [cfg (ldap-connect! conn-name)]
    (binding [*current-connection*        (:connection @cfg)
              *current-connection-config* (:config @cfg)]
      (try
       (body-fn)
       (catch Exception ex
         (printf "Caught exception during execution, msg=%s\n" (.getMessage ex))
         #_(raise ex))
       (finally
        (ldap-disconnect! conn-name))))))

(defmacro with-ldap [conn-name & body]
  `(with-ldap* ~conn-name (fn [] ~@body)))

(comment

  (reregister-ldap!
   :dapper {:host           "ec2-107-22-159-68.compute-1.amazonaws.com"
            :user-id-attr   "uid"
            :user-dn-suffix "ou=users,dc=relayzone,dc=com"
            :pooled?        true
            :pool-size      3})

  (with-ldap :dapper
    (bind "cn=admin,dc=relayzone,dc=com" "admin123")
    #_(add-user "jcrean" "jcjcjc" "josh" "crean"))

  (with-ldap :dapper
    (bind "cn=admin,dc=relayzone,dc=com" "admin123")
    (add-user "jdoe" "jdoe123" "john" "doe"))

  (with-ldap :dapper
    (find-users))

  (with-ldap :dapper
    (find-users
     (f/any :objectClass)
     :cn :uid :sn))

  (with-ldap :dapper
    (find-users
     (f/and
      (f/= :sn "crean")
      (f/= :uid "jcrean"))))

  (with-ldap :dapper
    (find-users
     (f/and
      (f/not (f/= :uid "jcrean"))
      (f/not (f/= :uid "jdoe")))))
  )

