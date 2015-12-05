package actor

import akka.actor.{Actor, ActorLogging}

import scala.collection.mutable

/**
  * Created by tomas on 04-12-15.
  */

object FileLockingActor {
  case class Lock(file: String)
  case class Unlock(file: String)
}

class FileLockingActor extends Actor with ActorLogging {

  import actor.FileLockingActor._

  var lockedFiles = mutable.Seq[String]()

  def receive = {
    case Lock(file) =>
      if (lockedFiles.contains(file)) {
        lockedFiles ++= Seq(file)
        sender ! true
      } else {
        sender ! false
      }

      println("LOCK Still locked: " + lockedFiles)

    case Unlock(file) =>
      lockedFiles = lockedFiles.filter(_ != file)
      println("UNLOCK Still locked: " + lockedFiles)
  }

}
