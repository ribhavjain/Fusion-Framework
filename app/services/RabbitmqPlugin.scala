package services

import play.api.{ Plugin, Logger, Application }
import com.typesafe.config.ConfigFactory
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import play.api.libs.json.Json
   
case class ExtractorMessage (
    id: String,
    host: String,
    key: String
)

/**
 * Rabbitmq service.
 *
 * @author Luigi Marini
 *
 */
class RabbitmqPlugin(application: Application) extends Plugin {

  val host = ConfigFactory.load().getString("rabbitmq.host");
  val exchange = ConfigFactory.load().getString("rabbitmq.exchange");
  var messageQueue: Option[ActorRef] = None
  
  override def onStart() {
    Logger.debug("Starting up Rabbitmq plugin.")
    try {
      val factory = new ConnectionFactory();
      factory.setHost(host);
      val connection: Connection = factory.newConnection()
      val sendingChannel = connection.createChannel()
      messageQueue =  Some(Akka.system.actorOf(Props(new SendingActor(channel = sendingChannel, exchange = exchange))))
    } catch {
      case ioe: java.io.IOException => Logger.error("Error connecting to rabbitmq broker")
      case _:Throwable => Logger.error("Unknown error connecting to rabbitmq broker")
    }
  }

  override def onStop() {
    Logger.debug("Shutting down Rabbitmq plugin.")
  }

  override lazy val enabled = {
    !application.configuration.getString("rabbitmqplugin").filter(_ == "disabled").isDefined
  }

  def extract(message: ExtractorMessage) = {
    Logger.info("Sending. message " + message.id)
    messageQueue match {
      case Some(x) => x ! message
      case None => Logger.warn("Could not send message over RabbitMQ")
    }
  }
}

class SendingActor(channel: Channel, exchange: String) extends Actor {
 
  def receive = {
      case ExtractorMessage(id, host, key) => {
        val msg = Json.toJson(Map(
            "id" -> Json.toJson(id),
            "host" -> Json.toJson(host)
            ))
        Logger.info(msg.toString())
        channel.basicPublish(exchange, key, true, null, msg.toString().getBytes())
      }
      
      case _ => {
        Logger.error("Unknown message type submitted.")
      }
  }
}