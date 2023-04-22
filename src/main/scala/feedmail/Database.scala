package feedmail

import feedmail.schema.RecordedEntries
import slick.jdbc.JdbcType
import slick.lifted.TableQuery

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

object Database {
  val api = slick.jdbc.H2Profile.api

  import api.stringColumnType
  implicit val uriColumnMapper: JdbcType[URI] =
    api.MappedColumnType.base[URI, String](_.toString, URI.create)

  val RecordedEntriesTable: TableQuery[RecordedEntries.RecordedEntriesTable] = schema.RecordedEntries.RecordedEntriesTable

  private lazy val db = api.Database.forConfig("database")

  private lazy val initializedDb = db.run(schema.RecordedEntries.initialize)

  implicit class EnhancedDBIO[T](dbio: api.DBIO[T]) {
    def execute(implicit ec: ExecutionContext): Future[T] = initializedDb.flatMap(_ => db.run(dbio))
  }
}
