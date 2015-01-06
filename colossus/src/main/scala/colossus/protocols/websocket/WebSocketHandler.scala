package colossus.protocols.websocket


import akka.util.ByteString
import colossus.core._
import colossus.protocol.websocket._
import colossus.protocols.http.{HttpResponse, HttpVersion, HttpCodes}
import colossus.controller._
import java.security.MessageDigest
import org.apache.commons.codec.Charsets.UTF_8
import org.apache.commons.codec.binary.Base64

abstract  class WebSocketHandler extends Controller[WebSocket, WebSocket](new WebSocketServerCodec, ControllerConfig(50)) {
  implicit lazy val sender = boundWorker.get.worker

  // Members declared in colossus.core.ConnectionHandler
  override def connected(endpoint: colossus.core.WriteEndpoint) {
    super.connected(endpoint)
    //push(Status("Please enter your name")){_ => ()}
  }

  protected def connectionClosed(cause: colossus.core.DisconnectCause){}
  protected def connectionLost(cause: colossus.core.DisconnectError){}
  def idleCheck(period: scala.concurrent.duration.Duration){}

  def onMessage(data: String): Unit
  def onClose(data: String): Unit
  def onPing(data: String): Unit
  def onPong(data: String): Unit
  def writeMessage(data: String): Unit = {
    push(WebSocketText(data)){_ => ()}
  }
  def close(): Unit = {
    push(WebSocketClose("")) { _ => {}}
  }


  def processMessage(request: WebSocket) {
    request match {
      case request: WebSocketRequest =>
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
        val response = WebSocketResponse(HttpResponse(HttpVersion.`1.1`, HttpCodes.SWITCHING_PROTOCOLS, ByteString(""), headers))
        push(response){_ => ()}
      case websockText: WebSocketText => {
        onMessage(websockText.message)

      }
      case websockBinary: WebSocketBinary => {
        onMessage(websockBinary.message)
      }
      case websockClose: WebSocketClose => {
        onClose(websockClose.message)
      }
      case websockPing: WebSocketPing => {
        onPing(websockPing.message)
      }
      case websockPong: WebSocketPong => {
        onPong(websockPong.message)
      }
      case _ => throw new WebSocketException("unspecific frame")


    }

  }

  override def connectionTerminated(cause: DisconnectCause) {
    super.connectionTerminated(cause)
    onClose("")
  }

}