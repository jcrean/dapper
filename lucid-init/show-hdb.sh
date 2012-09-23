#!/bin/bash

set -e

sudo ldapsearch -LLL -Y EXTERNAL -H ldapi:/// -b cn=config '(objectClass=olcHdbConfig)'
