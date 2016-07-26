package com.githubrepodownloader.main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by ramakrishnas on 26/7/16.
 */
public class ClientGitReposMetadataDownloader {

 public static void main(String args[]) throws IOException {
     BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
     String reposRange = br.readLine();
     ActorSystem system;

     system = ActorSystem.create("ClientGitHubReposMetadataDownloaderActorSystem", ConfigFactory.load("clientGitHubReposMetadataDownloader"));
     ActorRef actor = system.actorOf(Props.create(ClientGitHubReposMetadataDownloaderActor.class),
             "clientGitHubReposMetadataDownloaderActor");

     actor.tell(reposRange, actor);
    }
}
