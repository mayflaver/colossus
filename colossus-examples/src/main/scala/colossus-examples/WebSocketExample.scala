package colossus.examples

import akka.util.ByteString
import colossus.IOSystem
import colossus.core._
import colossus.protocol.websocket.{WebSocketData, WebSocketResponse, WebSocketRequest, WebSocket}
import colossus.protocols.http.{HttpResponse, HttpVersion, HttpCodes}
import colossus.controller._
import colossus.protocols.websocket._
import java.security.MessageDigest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.Charsets.UTF_8

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


class WebSocketChatHandler() extends Controller[WebSocket, WebSocket](new WebSocketServerCodec, ControllerConfig(50)) {
  implicit lazy val sender = boundWorker.get.worker

  // Members declared in colossus.core.ConnectionHandler
  override def connected(endpoint: colossus.core.WriteEndpoint) {
    super.connected(endpoint)
    //push(Status("Please enter your name")){_ => ()}
  }

  protected def connectionClosed(cause: colossus.core.DisconnectCause){}
  protected def connectionLost(cause: colossus.core.DisconnectError){}
  def idleCheck(period: scala.concurrent.duration.Duration){}

  def receivedMessage(message: Any,sender: akka.actor.ActorRef){
  }


  def processMessage(request: WebSocket) {
    val response = request match {
      case request: WebSocketRequest =>
        println(request.request.head.headers)
        val headers = request.request.head.headers.foldLeft(List[(String, String)]()) { (headers, keyVal) =>
          val low = (keyVal._1.toLowerCase, keyVal._2)
          keyVal match {
            case ("connection", "Upgrade") =>
              ("Connection", "Upgrade")::headers
            case ("upgrade", "websocket") =>
              ("Upgrade", "websocket")::headers
            case ("host", host) =>
              headers
            case ("origin", origin) =>
              headers
            case ("sec-websocket-version", secWebSocketVersion) =>
              headers
            case ("sec-websocket-key", secWebSocketKey) =>
              val md = MessageDigest.getInstance("SHA1")
              val sha1 = md.digest((secWebSocketKey+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(UTF_8))
              val accept = new String(Base64.encodeBase64(sha1))
              ("Sec-WebSocket-Accept", accept)::headers
            case _ => headers
          }
        }
        println(headers)
        WebSocketResponse(HttpResponse(HttpVersion.`1.1`, HttpCodes.SWITCHING_PROTOCOLS, ByteString(""), headers))

      case body: WebSocketData =>
        println("message body")
        WebSocketData("message")
    }

    push(response){_ => ()}


  }

  override def connectionTerminated(cause: DisconnectCause) {
    super.connectionTerminated(cause)
  }

}

class WebSocketChatDelegator(server: ServerRef, worker: WorkerRef) extends Delegator(server, worker) {

  def acceptNewConnection = Some(new WebSocketChatHandler)
}

object WebSocketExample {

  def start(port: Int)(implicit io: IOSystem): ServerRef = {
    //val broadcaster = io.actorSystem.actorOf(Props[Broadcaster])
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


