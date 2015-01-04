package colossus.examples

import colossus.IOSystem
import colossus.core._
import colossus.protocols.websocket.WebSocketHandler

/*
 * The controller layer adds generalized message processing to a connection.  A
 * Controller provides the ability to decode incoming data in to messages and
 * encode output messages.  There is no coupling between input and output
 * messages.  The service layer extends this layer to add request/response
 * semantics.
 *
 * This example is a simple chat server built on the controller layer.  This example is largely
 * a motivator for cleaning up the controller API.  It should become simpler as
 * we improve the interface.
 */




class DemoHandler extends WebSocketHandler {
  def onMessage(data: String): Unit = {
    println(data)
  }
  def onClose(data: String): Unit = {
    println("close")
  }
  def onPing(data: String): Unit = {
    println("ping")
  }
  def onPong(data: String): Unit = {
    println("pong")
  }
  def writeMessage(data: String): Unit = {}
  def receivedMessage(message: Any,sender: akka.actor.ActorRef){
  }

}

class WebSocketChatDelegator(server: ServerRef, worker: WorkerRef) extends Delegator(server, worker) {

  def acceptNewConnection = Some(new DemoHandler())
}

object WebSocketExample {

  def start(port: Int)(implicit io: IOSystem): ServerRef = {
    val echoConfig = ServerConfig(
      name = "chat",
      settings = ServerSettings(
        port = port
      ),
      delegatorFactory = (server, worker) => new WebSocketChatDelegator(server, worker)
    )
    Server(echoConfig)

  }

}


