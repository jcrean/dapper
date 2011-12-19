#!/bin/bash 

set -e

sudo ldapadd -Y EXTERNAL -H ldapi:/// -f db.ldif
sudo ldapadd -x -w <%= node[:ldap][:root_pw] %> -D <%= node[:ldap][:root_dn] %> -f /etc/ldap/base.ldif -c
sudo ldapadd -x -w <%= node[:ldap][:root_pw] %> -D <%= node[:ldap][:root_dn] %> -f /etc/ldap/users.ldif -c
