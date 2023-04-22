package feedmail

import com.rometools.rome.feed.synd.SyndEntry
import jakarta.mail.Message.RecipientType
import org.simplejavamail.api.email.Recipient
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

case class FeedEmail(name: String, entries: Seq[SyndEntry]) {
  import FeedEmail._

  private lazy val asTxt: String = "" // TODO

  private lazy val asHtml: String = html.FeedEmailTemplate(name, entries).toString()

  def send(implicit ec: ExecutionContext): Future[Unit] = {
    val email = EmailBuilder.startingBlank()
      .from(emailConfig.from)
      .to(emailConfig.to.map(new Recipient(null, _, RecipientType.TO)).asJava)
      .withSubject(s"[feedmail] - $name")
      .withPlainText(asTxt)
      .withHTMLText(asHtml)
      .buildEmail()
    mailer.sendMail(email).asScala.map(_ => ())
  }
}

object FeedEmail {
  private val mailer = MailerBuilder
    .withSMTPServerHost(emailConfig.smtpHost)
    .withSMTPServerPort(emailConfig.smtpPort)
    .withSMTPServerUsername(emailConfig.smtpUsername.orNull)
    .withSMTPServerPassword(emailConfig.smtpPassword.orNull)
    .async()
    .buildMailer()

  private lazy val emailConfig = Config.email
}
