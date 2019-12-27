FROM debian:stretch-slim

COPY server.tar.gz /

COPY entry.sh /

RUN apt-get update; apt-get -y install ccrypt

RUN ccrypt -d -K ${{secrets.KEY1}} server.tar.gz

RUN mkdir /usr/local/acptarget

RUN cd /usr/local/acptarget; tar xf /server.tar.gz

RUN cp /etc/resolv.conf /usr/local/acptarget/etc/

RUN chmod +x /entry.sh

ENTRYPOINT ["/entry.sh"]
