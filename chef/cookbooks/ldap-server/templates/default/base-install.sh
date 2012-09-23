#!/bin/bash 

set -e

search-config() {
  BASE=${2:-'cn=config'}
  sudo ldapsearch -LLL -Y EXTERNAL -H ldapi:/// -b $BASE "($1)" | grep -q 'dn:'
}

add-module-list-entry() {
if ! search-config 'objectClass=olcModuleList'; then
  echo "adding cn=module entry"
  sudo ldapadd -Y EXTERNAL -H ldapi:/// <<LDAP
dn: cn=module,cn=config
objectClass: olcModuleList
olcModulePath: /usr/lib/ldap
cn: module
LDAP
fi
}

add-module() {
if ! search-config "olcModuleLoad=$1"; then
  echo "adding module: $1" 
  sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
dn: cn=module{0},cn=config
changetype: modify
add: olcModuleLoad
olcModuleLoad: $1
LDAP
else
  echo "$1 module already loaded"
fi
}

add-syncprov-module() {
  sudo ldapadd -Y EXTERNAL -H ldapi:/// <<LDAP
dn: cn=module,cn=config
objectClass: olcModuleList
cn: module
olcModulePath: /usr/lib/ldap
olcModuleLoad: syncprov.la
LDAP
}

# Set password for config user
sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
dn: olcDatabase={0}config,cn=config
changetype: modify
replace: olcRootPW
olcRootPW: <%= node[:ldap][:config_pw] %>
LDAP

# Ensure back_hdb module is loaded
# cn=module may not have been loaded yet
add-module-list-entry
add-module 'back_hdb'

# For some reason, add-module wasnt working for this... will need more investigation.. punting for now
if ! search-config 'olcModuleLoad=syncprov.la'; then
  add-syncprov-module
fi

# Add database config
if ! search-config '& (objectClass=olcDatabaseConfig) (olcSuffix=<%= node[:ldap][:suffix] %>)'; then
  sudo ldapadd -Y EXTERNAL -H ldapi:/// -f db-config.ldif
fi

# Set serverIDs used for replication and apply syncprov overlay
sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
dn: cn=config
changetype: modify
replace: olcServerID
<% node[:ldap][:replication][:servers].each do |s| %>
olcServerID: <%= s[:id] %> ldap://<%= s[:host] %>
<% end %>
LDAP

if ! search-config '& (objectClass=olcSyncProvConfig) (objectClass=olcOverlayConfig)'; then
sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
dn: olcOverlay=syncprov,olcDatabase={0}config,cn=config
changetype: add
objectClass: olcOverlayConfig
objectClass: olcSyncProvConfig
olcOverlay: syncprov
olcSpCheckpoint: <%= node[:ldap][:replication][:checkpoint][:max_ops] %> <%= node[:ldap][:replication][:checkpoint][:max_time] %>
olcSpSessionLog: <%= node[:ldap][:replication][:sessionlog_size] %>
LDAP
fi

# Get config databases replicating to each other
<% if node[:ldap][:replication][:role] != 'provider' %>
sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
dn: olcDatabase={0}config,cn=config
changetype: modify
replace: olcSyncRepl
<% if node[:ldap][:replication][:role] != 'consumer' %>
<% node[:ldap][:replication][:servers].each do |s| %>
olcSyncRepl: rid=<%= sprintf("%03d", s[:id]) %> provider=ldap://<%= s[:host] %> binddn="cn=config" bindmethod=simple
  credentials=<%= node[:ldap][:config_pw] %> searchbase="cn=config" type=refreshAndPersist
  retry="5 5 300 5" timeout=1
<% end %>
<% else %>
olcSyncRepl: rid=<%= sprintf("%03d", 123) %> 
             provider=ldap://<%= node[:ldap][:replication][:provider] %> 
             binddn="cn=config" 
             bindmethod=simple
             credentials=<%= node[:ldap][:config_pw] %> 
             searchbase="cn=config" 
             type=refreshOnly
             retry="5 5 300 5" 
             timeout=1 
             interval=00:00:00:10
<% end %>
<% end %>
<% if node[:ldap][:replication][:role] == 'master-mirror' %>
-
replace: olcMirrorMode
olcMirrorMode: TRUE
LDAP
<% end %>

# # Now get the backend databases replicating
# DB_DN=$(ldapsearch -Y EXTERNAL -H ldapi:/// -b 'cn=config' '(& (objectClass=olcHdbConfig) (olcSuffix=dc=yourdomain,dc=com))' | grep 'dn:' | cut -f2 -d' ')
# 
# if ! search-config 'olcOverlay=syncprov' $DB_DN; then
#   sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
# dn: olcOverlay=syncprov,$DB_DN
# changetype: add
# objectClass: olcOverlayConfig
# objectClass: olcSyncProvConfig
# olcOverlay: syncprov
# LDAP
# fi
# 
# sudo ldapmodify -Y EXTERNAL -H ldapi:/// <<LDAP
# dn: $DB_DN
# replace: olcSyncRepl
# <% node[:ldap][:replication][:servers].each do |s| %>
# olcSyncRepl: rid=<%= sprintf("%03d", (s[:id] + node[:ldap][:replication][:servers].size)) %> provider=ldap://<%= s[:host] %> binddn="<%= node[:ldap][:root_dn] %>" bindmethod=simple
#   credentials=<%= node[:ldap][:root_pw] %> searchbase="<%= node[:ldap][:suffix] %>" type=refreshOnly
#   interval=00:00:00:10 retry="5 5 300 5" timeout=1
# <% end %>
# -
# replace: olcMirrorMode
# olcMirrorMode: TRUE
# LDAP

