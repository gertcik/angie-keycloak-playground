# Angie Console Light: prebuilt static UI (dist/console) is published as a tarball.
# https://angie.software/en/console/  |  demo: https://console.angie.software/
FROM alpine:3.20 AS console-light
ARG CONSOLE_VERSION=1.9.0
RUN apk add --no-cache ca-certificates \
 && wget -qO- "https://download.angie.software/files/angie-console-light/angie-console-light-${CONSOLE_VERSION}.tar.gz" \
    | tar -xz -C /tmp \
 && mkdir -p /console \
 && cp -a "/tmp/angie-console-light-${CONSOLE_VERSION}/html/." /console/

FROM docker.angie.software/angie:latest

# Console Light static files; wired up by /console/ in conf.d/default.conf
COPY --from=console-light /console /usr/share/angie-console-light/html

EXPOSE 82

CMD ["angie", "-g", "daemon off;"]
