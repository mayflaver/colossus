package colossus
package protocol.websocket

import akka.util.ByteString
import colossus.core.DataBuffer
import protocols.http.{HttpRequest, HttpResponse}

sealed trait WebSocket {
  def bytes(): DataBuffer
}

case class WebSocketRequest(request: HttpRequest) extends WebSocket {
  def bytes() = DataBuffer(request.bytes)
}

case class WebSocketResponse(response: HttpResponse) extends WebSocket {
  def bytes() = response.bytes
}

case class WebSocketData(message: String) extends WebSocket {
  def bytes() = DataBuffer(ByteString(message))
}

case class WebSocketClose(message: String) extends WebSocket {
  def bytes() = DataBuffer(ByteString(message))
}

case class WebSocketPing(message: String) extends WebSocket {
  def bytes() = DataBuffer(ByteString(message))
}

case class WebSocketPong(message: String) extends WebSocket {
  def bytes() = DataBuffer(ByteString(message))
}

