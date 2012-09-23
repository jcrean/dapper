{ :ldap =>
  { :domain       => 'yourdomain.com',
    :organization => 'Some Company',
    :root_cn      => 'admin',
    :root_pw      => 'admin123',
    :config_pw    => 'config123',
    :users_ou     => 'users',
    :log_level    => 'stats' },

  :servers =>
  { :ldap1 =>
    { :role   => 'provider',
      :ami    => {
        :ubuntu_version => 'lucid',
        :type           => 't1.micro',
        :region         => 'us-east-1',
        :arch           => 'amd64',
        :root_store     => 'ebs'
      },

      :replication =>
      { :role            => 'provider',
        :sessionlog_size => 100,
        :checkpoint =>
        { :max_ops  => 1,
          :max_time => 1 }}},

   :ldap2 =>
    { :role   => 'consumer',
     :ami    => {
       :ubuntu_version => 'lucid',
       :type           => 't1.micro',
       :region         => 'us-east-1',
       :arch           => 'amd64',
       :root_store     => 'ebs'
     },

     :replication =>
     { :role            => 'consumer',
       :provider        => :ldap1,
       :sessionlog_size => 100,
       :checkpoint =>
       { :max_ops  => 100,
         :max_time => 10 }}}
#
#   :ldap3 =>
#    { :role   => 'master-mirror',
#      :ami    => {
#        :ubuntu_version => 'lucid',
#        :type           => 't1.micro',
#        :region         => 'us-east-1',
#        :arch           => 'amd64',
#        :root_store     => 'ebs'
#      },
#
#      :replication =>
#      { :role            => 'master-mirror',
#        :sessionlog_size => 100,
#        :checkpoint =>
#        { :max_ops  => 100,
#          :max_time => 10 }}}
  },

  :recipe => 'ldap-server'
}
