package colossus.examples

import akka.actor.{Props, ActorRef, Actor}
import akka.event.LoggingAdapter
import colossus.IOSystem
import colossus.core._
import colossus.protocols.websocket.WebSocketHandler
import play.api.libs.functional.syntax._
import play.api.libs.json._

import org.apache.commons.codec.Charsets.UTF_8


/**
 * Created by wfc on 14-11-4.
 */


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

class RoomException(msg: String) extends Exception(msg)

trait Item {
  var _id: Option[Long] = None
  def id = _id
  def bind(id: Long): Unit = {
    _id = Some(id)
  }
  def unbind(): Unit = {
    _id = None
  }
}

case class User(worker: ActorRef, handlerId: Long, name: String) extends Item {
  var _roomId: Option[Long] = None
  def roomId = _roomId
  def bindRoom(id: Long): Unit = {
    _roomId = Some(id)
  }
  def unbindRoom(): Unit = {
    _roomId = None
  }

  var _zoneId: Option[Int] = None
  def zoneId = _zoneId
  def bindZone(id: Int): Unit = {
    _zoneId = Some(id)
  }
  def unbindZone(): Unit = {
    _zoneId = None
  }

  var _position: Option[Int] = None
  def position = _position
  def bindPos(id: Int): Unit = {
    _position = Some(id)
  }
  def unbindPosition(): Unit = {
    _position = None
  }
}

case class Room(zoneId: Int) extends Item {
  val _users = Array[Option[User]](None, None, None, None)

  def findPosition(): Option[Int] = {
    (0 to 3).filter(x => _users(x) == None).headOption
  }

  def set(pos: Int, user: User): Unit = {
    _users(pos) = Some(user)
    user.bindRoom(id.get)
    user.bindZone(zoneId)
    user.bindPos(pos)
  }

  def get(pos: Int): Option[User] = {
    _users(pos)
  }

  def remove(pos: Int): Unit = {
    _users(pos) = None
  }

  def count(): Int = {
    (0 to 3).filter(x => _users(x) != None).length
  }

  def broadcast(message: Message): Unit = {
    _users.foreach(userOption => userOption.foreach(user => user.worker ! WorkerCommand.Message(user.handlerId, message)))
  }

}

class District {
  val _zones = collection.mutable.Map[Int, RoomManager](1 -> RoomManager(), 2 -> RoomManager(), 3 -> RoomManager(), 4 -> RoomManager())
  def get(index: Int, gold: Int): Option[RoomManager] = {
    index match {
      case 1 if (gold <= 5000) => Some(_zones(index))
      case 2 if (gold <= 10000) => Some(_zones(index))
      case 3 if (gold <= 100000) => Some(_zones(index))
      case 4 if (gold > 100000) => Some(_zones(index))
      case _ => None
    }
  }



}

trait ItemManager[A <: Item] {

  val items = collection.mutable.Map[Long, A]()

  private var id: Long = 0L
  def newId(): Long = {
    id += 1
    id
  }

  def get(id: Long): Option[A] = items.get(id)

  /**
   * Binds a new item to this
   */
  def bind(item: A) {
      val id = newId()
      items(id) = item
      item.bind(id)
  }

  def unbind(id: Long) {
    if (items contains id) {
      val item = items(id)
      items -= id
    } else {
      println(s"Attempted to unbind worker $id that is not bound to this worker")
    }
  }

  def unbind(item: A) {
    item.id.map{i =>
      unbind(i)
    }.getOrElse(
        //maybe throw an exception instead?
        println("Attempted to unbind worker item that was already not bound!")
      )
  }


}

case class RoomManager extends ItemManager[Room] {
  var _busyRoom = Set[Long]()
  var _useRoom = Set[Long]()

  def addBusyRoom(id: Long): Unit = {
    if (!(items.contains(id))) throw new RoomException("add not exist room")
    if (_useRoom.contains(id)) {
      _useRoom = _useRoom - id
    }
    _busyRoom = _busyRoom + id
  }

  def removeBusyRoom(id: Long): Unit ={
    if (!(items.contains(id))) throw new RoomException("remove not exist room")
    _busyRoom = _busyRoom - id
  }

  def addUseRoom(id: Long): Unit ={
    if (!(items.contains(id))) throw new RoomException("add not exist room")
    if (_busyRoom.contains(id)) {
      _busyRoom = _busyRoom - id
    }
    _useRoom = _useRoom + id

  }

  def getUseRoomRandom(): Option[Long] = {
    if (_useRoom.size != 0) Some(_useRoom.head)
    else None

  }
  def removeUseRoom(id: Long): Unit ={
    if (!(items.contains(id))) throw new RoomException("remove not exist room")
    _useRoom = _useRoom - id
  }

}
case class UserManager extends ItemManager[User] {
  var _count: Long = 0L
  def increase(): Unit = {
    _count = _count + 1
  }

  def decrease(): Unit = {
    _count = _count - 1
  }

  def count(): Long = {
    _count
  }
}



sealed trait Message
case class UserIdMessage(id: String) extends Message
case class UserLoginRoomMessage(id: String, position: String) extends Message
case class UserLogOutRoomMessage(id: String, position: String) extends Message
case class RoomMessage(id: String, position: String) extends Message

class Broacaster extends Actor {
  import Broacaster._


  val userManager =  UserManager()
  val district = new District()
  val zones =  Array[RoomManager](RoomManager(), RoomManager(), RoomManager(), RoomManager())


  def broadcast(room: Room, message: Message) {
    room.broadcast(message)
  }

  def receive = {
    case GetUserId(name, handlerId) => {
      val user = User(sender(), handlerId, name)
      userManager.bind(user)
      userManager.increase()
      sender ! WorkerCommand.Message(handlerId, UserIdMessage(user.id.get.toString))
    }
    case GetRoomId(userId, zone, handlerId) => {
      val user = userManager.get(userId.toLong)
      val roomManager = zones(zone.toInt)
      val roomId = roomManager.getUseRoomRandom()
      if (roomId == None) {
        // create new room
        val room = Room(zone.toInt)
        roomManager.bind(room)
        roomManager.addUseRoom(room.id.get)
        val position = room.findPosition()
        room.set(position.get, user.get)
        sender ! WorkerCommand.Message(handlerId, RoomMessage(room.id.get.toString, position.get.toString))
      }
      else {
        // find a use room
        val room = roomManager.get(roomId.get)
        val position = room.map {case x => x.findPosition()}.get
        room.foreach {case r => r.set(position.get, user.get)}
        if (room.get.count() == 4) roomManager.addBusyRoom(room.get.id.get)
        else roomManager.addUseRoom(room.get.id.get)
        sender ! WorkerCommand.Message(handlerId, RoomMessage(roomId.get.toString, position.get.toString))
        room.foreach(room => room.broadcast(UserLoginRoomMessage(user.get.id.get.toString, position.get.toString)))
      }
    }
    case RoomLogOut(userId, handlerId) => {
      val user = userManager.get(userId.toLong)
      user.get.roomId match {
        case None => {}
        case Some(roomId) => {
          val room = zones(user.get.zoneId.get).get(roomId)
          room.foreach(room => room.remove(user.get.position.get))
          zones(user.get.zoneId.get).addUseRoom(roomId)
          room.foreach(room => room.broadcast(UserLogOutRoomMessage(user.get.id.get.toString, user.get.position.get.toString)))
        }
      }
    }
    case UserLogOut(userId, handlerId) => {
      userManager.unbind(userId.toLong)
    }


  }
}

object Broacaster {
  sealed trait BroadcasterMessage
  case class GetUserId(name: String, handlerId: Long) extends BroadcasterMessage
  case class GetRoomId(userId: String, zone: String, handlerId: Long) extends BroadcasterMessage
  case class RoomLogOut(userId: String, handlerId: Long) extends BroadcasterMessage
  case class UserLogOut(userId: String, handlerId: Long) extends BroadcasterMessage

}



class DemoHandler(broadcaster: ActorRef) extends WebSocketHandler {
  var userId: Option[String] = None
  def onMessage(data: String): Unit = {
    val json = Json.parse(data)
    json.\("command").asOpt[String].get match {
      case "login"  =>
        broadcaster ! Broacaster.GetUserId(json.\("name").asOpt[String].get, id.get)
      case "roomLogIn" => {
        if (json.\("id").asOpt[String].get != userId.get.toString) throw new Exception("id error")
        broadcaster ! Broacaster.GetRoomId(json.\("id").asOpt[String].get, json.\("zone").asOpt[String].get, id.get)
      }
      case "roomLogOut" => {
        if (json.\("id").asOpt[String].get != userId.get.toString) throw new Exception("id error")
        broadcaster ! Broacaster.RoomLogOut(json.\("id").asOpt[String].get, id.get)
      }
    }
  }
  def onClose(data: String): Unit = {
    broadcaster ! Broacaster.RoomLogOut(userId.get, id.get)
    broadcaster ! Broacaster.UserLogOut(userId.get, id.get)
  }
  def onPing(data: String): Unit = {
    println("ping")
  }
  def onPong(data: String): Unit = {
    println("pong")
  }
  def receivedMessage(message: Any,sender: akka.actor.ActorRef): Unit ={
    println("receivedMessage")
    message match {
      case UserIdMessage(id) => {
        userId = Some(id)
        writeMessage(Json.obj("id" -> JsString(id), "command" -> JsString("login")).toString)

      }
      case RoomMessage(id, position) => {
        writeMessage(Json.obj("roomId" -> JsString(id),  "command" -> JsString("room"), "position" -> JsString(position)).toString)
      }
      case UserLoginRoomMessage(id, position) =>
        writeMessage(Json.obj("id" -> JsString(id), "command" -> JsString("roomLogIn"), "position" -> JsString(position)).toString)
      case UserLogOutRoomMessage(id, position) =>
        writeMessage(Json.obj("id" -> JsString(id), "command" -> JsString("roomLogOut"), "position" -> JsString(position)).toString)
    }
  }

}

class WebSocketChatDelegator(server: ServerRef, worker: WorkerRef, broadcaster: ActorRef) extends Delegator(server, worker) {

  def acceptNewConnection = Some(new DemoHandler(broadcaster))
}

object WebSocketExample {

  def start(port: Int)(implicit io: IOSystem): ServerRef = {
    val broadcaster = io.actorSystem.actorOf(Props[Broacaster])
    val echoConfig = ServerConfig(
      name = "chat",
      settings = ServerSettings(
        port = port
      ),
      delegatorFactory = (server, worker) => new WebSocketChatDelegator(server, worker, broadcaster)
    )
    Server(echoConfig)

  }

}


