package com.hackdiary.zoocker

import scala.concurrent._
import scala.util.{ Success, Failure }
import scala.concurrent.duration._
import akka.actor._
import spray.http._
import spray.client.pipelining._
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.framework.recipes.nodes.PersistentEphemeralNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lambdaworks.jacks.JacksMapper
import scala.collection.JavaConverters._

case class Config(host: String, dockerUrl: String, zookeeperHost: String, zookeeperPort: String)
case class Refresh(resultTo: ActorRef)
case class InspectContainer(id: String, resultTo: ActorRef)
case class RemoveContainer(id: String, resultTo: ActorRef)
case class AddNode(id: String, path: String, data: String)
case class RemoveNode(id: String)

object Zoocker extends App {
  val system = ActorSystem("zoocker")
  val envs = Seq("EXTERNAL_HOST", "DOCKER_URL", "ZK_PORT_2181_TCP_ADDR", "ZK_PORT_2181_TCP_PORT").filter {
    System.getenv(_) == null
  }
  if (envs.size > 0) {
    for (env <- envs) {
      system.log.error("Can't run without environment variable {}", env)
    }
    system.shutdown
  } else {
    val config = Config(
      host = System.getenv("EXTERNAL_HOST"),
      dockerUrl = System.getenv("DOCKER_URL"),
      zookeeperHost = System.getenv("ZK_PORT_2181_TCP_ADDR"),
      zookeeperPort = System.getenv("ZK_PORT_2181_TCP_PORT")
    )

    val zookeeper = system.actorOf(Props[ZookeeperNodes])
    val docker = system.actorOf(Props[DockerContainers])

    zookeeper ! config
    docker ! config
    docker ! Refresh(zookeeper)
  }
}

class DockerContainers extends Actor with ActorLogging {
  import context.dispatcher
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  val mapper = new ObjectMapper()

  def receive = {
    case config: Config => context.become(receiveWithContainers(config, Set()))
  }

  def receiveWithContainers(config: Config, existingContainers: Set[String]): Receive = {
    case Refresh(resultTo) => {
      pipeline(Get(config.dockerUrl + "/containers/json")) onComplete {
        case Success(response) => {
          val containers = mapper.readTree(response.entity.asString).elements.asScala.map(_.path("Id").asText).toSet
          (existingContainers -- containers).foreach(self ! RemoveContainer(_, resultTo))
          containers.foreach(self ! InspectContainer(_, resultTo))
          context.system.scheduler.scheduleOnce(2.seconds, self, Refresh(resultTo))
        }
        case Failure(failure) => {
          log.error("Failure getting container JSON: {}", failure)
          context.system.scheduler.scheduleOnce(2.seconds, self, Refresh(resultTo))
        }
      }
    }
    case RemoveContainer(id, resultTo) => {
      resultTo ! RemoveNode(id)
      context.become(receiveWithContainers(config, existingContainers - id))
    }
    case InspectContainer(id, resultTo) => {
      if (!existingContainers.contains(id)) {
        for (
          response <- pipeline(Get(config.dockerUrl + s"/containers/$id/json"));
          container = mapper.readTree(response.entity.asString)
        ) {
          val containerConfig = container.path("Config")
          val hostname = containerConfig.path("Hostname").asText
          val image = containerConfig.path("Image").asText.replace("/", ":")
          val path = s"/docker/$image/$id"
          val ports = container.path("NetworkSettings").path("Ports")
          val data = ports.fields.asScala.toList.flatMap { mapEntry =>
            val from = mapEntry.getKey.replace("/tcp", "")
            mapEntry.getValue.elements.asScala.map { toHostMap =>
              Map("port" -> from,
                "hostIp" -> toHostMap.get("HostIp").asText.replace("0.0.0.0", config.host),
                "hostPort" -> toHostMap.get("HostPort").asText)
            }
          }
          resultTo ! AddNode(id, path, JacksMapper.writeValueAsString(data))
        }
        context.become(receiveWithContainers(config, existingContainers + id))
      }
    }
  }
}

class ZookeeperNodes extends Actor with ActorLogging {
  var curator: CuratorFramework = _

  override def postStop {
    curator.close
  }

  def receive = {
    case config: Config => {
      curator = CuratorFrameworkFactory.newClient(config.zookeeperHost + ":" + config.zookeeperPort, new ExponentialBackoffRetry(1000, 3))
      curator.start
      curator.getZookeeperClient.blockUntilConnectedOrTimedOut

      context.become(receiveWithNodes(config, Map()))
    }
  }

  def receiveWithNodes(config: Config, nodes: Map[String, PersistentEphemeralNode]): Receive = {
    case RemoveNode(id) => {
      log.info(s"Remove node $id")
      for (node <- nodes.get(id)) node.close
      context.become(receiveWithNodes(config, nodes - id))
    }
    case AddNode(id, path, data) => {
      log.info(s"Adding node at $path")
      val node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, path, data.getBytes)
      node.start
      context.become(receiveWithNodes(config, nodes + (id -> node)))
    }
  }
}
