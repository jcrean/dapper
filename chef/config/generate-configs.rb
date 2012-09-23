require 'rubygems'
require 'yaml'
require 'json'

config = YAML.load_file(File.join(File.dirname(__FILE__),'deploy.yml'))

config.each do |role,role_conf|
  puts "role: #{role}, host: #{role_conf['host']}, base_conf: #{role_conf['base_conf']}"
  base = JSON.parse(File.read(File.join(File.dirname(__FILE__),'..',role_conf['base_conf'])))
  base["ldap"]["replication"]["server_id"] = role_conf["server_id"]
  JSON.pretty_unparse(base)
end
