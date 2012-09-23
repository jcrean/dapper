["slapd", "ldap-utils"].each do |p|
  package p do
    action :install
  end
end

template "/etc/ldap/db-config.ldif" do
  source "base-db.ldif"
  owner "root"
  group "root"
  mode "0700"
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
