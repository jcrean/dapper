#!/bin/bash

sudo slapd
sudo ldapadd -x -w <%= node[:ldap][:root_pw] %> -D <%= node[:ldap][:root_dn] %> -f /etc/openldap/users.ldif -c
