#!/bin/bash

#
# This was taken mostly from http://opinionated-programmer.com/2011/06/chef-solo-tutorial-managing-a-single-server-with-chef/. 
#

if [ -z "$1" ]; then
  echo "Supply role to use for provisioning (master or slave)"
  exit -1
fi

ROLE=$1
HOST=${2:-localhost}

echo "Provisioning new $ROLE LDAP server at $HOST"

rsync -avz . ec2-user@$HOST:/home/ec2-user/chef/

echo "ssh -t ec2-user@$HOST 'cd chef; sudo ./install.sh $ROLE;'"
ssh -t ec2-user@$HOST "cd chef; sudo ./install.sh $ROLE"

