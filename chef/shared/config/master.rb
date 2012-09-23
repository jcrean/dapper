{ :replication =>
  { :role            => 'master',
    :sessionlog_size => 100,
    :checkpoint =>
    { :max_ops  => 100,
      :max_time => 10 }},

  :run_list => [ 'recipe[ldap-server::default]' ]
}
