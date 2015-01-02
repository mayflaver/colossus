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
  object  WebSocketRequestParser {
    def apply(): Parser[WebSocketRequest] = HttpRequestParser() >> {httpRequest => WebSocketRequest(httpRequest)}
  }

  object WebSocketResponseParser {
    def apply(): Parser[WebSocketResponse] = HttpResponseParser() >> {httpResponse => WebSocketResponse(httpResponse)}
  }

  def WebSocketFrameParser: Parser[WebSocket] = {
    val FIN = 0x80
    val RSV1 = 0x40
    val RSV2 = 0x20
    val RSV3 = 0x10
    val RSV_MASK = RSV1 | RSV2 | RSV3
    val OPCODE_MASK = 0x0f
    var final_frame: Option[Int]= None
    var frame_opcode: Option[Int] = None
    var masked_frame: Option[Int] = None
    var frame_length: Option[Int] = None
    var fragmented_message_buffer = new StringBuilder
    var fragmented_message_opcode: Option[Int] = None
    var frame_opcode_is_control: Option[Int] = None
    byte ~ byte  |> { case first ~ second => {
      final_frame = Some(first & FIN)
      val reserved_bits = first & RSV_MASK
      if (reserved_bits != 0) throw new WebSocketException("client is using as-yet-undefined extensions")
      frame_opcode = Some(first & OPCODE_MASK)
      frame_opcode_is_control = frame_opcode.map( _ & 0x8)
      masked_frame = Some(second & 0x80)
      val payloadlen = second & 0x7f
      if (frame_opcode_is_control !=0  && payloadlen >= 126) throw new WebSocketException("control frames must have payload < 126 ")
      val p = payloadlen match {
        case payloadlen if payloadlen < 126 =>
          if (masked_frame != 0) bytes(4) ~ bytes(payloadlen) >> { case frameMask ~ data => data}
          else bytes(payloadlen)
        case 126 =>
          if (masked_frame != 0) bytes(2) |> { case payloadlen => bytes(4) ~ bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> { case frameMask ~ data => data} }
          else bytes(2) |> { case payloadlen => bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) }
        case 127 =>
          if (masked_frame != 0) bytes(8) |> { case payloadlen => bytes(4) ~ bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> { case frameMask ~ data => data} }
          else bytes(2) |> { case payloadlen => bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) }
      }
      p >> {data => frame_opcode match {
        case 0x0 =>
          if (fragmented_message_buffer.length == 0) throw new WebSocketException("nothing to continue")
          else {
            fragmented_message_buffer.append(data)
            if (final_frame == 0) {
              None
            } else {
              fragmented_message_opcode match {
                case 0x1 => WebSocketData(fragmented_message_buffer.toString)
                case 0x2 => WebSocketData(fragmented_message_buffer.toString)
                case 0x9 => WebSocketPing(fragmented_message_buffer.toString)
                case 0xA => WebSocketPong(fragmented_message_buffer.toString)
                case _ => throw new WebSocketException("***")

              }

            }

          }
        case 0x1 =>
          



      }

        if (frame_opcode == 0) {
          if (fragmented_message_buffer.length == 0) throw new WebSocketException("nothing to continue")
          fragmented_message_buffer.append(data)
          if (final_frame != 0) WebSocketData(fragmented_message_buffer.toString)
          else None
        }
        if
      }
      if (frame_opcode_is_control == 1 && final_frame == 0) throw new WebSocketException("control frames must not be fragmented")










    }
      byte >> {b => WebSocketData("message")}
    }
  }
}