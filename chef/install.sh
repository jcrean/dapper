#!/bin/bash

#
# Taken directly from http://opinionated-programmer.com/2011/06/chef-solo-tutorial-managing-a-single-server-with-chef/
#

CHEF_BINARY=/usr/bin/chef-solo

# Are we on a vanilla system?
if ! test -f "$CHEF_BINARY"; then
    export DEBIAN_FRONTEND=noninteractive
    sudo yum update -y &&
    # Install Ruby and Chef
    sudo yum install -y ruby rubygems ruby-devel make gcc &&
    sudo gem install --no-rdoc --no-ri chef --version 0.10.0
fi &&

$CHEF_BINARY -c solo.rb -j ldap02.json
