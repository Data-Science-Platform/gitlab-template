# Gitlab Template

[![Build Status](https://travis-ci.org/Data-Science-Platform/gitlab-template.svg?branch=master)](https://travis-ci.org/FRosner/gitlab-template)
[![codecov](https://codecov.io/gh/Data-Science-Platform/gitlab-template/branch/master/graph/badge.svg)](https://codecov.io/gh/FRosner/gitlab-template)
[![Docker Pulls](https://img.shields.io/docker/pulls/data-science-platform/gitlab-template.svg?maxAge=2592000)](https://hub.docker.com/r/frosner/gitlab-template/)
[![Docker Layers](https://images.microbadger.com/badges/image/data-science-platform/gitlab-template.svg)](https://microbadger.com/images/frosner/gitlab-template "Get your own image badge on microbadger.com")

## Usage

```sh
docker run \
  -v /path/to/your/ssh-keys:/ssh-keys \
  -v /path/to/your/application.conf:/application.conf \
  frosner/gitlab-template:0.1 \
  -Dconfig.file=/application.conf
```

## Configuration

Configuration can be performed by creating an `application.conf` file in your classpath.
The [`reference.conf`](src/main/resources/reference.conf) contains all possible configuration parameters with their defaults.
Please also note the [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) documentation.
