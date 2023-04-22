package feedmail

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

import java.net.URL
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Config {
  private lazy val underlying = ConfigFactory.load()

  // TODO option to remove old entries (resetAbsentEntries)
  case class Feed(url: URL, interval: FiniteDuration, executeOnStartup: Boolean, clearHistoryOnStartup: Boolean)

  private implicit val feedValueReader: ValueReader[Feed] = ValueReader.relative { feedConfig =>
    Feed(
      url = feedConfig.as[URL]("url"),
      interval = feedConfig.as[FiniteDuration]("interval"),
      executeOnStartup = feedConfig.getOrElse[Boolean]("execute-on-startup", false),
      clearHistoryOnStartup = feedConfig.getOrElse[Boolean]("clear-history-on-startup", false)
    )
  }

  lazy val feeds: Map[String, Feed] = underlying.as[Map[String, Feed]]("feeds")

  case class Email(
      to: Seq[String],
      from: String,
      smtpHost: String,
      smtpPort: Int,
      smtpUsername: Option[String],
      smtpPassword: Option[String]
  )

  private implicit val emailValueReader: ValueReader[Email] = ValueReader.relative { emailConfig =>
    Email(
      to = Try(emailConfig.as[String]("to")).map(Seq(_)) getOrElse emailConfig.as[Seq[String]]("to"),
      from = emailConfig.as[String]("from"),
      smtpHost = emailConfig.as[String]("smtp-host"),
      smtpPort = emailConfig.as[Int]("smtp-port"),
      smtpUsername = emailConfig.getAs[String]("smtp-username"),
      smtpPassword = emailConfig.getAs[String]("smtp-password")
    )
  }

  lazy val email: Email = underlying.as[Email]("email")
}
