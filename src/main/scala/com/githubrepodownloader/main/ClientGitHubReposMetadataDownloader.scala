package com.githubrepodownloader.main

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import com.githubrepodownloader.logging.Logger
import com.typesafe.config.ConfigFactory

/**
  * Created by ramakrishnas on 19/7/16.
  */
class GitHubReposDownloader extends Actor with Logger {
  val remoteGitHubReposMetadataDownloaderPath: String = "akka.tcp://GitHubRepoMetadataDownloader@" +
    "127.0.0.1:5150/user/gitHubRepoMetadataDownloader"

  override def receive: Receive = {
    case "RECEIVED" => {
      log.info("Remote received and started the given task........");
    }
    case "COMPLETED" => {
      log.info("Remote completed the given task.......");
      context.system.shutdown
    }
    case msg => {
      val remoteGitHubReposMetadataDownloader = context.actorSelection(remoteGitHubReposMetadataDownloaderPath)
      remoteGitHubReposMetadataDownloader ! msg
    }
  }
}

object ClientGitHubReposMetadataDownloader {
  def main(args: Array[String]) {
    var since = args(0).toInt
    var to = args(1).toInt
    val configFile = getClass.getClassLoader.
      getResource("local_gitHubReposMetadataDownloader.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))
    val system = ActorSystem("ClientSystem", config)
    val localActor = system.actorOf(Props[GitHubReposDownloader], name = "local")
    localActor ! since + "," + to
  }
}
