package colossus
package protocols.websocket

import protocol.websocket.WebSocket
import service.Codec
import core._
import protocols.websocket.WebSocketParser._


class WebSocketServerCodec extends Codec.ServerCodec[WebSocket, WebSocket] {
  private var parser = WebSocketRequestParser() onceAndThen WebSocketBodyParser()
  def decode(data: DataBuffer): Option[WebSocket] = parser.parse(data)
  def encode(response: WebSocket): DataBuffer = response.bytes()
  def reset() = {
    parser = WebSocketRequestParser() onceAndThen WebSocketBodyParser()
  }
}

