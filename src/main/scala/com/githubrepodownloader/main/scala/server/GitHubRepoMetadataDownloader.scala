package com.githubrepodownloader.main.scala.server

import java.io._

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RoundRobinPool
import com.githubrepodownloader.logging.Logger
import com.githubrepodownloader.main.java
import com.typesafe.config.ConfigFactory
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.{HttpClient, MultiThreadedHttpConnectionManager}
import org.json4s.JsonAST.{JField, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.io.Source
import scala.util.Try

/**
  * Created by ramakrishnas on 18/7/16.
  */
case class RepoMetaDataDownloader(repoInfo: List[String])

case class UpdateTaskCompletion()

object GitHubReposMetadataDownloader extends App {

  val configFile = getClass.getClassLoader.
    getResource("gitHubReposMetadataDownloader.conf").getFile
  val config = ConfigFactory.parseFile(new java.io.File(configFile))
  val system = ActorSystem("GitHubRepoMetadataDownloaderActorSystem", config)
  val gitHubRepoMetadataDownloaderActor = system.actorOf(Props[GitHubReposMetadataDownloaderActor],
    name = "gitHubRepoMetadataDownloaderActor")
}

class GitHubReposMetadataDownloaderActor extends Actor with Logger {

  val conf = ConfigFactory.load()
  var noOfRepos = 0
  val noOfWorkers = conf.getString("noOfWorkers").toInt
  val workerRouter = context.actorOf(Props[RepoMetaDataDownloaderActor].
    withRouter(RoundRobinPool(noOfWorkers)), "workers")
  val result = new PrintWriter(conf.getString("metadataDir") +
    "ReposMetadata.txt")
  val clientGitHubReposMetadataDownloader = context.
    actorSelection(conf.getString("clientGitHubReposMetadataDownloaderPath"))

  override def receive: Actor.Receive = {
    case UpdateTaskCompletion() =>
      noOfRepos -= 1
      if (0 == noOfRepos) {
        clientGitHubReposMetadataDownloader ! "COMPLETED"
        val fileLocations = conf.getString("fileLocations").split(',')
        var fileLocationIndex = 0;
        while (fileLocationIndex < fileLocations.size - 1) {
          val repoMetadatasTemp1 = Source.fromFile(conf.getString("metadataDir") +
            fileLocations.apply(fileLocationIndex)).getLines.toList
          val repoMetadatasTemp2 = Source.fromFile(conf.getString("metadataDir") +
            fileLocations.apply(fileLocationIndex + 1)).getLines.toList
          fileLocationIndex += 1
          val reposMetadata = repoMetadatasTemp1 ++ repoMetadatasTemp2
          reposMetadata.foreach(repoMetadata => result.write(repoMetadata + "\n"))
        }
        result.close()
        sender() ! "COMPLETED"
        log.info("Task done...")
      }
    case msg =>
      sender() ! "RECEIVED"
      val args = msg.toString.split('-')
      val since = args(0).toInt
      val to = args(1).toInt
      if (to > since) {
        log.info("Started Getting repositories info from " + since + " to " + to)
        var currentSince = since
        while (currentSince < to) {
          val allGithubRepos = GitHubReposMetadataDownloaderHelper.getGitHubReposInfoFrom(currentSince)
          allGithubRepos.filter(repoInfo => repoInfo.toArray.apply(2).equals("false")).
            distinct.foreach(repoInfo => {
            noOfRepos += 1
            workerRouter ! RepoMetaDataDownloader(repoInfo)
          })
          currentSince = allGithubRepos.toArray.apply(allGithubRepos.size - 1).toArray.apply(0).toInt
        }
      }
      else
        sender() ! "INPUT-ERROR"
  }
}

class RepoMetaDataDownloaderActor extends Actor with Logger {

  val conf = ConfigFactory.load()
  val repoMetadataWriter = new PrintWriter(conf.getString("metadataDir") +
    self.path.name + ".txt")

  override def receive: Actor.Receive = {
    case RepoMetaDataDownloader(repoInfo) =>
      try {
        val repoMetadata: String =
          compact(render(GitHubReposMetadataDownloaderHelper.getJsonResponse
          ("https://api.github.com/repos/" + repoInfo.toArray.apply(1)).get)).toString
        val repoMetadataJson = repoMetadata + "\n"
        repoMetadataWriter.write(repoMetadataJson)
      }
      catch {
        case noSuchElementException: NoSuchElementException =>
          log.error("Failed to download repo metadata: " + repoInfo)
          // self ! RepoMetaDataDownloader(repoInfo)
      }
      finally {
        sender() ! UpdateTaskCompletion
      }
    case "COMPLETED" =>
      repoMetadataWriter.close
  }
}


object GitHubReposMetadataDownloaderHelper extends Logger {

  private val client = new HttpClient(new MultiThreadedHttpConnectionManager())
  val conf = ConfigFactory.load()
  var tokens = conf.getString("githubTokens").split(',')
  @volatile var currentToken = tokens(0)
  @volatile var lastIndex = 0

  def getGitHubReposInfoFrom(since: Int): List[List[String]] = {
    val json = getJsonResponse("https://api.github.com/repositories?since=" + since).toList
    val interestingFields = List("id", "full_name", "fork")
    val allGitHubRepos = for {
      reposInfoJson <- json
      repoDetailsJson <- reposInfoJson.children
      list = for {
        JObject(child) <- repoDetailsJson
        JField(name, value) <- child
        if interestingFields.contains(name)
      } yield value.values.toString
    } yield list
    allGitHubRepos
  }

  def getJsonResponse(url: String): Option[JValue] = {
    val method = new GetMethod(url)
    method.setDoAuthentication(true)
    method.addRequestHeader("Authorization", "token " + currentToken)
    log.debug("using token" + currentToken)
    client.executeMethod(method)

    val requestLimitRemaining = method.getResponseHeader("X-RateLimit-Remaining").getValue
    if (requestLimitRemaining.toInt == 0)
      updateToken()

    if (method.getStatusCode == 200)
      Try(parse(method.getResponseBodyAsString)).toOption
    else {
      log.error("Request failed with status:" + method.getStatusCode + "Response:"
        + method.getResponseHeaders.mkString("\n") +
        "\n ResponseBody " + method.getResponseBodyAsString)
      None
    }
  }

  def updateToken() = {
    if (lastIndex == tokens.length - 1) {
      lastIndex = 0
      currentToken = tokens(lastIndex)
    } else {
      lastIndex = lastIndex + 1
      currentToken = tokens(lastIndex)
    }
    log.info("limit 0,token changed :" + currentToken)
  }
}