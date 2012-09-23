#!/bin/bash

set -e

MODULE=$1

sudo ldapsearch -LLL -Y EXTERNAL -H ldapi:/// -b 'cn=config' "(olcModuleLoad=$MODULE)" 2>&1 | grep -q 'dn:'
