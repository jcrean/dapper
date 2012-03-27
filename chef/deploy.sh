#!/bin/bash

#
# This was taken mostly from http://opinionated-programmer.com/2011/06/chef-solo-tutorial-managing-a-single-server-with-chef/.
#

if [ -z "$1" ]; then
  echo "Supply role to use for provisioning (master or slave)"
  exit -1
fi

ROLE=$1
HOST=$2
USER=${EC2_USERNAME:-ec2-user}

echo "Provisioning new $ROLE LDAP server at $HOST"
echo "Will connect to $HOST as user $USER"

DIR="$( cd "$( dirname "$0" )" && pwd )"
rsync -avz -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" $DIR/* $USER@$HOST:/home/$USER/chef/

echo "ssh -t $USER@$HOST 'cd chef; sudo ./install.sh $ROLE;'"
ssh -t $USER@$HOST "cd chef; sudo ./install.sh $ROLE"
