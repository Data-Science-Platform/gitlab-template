# Gitlab Template

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
