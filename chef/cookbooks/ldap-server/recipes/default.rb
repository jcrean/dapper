["slapd", "ldap-utils"].each do |p|
  package p do
    action :install
  end
end

template "/etc/ldap/db.ldif" do
  source "db.ldif"
  owner "root"
  group "root"
end

template "/etc/ldap/base.ldif" do
  source "base.ldif"
  owner "root"
  group "root"
end

template "/etc/ldap/users.ldif" do
  source "users.ldif"
  owner "root"
  group "root"
end

template "/etc/ldap/base-install.sh" do
  source "base-install.sh"
  owner "root"
  group "root"
  mode "0700"
end


bash "install base ldap config" do
  user "root"
  cwd "/etc/ldap"
  code <<-EOH
  ./base-install.sh
  EOH
end
