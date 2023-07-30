package feedmail

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.rometools.rome.feed.synd.{SyndEntry, SyndFeed}
import com.rometools.rome.io.SyndFeedInput
import sttp.client3.HttpClientFutureBackend
import sttp.model.MediaType

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{URI, URL}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import scala.xml.InputSource

object FeedActor {
  import Database._
  import api._

  sealed trait FeedActorMessage
  private final case object InitializeCheck extends FeedActorMessage
  private final case class ProcessFeed(feed: Try[SyndFeed]) extends FeedActorMessage
  private object ProcessFeed {
    def apply(inputStream: InputStream): ProcessFeed = ProcessFeed(Success(new SyndFeedInput().build(new InputSource(inputStream))))
  }
  private final case object Reschedule extends FeedActorMessage

  def apply(name: String, config: Config.Feed): Behavior[FeedActorMessage] = Behaviors.setup { context =>
    if (config.clearHistoryOnStartup) {
      Await.result(clearAllEntries(name)(context.executionContext), 5.seconds)
    }

    context.self.tell(if (config.executeOnStartup) InitializeCheck else Reschedule)

    defaultBehavior(name, config)
  }

  private def defaultBehavior(name: String, config: Config.Feed, retries: Int = 2): Behavior[FeedActorMessage] = Behaviors.withTimers { timer =>
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext

      Behaviors.receiveMessage {
        case InitializeCheck =>
          context.log.debug(s"checking $name at ${config.url}")
          context.pipeToSelf(getRemoteFeed(config.url))(ProcessFeed(_))
          Behaviors.same
        case ProcessFeed(Success(feed)) =>
          val entries = feed.getEntries.asScala.toSeq
          val entryUris = entries.map(_.uri).toSet
          val doneF = for {
            newEntryUris <- filterNewEntries(name, entryUris)
            newEntries = entries.filter(newEntryUris contains _.uri)
            _ <- if (newEntries.nonEmpty) FeedEmail(name, newEntries).send else Future.successful(())
            _ <- addNewEntries(name, newEntryUris)
            _ <- if (config.resetAbsentEntries) resetAbsentEntries(name, entryUris) else Future.successful(0)
          } yield ()
          context.pipeToSelf(doneF)(assumingSuccess(_ => Reschedule))
          defaultBehavior(name, config)
        case ProcessFeed(Failure(error)) =>
          context.log.error(s"Failed to fetch feed $name, retries ($retries)", error)
          if (retries > 0) {
            timer.startSingleTimer(InitializeCheck, 5.seconds)
            defaultBehavior(name, config, retries - 1)
          } else {
            context.self.tell(Reschedule)
            defaultBehavior(name, config)
          }
        case Reschedule =>
          timer.startSingleTimer(InitializeCheck, config.interval)
          Behaviors.same
      }
    }
  }

  private implicit class EnhancedSyndEntry(syndEntry: SyndEntry) {
    def uri: URI = new URI(syndEntry.getUri)
  }

  private def filterNewEntries(feedName: String, uris: Set[URI])(implicit ec: ExecutionContext): Future[Set[URI]] =
    RecordedEntriesTable
      .filter(re => (re.feedName === feedName) && (re.uri inSet uris))
      .map(_.uri)
      .result
      .execute
      .map(uris -- _)

  private def addNewEntries(feedName: String, uris: Set[URI])(implicit ec: ExecutionContext): Future[Int] =
    if (uris.nonEmpty)
      RecordedEntriesTable
        .map(re => (re.feedName, re.uri))
        .++=(uris.map((feedName, _)))
        .execute
        .map(_.getOrElse(0))
    else
      Future.successful(0)

  private def resetAbsentEntries(feedName: String, uris: Set[URI])(implicit ec: ExecutionContext): Future[Int] =
    RecordedEntriesTable.filter(re => (re.feedName === feedName) && !(re.uri inSet uris)).delete.execute

  private def clearAllEntries(feedName: String)(implicit ec: ExecutionContext): Future[Int] =
    RecordedEntriesTable.filter(_.feedName === feedName).delete.execute

  private def assumingSuccess[T, U](fn: T => U)(attempt: Try[T]): U = attempt match {
    case Success(value)     => fn(value)
    case Failure(exception) => throw exception
  }

  private val AcceptedMediaTypes = Seq(
    MediaType.ApplicationXml,
    MediaType("application", "atom+xml")
  )

  private object ResponseWithFeed {
    def unapply(response: sttp.client3.Response[Either[String, String]]): Option[SyndFeed] = for {
      content <- response.body.toOption
      contentType <- response.contentType
      mediaType <- MediaType.parse(contentType).toOption
      if AcceptedMediaTypes.exists(_.equalsIgnoreParameters(mediaType))
    } yield new SyndFeedInput().build(new InputSource(new ByteArrayInputStream(content.getBytes)))
  }

  private val httpBackend = HttpClientFutureBackend()

  private def getRemoteFeed(url: URL)(implicit ec: ExecutionContext): Future[SyndFeed] = {
    import sttp.client3._
    import sttp.model.Uri
    basicRequest.get(Uri(url.toURI)).send(httpBackend).map {
      case ResponseWithFeed(feed) =>
        feed
      case r @ Response(Right(unexpectedContent), _, _, _, _, _) =>
        throw new Exception(s"unexpected http response content type (${r.contentType}): ${unexpectedContent.take(1000)}")
      case Response(Left(error), code, _, _, _, _) =>
        throw new Exception(s"unexpected http response (${code.code}): ${error.take(1000)}")
    }
  }
}
