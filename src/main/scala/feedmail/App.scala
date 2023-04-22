package feedmail

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

object App {
  def main(args: Array[String]): Unit = {
    ActorSystem(App(), "App")
  }

  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    if (Config.feeds.nonEmpty) {
      context.log.info(s"Initializing ${Config.feeds.size} feeds")
      Config.feeds.foreach { case (feedName, feedConfig) =>
        val actorName = feedName.replaceAll("\\W+", "-")
        val feedActor = context.spawn(FeedActor(feedName, feedConfig), actorName)
        context.watch(feedActor)
      }
      Behaviors.empty
    } else {
      context.log.info("No feeds configured. Terminating")
      Behaviors.stopped
    }
  }
}
