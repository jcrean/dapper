(ns dapper.dsl
  (:require
   [clojure.string :as str])
  (:use
   dapper.bindings
   [clj-etl-utils.lang-utils :only [raise]])
  (:import
   [com.unboundid.ldap.sdk Entry Attribute DN DeleteRequest]
   [com.unboundid.ldap.sdk.extensions
    PasswordModifyExtendedRequest
    PasswordModifyExtendedResult]))

(defn conn []
  *current-connection*)

(defn config
  ([]
     *current-connection-config*)
  ([kwd]
     (get *current-connection-config* kwd)))

(defn- raise-configuration-missing [& keys]
  (raise "Error: required keys [%s] not found in config: %s"
         (str/join "," keys) (config)))

;; NB: should validate configuration when (register-ldap) is called
(defn user-dn [username]
  (if-not (and (config :user-dn-suffix)
               (config :user-id-attr))
    (raise-configuration-missing :user-dn-suffix :user-id-attr)
    (format "%s=%s,%s" (config :user-id-attr) username (config :user-dn-suffix))))

(defmacro defop [name args & body]
  `(defn ~name ~args
     (if-not (conn)
       (raise "Error: %s must be called in context of the with-ldap macro or by manually binding *current-connection*" ~name)
       (do ~@body))))

(defn make-attribute [k v]
  (Attribute. (name k) v))

(defn make-entry [dn attrs]
  (Entry. dn (map make-attribute (keys attrs) (vals attrs))))

(defop bind [dn password]
  (.bind (conn) dn password))

(defop add [dn attrs]
  (.add (conn) (make-entry dn attrs)))

(defop delete [dn]
  (.delete (conn) (DeleteRequest. dn)))

(defop password-modify [dn old-pw new-pw]
  (.processExtendedOperation
   (conn)
   (PasswordModifyExtendedRequest. dn old-pw new-pw)))

(defop add-user [username password fname lname]
  (add (user-dn username)
       {:objectClass  "inetOrgPerson"
        :userPassword password
        :uid          username
        :cn           (format "%s %s" fname lname)
        :sn           lname}))

(defop delete-user [username]
  (delete (user-dn username)))

;; NB: should make this more flexible
(defn dn [val]
  (DN. val))
