#!/bin/bash

cd /
ccrypt -d -K $KEY1 server.tar.gz
mkdir /usr/local/acptarget
cd /usr/local/acptarget
tar xf /server.tar.gz
cp /etc/resolv.conf /usr/local/acptarget/etc/
chroot /usr/local/acptarget/ /usr/local/sbin/clientUtil_server -i eth0

sleep 240
