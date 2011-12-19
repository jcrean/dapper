#!/bin/bash

#
# Taken directly from http://opinionated-programmer.com/2011/06/chef-solo-tutorial-managing-a-single-server-with-chef/
#

CHEF_BINARY=/usr/local/bin/chef-solo
ROLE=${1:-'master'}

# Are we on a vanilla system?
if ! test -f "$CHEF_BINARY"; then
   export DEBIAN_FRONTEND=noninteractive
   sudo aptitude update -y &&
   # Install Ruby and Chef
   sudo aptitude install -y ruby rubygems ruby-dev make gcc &&
   sudo gem install --no-rdoc --no-ri chef --version 0.10.0
fi &&

$CHEF_BINARY -c solo.rb -j ldap-$ROLE.json
