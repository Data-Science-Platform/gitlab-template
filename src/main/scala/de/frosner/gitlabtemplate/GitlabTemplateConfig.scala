package de.frosner.gitlabtemplate

import java.nio.file.Path

import scala.concurrent.duration.FiniteDuration

case class GitlabTemplateConfig(source: SourceConfig,
                                sink: SinkConfig,
                                dryRun: Boolean,
                                renderFrequency: FiniteDuration,
                                timeout: FiniteDuration)

case class SourceConfig(gitlab: GitlabConfig, technicalUsersKeys: TechnicalUsersKeysConfig)

case class TechnicalUsersKeysConfig(url: String,
                                    httpBasicAuth: HttpBasicAuthConfig,
                                    privateTokenAuth: PrivateTokenAuthConfig)

case class HttpBasicAuthConfig(enabled: Boolean, username: String, password: String)

case class PrivateTokenAuthConfig(enabled: Boolean, token: String)

case class SinkConfig(filesystem: FilesystemConfig)

case class GitlabConfig(onlyActiveUsers: Boolean, privateToken: String, url: String, perPage: Int, throttleDuration: Int)

case class FilesystemConfig(path: Path, publicKeysFile: String, createEmptyKeyFile: Boolean)
