package com.githubrepodownloader.main.scala.client

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import com.githubrepodownloader.logging.Logger
import com.typesafe.config.ConfigFactory

/**
  * Created by ramakrishnas on 19/7/16.
  */
class ClientGitHubReposMetadataDownloaderActor extends Actor with Logger {

  val conf = ConfigFactory.load()
  override def receive: Receive = {
    case "RECEIVED" => {
      log.info("Remote actor started the given task........");
    }
    case "INPUT-ERROR" => {
      log.info("Wrong input........");
    }
    case "COMPLETED" => {
      log.info("Remote actor completed the given task.......");
      context.system.shutdown
    }
    case msg => {
      val remoteGitHubReposMetadataDownloader = context.actorSelection(conf.
        getString("remoteGitHubReposMetadataDownloaderPath"))
      remoteGitHubReposMetadataDownloader ! msg
    }
  }
}

object ClientGitHubReposMetadataDownloader {
  def main(args: Array[String]) {
    var since = args(0).toInt
    var to = args(1).toInt
    val configFile = getClass.getClassLoader.
      getResource("clientGitHubReposMetadataDownloader.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))
    val system = ActorSystem("ClientGitHubReposMetadataDownloaderActorSystem", config)
    val localActor = system.actorOf(Props[ClientGitHubReposMetadataDownloaderActor],
      name = "clientGitHubReposMetadataDownloaderActor")
    localActor ! since + "-" + to
  }
}
