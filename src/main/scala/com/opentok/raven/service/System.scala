package com.opentok.raven.service

import akka.actor.ActorSystem

/**
 * Interface containing the [[akka.actor.ActorSystem]]
 */
trait System {
  implicit val system: ActorSystem
}

/**
 * It implements ``System`` by instantiating the ActorSystem and registering
 * the JVM termination hook to shutdown the ActorSystem on JVM exit.
 */
trait AkkaSystem extends System {
  implicit val system = ActorSystem("raven")

  sys.addShutdownHook(system.terminate())

}
