#!/bin/bash

#
# This was taken mostly from http://opinionated-programmer.com/2011/06/chef-solo-tutorial-managing-a-single-server-with-chef/. 
#

LDAP01=ec2-50-19-187-215.compute-1.amazonaws.com
LDAP02=ec2-107-20-50-80.compute-1.amazonaws.com

if [ -z "$1" ]; then
  echo "Supply host to provision"
  exit -1
fi

case "$1" in
    ldap01)
        echo "Provisioning ldap01..."
        HOST=$LDAP01
        ;;
    ldap02)
        echo "Provisioning ldap02..."
        HOST=$LDAP02
        ;;
    *)
        echo "Unsupported host ID: $1"
        exit 1
esac

rsync -avz . ec2-user@ec2-107-20-50-80.compute-1.amazonaws.com:/home/ec2-user/chef/

ssh -t ec2-user@ec2-107-20-50-80.compute-1.amazonaws.com 'cd chef; sudo ./install.sh;'

# tar cj . | ssh -o 'StrictHostKeyChecking no' ec2-user@"$HOST" '
# rm -rf ~/chef &&
# mkdir ~/chef &&
# cd ~/chef &&
# tar xj &&
# bash install.sh'
