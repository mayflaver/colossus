package colossus
package protocols.websocket

import colossus.core.DataBuffer
import colossus.parsing.Combinators.Parser
import colossus.protocol.websocket._
import colossus.protocols.http.{HttpResponseParser, HttpRequestParser}
import colossus.parsing.Combinators._
import sun.security.x509.X400Address

class WebSocketException(message: String) extends Exception(message)

object WebSocketParser {

  object WebSocketRequestParser {
    def apply(): Parser[WebSocketRequest] = HttpRequestParser() >> { httpRequest => WebSocketRequest(httpRequest)}
  }

  object WebSocketResponseParser {
    def apply(): Parser[WebSocketResponse] = HttpResponseParser() >> { httpResponse => WebSocketResponse(httpResponse)}
  }

  object WebSocketFrameParser {
    def apply(): Parser[WebSocket] = {
      val FIN = 0x80
      val RSV1 = 0x40
      val RSV2 = 0x20
      val RSV3 = 0x10
      val RSV_MASK = RSV1 | RSV2 | RSV3
      val OPCODE_MASK = 0x0f
      var final_frame: Option[Int] = None
      var frame_opcode: Option[Int] = None
      var masked_frame: Option[Int] = None
      val fragmented_message_buffer = new StringBuilder
      var fragmented_message_opcode: Option[Int] = None
      var frame_opcode_is_control: Option[Int] = None
      byte ~ byte |> { case first ~ second => {
        final_frame = Some(first & FIN)
        println(Integer.toBinaryString(first&0xff))
        println(Integer.toBinaryString(second&0xff))
        val reserved_bits = first & RSV_MASK
        if (reserved_bits != 0) throw new WebSocketException("client is using as-yet-undefined extensions")
        frame_opcode = Some(first & OPCODE_MASK)
        frame_opcode_is_control = frame_opcode.map(_ & (0x8 | 0x9 | 0xA))
        masked_frame = Some(second & 0x80)
        val payloadlen = second & 0x7f
        if (frame_opcode_is_control != 0 && payloadlen >= 126) throw new WebSocketException("control frames must have payload < 126 ")
        val p = payloadlen match {
          case payloadlen if payloadlen < 126 =>
            if (masked_frame != 0) bytes(4) ~ bytes(payloadlen) >> { case frameMask ~ data => (0 until data.length).map(i => (data(i) ^ frameMask(i % 4)).toChar).mkString("")}
            else bytes(payloadlen) >> {data => data.toString}
          case 126 =>
            if (masked_frame != 0) bytes(2) |> { case payloadlen => bytes(4) ~ bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> { case frameMask ~ data => (0 until data.length).map(i => (data(i) ^ frameMask(i % 4)).toChar).mkString("")}}
            else bytes(2) |> { case payloadlen => bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> {data => data.toString}}
          case 127 =>
            if (masked_frame != 0) bytes(8) |> { case payloadlen => bytes(4) ~ bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> { case frameMask ~ data => (0 until data.length).map(i => (data(i) ^ frameMask(i % 4)).toChar).mkString("")}}
            else bytes(2) |> { case payloadlen => bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> {data => data.toString}}
        }
        p >> { data =>
          // control frames
          if (frame_opcode_is_control == 0) {
            if (final_frame == 0) throw new WebSocketException("control frames must not be fragmented")
          }
          // continuation frames
          else if (frame_opcode == 0) {
            if (fragmented_message_buffer.length == 0)
              throw new WebSocketException("nothing to continue")
            else
              fragmented_message_buffer.append(data)
          }
          else {
            if (fragmented_message_buffer.length != 0) throw new WebSocketException("can't start new message until the old one is finished")
            if (final_frame != 0) {
              fragmented_message_buffer.append(data)
              fragmented_message_opcode = frame_opcode
            }
          }
          if (final_frame == 0)
            WebSocketContinuation("")
          else {
            val message = fragmented_message_buffer.toString
            fragmented_message_buffer.clear()
            fragmented_message_opcode = None
            frame_opcode.get match {
              case 0x1 => WebSocketData(message)
              case 0x2 => WebSocketData(message)
              case 0x8 => WebSocketClose(message)
              case 0x9 => WebSocketPing(message)
              case 0xA => WebSocketPong(message)
              case _ => throw new WebSocketException("unspecific data")
            }
          }
        }
      }
      }
    }
  }

}
