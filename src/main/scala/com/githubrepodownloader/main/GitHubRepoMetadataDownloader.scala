package com.githubrepodownloader.main

import java.io._
import java.util.Properties

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RoundRobinPool
import com.githubrepodownloader.logging.Logger
import com.typesafe.config.ConfigFactory
import org.apache.commons.httpclient.{HttpClient, MultiThreadedHttpConnectionManager}
import org.apache.commons.httpclient.methods.GetMethod
import org.json4s.JsonAST.{JField, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.immutable.Map
import scala.util.Try

/**
  * Created by ramakrishnas on 18/7/16.
  */
case class GetRepositoryMetaData(printWriter: PrintWriter, repoMap: Map[String, String])

case class Superviser(id: String)

object GitHubRepoMetadataDownloader extends App {
  val prop = new Properties()
  prop.load(new FileInputStream("currentStatus.properties"))

  val configFile = getClass.getClassLoader.
    getResource("gitHubReposMetadataDownloader.conf").getFile
  val config = ConfigFactory.parseFile(new File(configFile))
  val system = ActorSystem("GitHubRepoMetadataDownloaderActorSystem", config)
  val remoteActor = system.actorOf(Props[GitHubRepoMetadataDownloaderActor],
    name = "gitHubRepoMetadataDownloaderActor")

  val requestedTill = prop.getProperty("ReposMetadataRequestedTill")
  val writtenTill = prop.getProperty("ReposMetadataWrittenTill")
  if (requestedTill > writtenTill)
    remoteActor ! writtenTill + "-" + requestedTill
}

class GitHubRepoMetadataDownloaderActor extends Actor with Logger {
  val prop = new Properties()
  prop.load(new FileInputStream("currentStatus.properties"))
  val conf = ConfigFactory.load()

  var noOfRequests = 0;
  var noOfRepos = 0
  val noOfWorkers = conf.getString("noOfWorkers").toInt
  val repoMetadataWriter = new PrintWriter("" + conf.getString("metadataDir") +
    self.path.name + ".txt")
  val workerRouter = context.actorOf(Props[RepositoryMetaDataDownloader].
    withRouter(RoundRobinPool(noOfWorkers)), "workers")

  override def receive: Actor.Receive = {
    case "RECEIVED" | "COMPLETED" | "INPUT-ERROR" =>
      log.info("Working on previous tasks")
    case Superviser(id) =>
      noOfRepos -= 1
      noOfRequests += 1;
      if (noOfRequests == conf.getInt("rateLimit"))
        GitHubApiHelper.updateToken()
      if (0 == noOfRepos) {
        repoMetadataWriter.close()
        log.info("Task done...")
        System.setProperty("ReposMetadataWrittenTill", id)
      }
    case msg =>
      sender() ! "RECEIVED"
      val receivedMsg = msg.toString
      if (receivedMsg.contains("-")) {
        val args = msg.toString.split('-')
        val since = args(0).toInt
        val to = args(1).toInt
        val writtenFrom = prop.getProperty("ReposMetadataWrittenFrom").toInt
        val writtenTill = prop.getProperty("ReposMetadataWrittenTill").toInt
        if ((writtenTill < to) || (writtenFrom > since)) {
          prop.setProperty("ReposMetadataWrittenFrom", since.toString)
          prop.setProperty("ReposMetadataRequestedTill", to.toString)
          log.info("Started Getting repositories info from " + since + " to " + to)
          var currentSince = since
          while (currentSince < to) {
            val (allGithubRepos, nextSince) = GitHubApiHelper.getAllGitHubRepos(currentSince)
            allGithubRepos.filter(repoMap => repoMap("fork").equals("false")).
              distinct.foreach(repoMap => {
              noOfRepos += 1
              workerRouter ! GetRepositoryMetaData(repoMetadataWriter, repoMap)
            })
            currentSince = nextSince
            noOfRequests += 1;
          }
        }
        sender() ! "COMPLETED"
      }
      else
        sender() ! "INPUT-ERROR"
  }
}

class RepositoryMetaDataDownloader extends Actor with Logger {

  override def receive: Actor.Receive = {
    case GetRepositoryMetaData(printWriter, repoMap) =>
      try {
        val repoMetadata: Option[String] =
          GitHubApiHelper.fetchAllDetails(repoMap)
        val repoMetadataJson = repoMetadata + "\n"
        printWriter.write(repoMetadataJson)
      }
      catch {
        case interruptedException: InterruptedException =>
          log.error(s"Failed to write " + interruptedException.printStackTrace())
      }
      finally {
        sender() ! Superviser(repoMap("id"))
      }
  }
}

object GitHubApiHelper extends Logger {

  private val client = new HttpClient(new MultiThreadedHttpConnectionManager())
  val conf = ConfigFactory.load()
  var tokens = conf.getString("githubTokens").split(',')
  @volatile var currentToken = tokens(0)
  @volatile var lastIndex = 0

  def getAllGitHubRepos(since: Int): (List[Map[String, String]], Int) = {
    val method = executeMethod(s"https://api.github.com/repositories?since=$since", currentToken)
    val nextSinceValueRaw = Option(method.getResponseHeader("Link").getElements.toList(0).getValue)
    val nextSince = nextSinceValueRaw.get.substring(0, nextSinceValueRaw.get.length - 1).toInt
    val json = httpGetJson(method).toList
    val interestingFields = List("id", "full_name", "fork")
    val allGitHubRepos = for {
      j <- json
      c <- j.children
      map = (for {
        JObject(child) <- c
        JField(name, value) <- child
        if interestingFields.contains(name)
      } yield name -> value.values.toString).toMap
    } yield map
    (allGitHubRepos, nextSince)
  }

  def executeMethod(url: String, token: String): GetMethod = {
    val method = new GetMethod(url)
    method.setDoAuthentication(true)
    method.addRequestHeader("Authorization", s"token $token")
    log.debug(s"using token $token")
    client.executeMethod(method)
    method
  }

  def fetchAllDetails(repoMap: Map[String, String]): Option[String] = {
    for {
      repo <-
      httpGetJson(executeMethod("https://api.github.com/repos/" + repoMap("full_name"), currentToken))
    } yield compact(render(repo))
  }

  def httpGetJson(method: GetMethod): Option[JValue] = {
    val status = method.getStatusCode
    if (status == 200) {
      Try(parse(method.getResponseBodyAsString)).toOption
    } else {
      log.error("Request failed with status:" + status + "Response:"
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