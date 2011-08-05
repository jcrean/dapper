["openldap", "openldap-clients", "openldap-servers"].each do |p|
  package p do
    action :install
  end
end

user "ldap" do
  comment "user that will run ldap"
  uid "1000"
  gid "ldap"
  home "/home/ldap"
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


directory "/home/ldap/bin" do
  owner "ldap"
  group "ldap"
  mode "0755"
  action :create
end

template "/home/ldap/bin/base-install.sh" do
  source "base-install.sh"
  owner "ec2-user"
  group "ec2-user"
end

