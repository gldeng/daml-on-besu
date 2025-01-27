// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.besu.daml.rpc

import java.nio.file.Paths
import java.time.Duration

import akka.stream.Materializer

import com.daml.ledger.api.auth.{AuthServiceJWT, AuthService, AuthServiceWildcard}
import com.daml.jwt.{RSA256Verifier,JwksVerifier}
import com.daml.ledger.participant.state.kvutils.api.{KeyValueLedger, KeyValueParticipantState}
import com.daml.ledger.participant.state.kvutils.app.{Config, LedgerFactory, ParticipantConfig, Runner}
import com.daml.ledger.participant.state.v1.{Configuration, TimeModel}
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext
import com.daml.platform.configuration.LedgerConfiguration
import com.daml.resources.{ProgramResource, ResourceOwner}

import org.slf4j.event.Level
import org.web3j.protocol.http.HttpService

import scala.util.Try
import scopt.OptionParser

object Main {
  def main(args: Array[String]): Unit = {
    val factory = new JsonRpcLedgerFactory()
    val runner = new Runner("JsonRPC Ledger", factory).owner(args)
    new ProgramResource(runner).run()
  }

  class JsonRpcLedgerFactory()
      extends LedgerFactory[KeyValueParticipantState, ExtraConfig] {

    override final def readWriteServiceOwner(
        config: Config[ExtraConfig],
        participantConfig: ParticipantConfig,
        engine: Engine,
    )(
        implicit materializer: Materializer,
        logCtx: LoggingContext,
    ): ResourceOwner[KeyValueParticipantState] = {
      LogUtils.setRootLogLevel(config.extra.logLevel)
      LogUtils.setLogLevel(classOf[HttpService], Level.INFO.name())
      LogUtils.setLogLevel("org.flywaydb.core.internal", Level.INFO.name())

      for {
        readerWriter <- owner(config, participantConfig, engine)
      } yield
        new KeyValueParticipantState(
          readerWriter,
          readerWriter,
          createMetrics(participantConfig, config),
        )
    }

    def owner(config: Config[ExtraConfig], participantConfig: ParticipantConfig, engine: Engine)(
        implicit materializer: Materializer,
        logCtx: LoggingContext,
    ): ResourceOwner[KeyValueLedger] = {
      new JsonRpcReaderWriter.Owner(participantConfig.participantId,
        config.extra.privateKeyFile,
        config.extra.jsonRpcUrl,
        config.ledgerId)
    }

    override def ledgerConfig(config: Config[ExtraConfig]): LedgerConfiguration =
      LedgerConfiguration(
        initialConfiguration = Configuration(
          generation = 1,
          timeModel = TimeModel(
            avgTransactionLatency = Duration.ofSeconds(1L),
            minSkew = Duration.ofSeconds(40L),
            maxSkew = Duration.ofSeconds(80L),
          ).get,
          maxDeduplicationTime = Duration.ofDays(1),
        ),
        initialConfigurationSubmitDelay = Duration.ofSeconds(5),
        configurationLoadTimeout = Duration.ofSeconds(10),
      )

    override def authService(config: Config[ExtraConfig]): AuthService = {
        config.extra.authType match {
          case "none" => AuthServiceWildcard
          case "rsa256" => {
            val verifier = RSA256Verifier
              .fromCrtFile(config.extra.secret)
              .valueOr(err => sys.error(s"Failed to create RSA256 verifier for: $err"))
            AuthServiceJWT(verifier)
          }
          case "jwks" => {
            val verifier = JwksVerifier(config.extra.jwksUrl)
            AuthServiceJWT(verifier)
          }
        }
    }

    override val defaultExtraConfig: ExtraConfig = ExtraConfig.default

    private def validatePath(path: String, message: String): Either[String, Unit] = {
      val valid = Try(Paths.get(path).toFile.canRead).getOrElse(false)
      if (valid) Right(()) else Left(message)
    }

    override final def extraConfigParser(parser: OptionParser[Config[ExtraConfig]]): Unit = {
      parser
        .opt[String]("json-rpc-url")
        .optional()
        .text("URL of the daml-on-besu json-rpc to connect to")
        .action {
          case (v, config) => config.copy(extra = config.extra.copy(jsonRpcUrl = v))
        }
      parser
        .opt[String]("logging")
        .optional()
        .text("Logging level, one of error|warn|info|debug|trace")
        .action {
          case (l, config) => config.copy(extra = config.extra.copy(logLevel = l.toLowerCase()))
        }
      parser
        .opt[String]("auth-jwt-rs256-crt")
        .optional()
        .validate(
          validatePath(_, "The certificate file specified via --auth-jwt-rs256-crt does not exist")
        )
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given X509 certificate file (.crt)"
        )
        .action {
          case (v, config) => {
            config.copy(
              extra = config.extra.copy(
                secret = v,
                authType = "rsa256"
              )
            )
          }
        }
      parser
        .opt[String]("auth-jwt-rs256-jwks")
        .optional()
        .validate(v => Either.cond(v.length > 0, (), "JWK server URL must be a non-empty string"))
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given JWKS URL"
        )
        .action {
          case (v, config) => {
            config.copy(
              extra = config.extra.copy(
                jwksUrl = v,
                authType = "jwks"
              )
            )
          }
        }
      parser
        .opt[String]("private-key-file")
        .text("Absolute path of file containing Besu node private key")
        .action {
          case (f, config) => config.copy(extra = config.extra.copy(privateKeyFile = f))
        }
      ()
    }
  }
}
