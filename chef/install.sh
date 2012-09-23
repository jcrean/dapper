#!/bin/bash

#
# Taken directly from http://opinionated-programmer.com/2011/06/chef-solo-tutorial-managing-a-single-server-with-chef/
#

CHEF_BINARY=/usr/bin/chef-solo
CONFIG=$1

# Are we on a vanilla system?
if ! test -f "$CHEF_BINARY"; then
   sudo aptitude update -y &&
   # Install Ruby and Chef
   sudo aptitude install -y ruby ruby-dev rdoc ri irb build-essential wget ssl-cert curl make gcc libopenssl-ruby &&
   cd /tmp &&
   curl -O http://production.cf.rubygems.org/rubygems/rubygems-1.8.10.tgz &&
   tar zxf rubygems-1.8.10.tgz && 
   cd rubygems-1.8.10 && 
   sudo ruby setup.rb --no-format-executable && 
   sudo gem install --no-rdoc --no-ri chef &&
   cd ~/provision
fi &&

sudo $CHEF_BINARY -c solo.rb -j $CONFIG
