package fixture

import akka.actor.{ActorRef, Actor, ActorSystem}
import akka.stream.ActorMaterializer
import akka.testkit.TestActorRef
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.EmailSupervisor

trait MockSystem extends com.opentok.raven.service.System {

  override implicit val system: ActorSystem = ActorSystem("hermes-test")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override val monitoringService, certifiedService, priorityService = TestActorRef(new Actor {
    var received: Any = 0

    override def receive: Receive = {
      case msg ⇒ sender() ! Receipt.success
        received = msg
    }
  })
}
