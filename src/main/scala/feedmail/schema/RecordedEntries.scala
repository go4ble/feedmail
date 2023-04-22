package feedmail.schema

import feedmail.Database.uriColumnMapper
import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape

import java.net.URI
import java.time.LocalDateTime

object RecordedEntries {
  case class RecordedEntriesRow(feedName: String, uri: URI, createdAt: LocalDateTime)

  class RecordedEntriesTable(tag: Tag) extends Table[RecordedEntriesRow](tag, "RECORDED_ENTRIES") {
    def feedName: Rep[String] = column("FEED_NAME")
    def uri: Rep[URI] = column("URI")
    def createdAt: Rep[LocalDateTime] = column("CREATED_AT")

    def * : ProvenShape[RecordedEntriesRow] = (feedName, uri, createdAt) <> (RecordedEntriesRow.tupled, RecordedEntriesRow.unapply)
  }

  val RecordedEntriesTable = TableQuery[RecordedEntriesTable]

  val initialize: DBIOAction[Unit, NoStream, Effect] = DBIO.seq(
    sql"""
      CREATE TABLE IF NOT EXISTS recorded_entries (
        FEED_NAME VARCHAR(255) NOT NULL,
        URI VARCHAR(2048) NOT NULL,
        CREATED_AT TIMESTAMP DEFAULT now()
      )
    """.asUpdate,
    sql"""
      CREATE INDEX IF NOT EXISTS RECORDED_ENTRIES_ENTRY_IDX
      ON RECORDED_ENTRIES (FEED_NAME, URI)
    """.asUpdate
  )
}