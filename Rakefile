require 'rubygems'
require 'json'
require 'net/http'
require 'net/ssh'
require 'uri'


class EC2Configurator
  attr_accessor :config

  def initialize(config_file)
    @config = eval(File.read(config_file))
    @instances = {}
    @ami_info = {}
  end

  def await_ssh hostname,username
    max_retries = 10
    current_retries = 0
    sleep_time = 3
    ssh_success = false
    while !ssh_success && current_retries < max_retries
      begin
        puts "Attempting ssh connection #{hostname} as #{username}"
        Net::SSH.start(hostname,username,:keys=>[ENV['EC2_SSH_KEYFILE']]) do |ssh|
          puts "Connect success"
          ssh_success = true
        end
      rescue Exception => e
        puts "Still awaiting sshd..(last error was: #{e.message})"
        current_retries += 1
        sleep sleep_time
      end
    end
    ssh_success
  end

  def await_server_start instance_id, name, server_conf
    status = 'not running'
    max_retries = 5
    retries = max_retries
    hostname = nil
    while status != 'running' && retries > 0
      sleep 3
      puts "Waiting for instance to come up..."
      res = %x[ec2-describe-instances "#{instance_id}"]
      status = res.split("\n")[1].split("\t")[5]
      if status == 'running'
        hostname = res.split("\n")[1].split("\t")[3]
        @instances[instance_id] ||= {}
        @instances[instance_id][:hostname] = hostname
        @instances[instance_id][:config] = server_conf
        @instances[instance_id][:name] = name
      end
      puts " - status is #{status}"
      retries -= 1
    end

    if status != 'running'
      puts "After #{max_retries} attempts, failed to find running instance for EC2 instance ID: #{instance_id}"
      return :startup_failed
    end

    puts "Instance hostname: #{@instances[instance_id][:hostname]}"
    return await_ssh(hostname,ENV['EC2_USERNAME'])
  end

  def tag_instance instance_id, tags
    tag_args = tags.inject(["ec2-create-tags", instance_id]) { |res,pair| res.concat ["--tag", "#{pair[0]}=#{pair[1]}"] }
    system *tag_args
  end

  def chef_config_file server_name,server_role
    File.join(File.dirname(__FILE__), 'chef', "#{server_name}-#{server_role}.json")
  end

  def ensure_valid_config config=@config
    domain = config[:ldap][:domain] || ''
    domain_parts = domain.split('.')

    if !config[:ldap][:suffix]
      unless domain
        raise "Invalid configuration, must provide either :domain or :suffix"
      end
      config[:ldap][:suffix] = domain_parts.map { |d| "dc=#{d}" }.join(',')
    end

    if !config[:ldap][:root_dc]
      dc = domain_parts[0]
      unless dc
        raise "Invalid configuration, unable to determine root_dc"
      end
      config[:ldap][:root_dc] = dc
    end

    if !config[:ldap][:root_dn]
      unless config[:ldap][:root_cn]
        raise "Invalid configuration, must supply either root_dn or root_cn"
      end

      config[:ldap][:root_dn] = "cn=#{config[:ldap][:root_cn]},#{config[:ldap][:suffix]}"
    end

    unless config[:recipe]
      raise "Must supply recipe name to be used for provisioning"
    end

    config[:run_list] = [ "recipe[#{config[:recipe]}::default]" ]

    config
  end

  def generate_chef_configs config=@config
    puts "generating chef configs"
    config[:servers].each do |name,conf|
      puts " - server: #{name}, role: #{conf[:role]}"
      role = conf[:role]
      puts " --- Ensuring valid config: #{config.inspect}"
      chef_config = ensure_valid_config config
      chef_config[:ldap][:replication] = conf[:replication]
      server_id = 1
      if conf[:replication][:provider]
        chef_config[:ldap][:replication][:provider] = @instances.select {|k,v| v[:name] == conf[:replication][:provider]}.first[1][:hostname]
      end
      chef_config[:ldap][:replication][:servers] = @instances.inject([]) do |accum,instance_config|
        instance_id,instance_conf = instance_config
        accum << {:id => server_id, :host => instance_conf[:hostname]}
        server_id += 1
        accum
      end
      File.open(chef_config_file(name,role), 'w') do |f|
        chef_config.delete(:servers)
        #f.puts chef_config.delete(:servers).to_json
        f.puts JSON.pretty_generate(chef_config)
      end
    end
    puts "Finished generating chef configs, time to deploy"
  end

  def get_latest_ubuntu_ami_info suite_name
    url = URI.parse("http://cloud-images.ubuntu.com/query/#{suite_name}/server/released.current.txt")
    req = Net::HTTP::Get.new(url.path)
    resp = Net::HTTP.start(url.host,url.port) { |http| http.request(req) }
    info = {}
    resp.body.split("\n").each do |line|
      fields = line.split("\t")
      root_store,arch,region,ami = fields[4..7]
      info[region] ||= {}
      info[region][arch] ||= {}
      info[region][arch][root_store] = ami
    end
    info
  end

  def get_ami_id ami_conf
    ubuntu_vers = ami_conf[:ubuntu_version]
    @ami_info[ubuntu_vers] ||= get_latest_ubuntu_ami_info(ubuntu_vers)
    region     = ami_conf[:region]
    arch       = ami_conf[:arch]
    root_store = ami_conf[:root_store]
    ami_id     = @ami_info[ubuntu_vers][region][arch][root_store]
    puts "Ubuntu AMI[#{ubuntu_vers}]: #{region}:#{arch}:#{root_store}: #{ami_id}"
    ami_id
  end

  def remote_provision host, user, server_name, server_role
    puts "Connecting to remote host #{host} as #{user}"
    res = ''
    Net::SSH.start(host, user, :keys => [ENV['EC2_SSH_KEYFILE']]) do |ssh|
      system %Q|rsync -avz --delete --rsh="ssh -i #{ENV['EC2_SSH_KEYFILE']} -x -l #{user}" chef/ #{host}:provision/|
      res = ssh.exec! "cd provision && ./install.sh #{server_name}-#{server_role}.json"
    end

    puts "Got res: #{res}"

#       puts "rsync'ing chef data to #{user}@#{hostname}"
#       chef_config = File.basename(chef_config_file(name,role))
#       cmd =  [File.join(File.dirname(__FILE__),'chef','deploy.sh'), chef_config, hostname]
#       puts "run the following command to provision #{name}:"
#       puts "  #{cmd.join(' ')}"
#       #system *cmd
  end

  def get_instance_id name 
    res = %x[ec2-describe-tags --filter key=name --filter value=#{name}]
    data = res.match(/.*instance\t(.*?)\t.*/)
    if data
      data[1]
    else
      nil
    end
  end

  def provision_hosts config=@config
    config[:servers].each do |name,server_conf|
      instance_id = get_instance_id name
      puts "Current instance ID?: #{instance_id}"
      if !instance_id
        server_conf[:ami][:id] ||= get_ami_id(server_conf[:ami])
        puts "Provisioning EC2 instance: #{name}, role=#{server_conf[:role]}"
        type = server_conf[:ami][:type]
        region = server_conf[:ami][:region]
        res = %x[ec2-run-instances "#{server_conf[:ami][:id]}"  --instance-type "#{type}" --region "#{region}" --key "#{ENV['EC2_KEYPAIR']}" --group ldap-server]
        instance_id = res.match(/.*INSTANCE\t(.*?)\t.*/)[1]
        puts "Provisioned instance ID: #{instance_id}"
        tag_instance instance_id, {"name" => name, "ldap-role" => server_conf[:role]}
        # #exit
      else
        puts "Already provisioned #{name}"
      end
      success = await_server_start instance_id, name, server_conf
      unless success
        raise "server not up yet, giving up"
      end
    end
    generate_chef_configs
    puts "Instances: #{@instances.inspect}"
    @instances.each do |instance_id,conf|
      hostname = conf[:hostname]
      user     = ENV['EC2_USERNAME']
      name     = conf[:name]
      role     = conf[:config][:role]
      remote_provision hostname, user, name, role
    end
  end

end

namespace :dapper do
  namespace :chef do
    desc "Generate JSON config for chef (default config file is config/deploy.rb)"
    task :generate_configs, :conf_file do |t,args|
      file = args[:conf_file] || File.join(File.dirname(__FILE__), 'config', 'deploy.rb')
      puts "Will use config file: #{file}"
      configurator = EC2Configurator.new(file)
      configurator.generate_chef_configs
    end

    desc "Runs recipes on remote machine(s)"
    task :remote_deploy do |t,args|
      file = args[:conf_file] || File.join(File.dirname(__FILE__), 'config', 'deploy.rb')
      puts "Will use config file: #{file}"
      configurator = EC2Configurator.new(file)
      configurator.remote_provision
    end
  end

  namespace :ec2 do
    desc "Provision EC2 LDAP servers according to spec (uses config/deploy.rb by default)"
    task :provision, :conf_file do |t,args|
      file = args[:conf_file] || File.join(File.dirname(__FILE__), 'config', 'deploy.rb')
      puts "Will use config file: #{file}"
      configurator = EC2Configurator.new(file)
      configurator.provision_hosts
    end

    desc "Terminate all running EC2 instances"
    task :terminate_all do
      system File.join(File.dirname(__FILE__), "bin", "terminate-all")
    end

    namespace :ubuntu do
      desc "Get latest AMI info for Ubuntu cloud images"
      task :ami_info do
        file = File.join(File.dirname(__FILE__), 'config', 'deploy.rb')
        puts EC2Configurator.new(file).get_latest_ubuntu_ami_info('oneiric').inspect
      end
    end
  end
end
