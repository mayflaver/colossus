package colossus
package protocol.websocket

import akka.util.{ByteStringBuilder, ByteString}
import colossus.core.DataBuffer
import protocols.http.{HttpRequest, HttpResponse}
import java.nio.ByteOrder

sealed trait WebSocket {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  def bytes(): DataBuffer
  def onFrame(opcode: Byte, data: String) = {
    val finalFrame: Byte =  0x80.toByte
    val builder = new ByteStringBuilder
    // first byte
    builder.putByte((finalFrame | opcode).toByte)
    data.length match {
      case len: Int if (len < 126) =>
        builder.putByte(len.toByte)
      case len: Int if (len < (2 << 16 - 1)) =>
        builder.putByte(126.toByte)
        builder.putShort(len.toShort)
      case len: Int if (len < (2 << 64 - 1)) =>
        builder.putByte(127.toByte)
        builder.putLong(len.toLong)
    }

    builder.putBytes(data.getBytes)

    println(builder.result)
    DataBuffer(builder.result())
  }
}

case class WebSocketRequest(request: HttpRequest) extends WebSocket {
  def bytes() = DataBuffer(request.bytes)
}

case class WebSocketResponse(response: HttpResponse) extends WebSocket {
  def bytes() = response.bytes
}

case class WebSocketData(message: String) extends WebSocket {
  def bytes() = onFrame(0x1.toByte, message)
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

case class WebSocketContinuation(message: String) extends WebSocket {
  def bytes() = DataBuffer(ByteString(message))
}

