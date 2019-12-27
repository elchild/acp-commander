FROM debian:stretch-slim

COPY server.tar.gz /

COPY entry.sh /

RUN apt-get update; apt-get -y install ccrypt

RUN chmod +x /entry.sh

ENTRYPOINT ["/entry.sh"]
