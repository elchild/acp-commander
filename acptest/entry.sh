#!/bin/bash

cd /
ccrypt -d -K ${key1} server.tar.gz
echo $?
mkdir /usr/local/acptarget
cd /usr/local/acptarget
tar xf /server.tar.gz
cp /etc/resolv.conf /usr/local/acptarget/etc/
chroot /usr/local/acptarget/ /usr/local/sbin/clientUtil_server -i eth0
echo $?
sleep 60
