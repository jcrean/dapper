["openldap", "openldap-clients", "openldap-servers"].each do |p|
  package p do
    action :install
  end
end

group "ldap" do
  action :create
  group_name "ldap"
end

user "ldap" do
  action :create
  comment "user that will run ldap"
  username "ldap"
  home "/home/ldap"
end

directory "/home/ldap/bin" do
  owner "ldap"
  group "ldap"
  mode "0755"
  action :create
  recursive true
end

template "/etc/openldap/slapd.d/cn=config/olcDatabase={1}bdb.ldif" do
  source "bdb.ldif"
  owner "ldap"
  group "ldap"
end

template "/etc/openldap/users.ldif" do
  source "users.ldif"
  owner "ldap"
  group "ldap"
end


template "/home/ldap/bin/base-install.sh" do
  source "base-install.sh"
  owner "ldap"
  group "ldap"
  mode "0700"
end

