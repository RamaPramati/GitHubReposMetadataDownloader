package com.githubrepodownloader.logging

import org.slf4j.LoggerFactory

/**
  * Created by ramakrishnas on 20/7/16.
  */
trait Logger {
  val log = LoggerFactory.getLogger(this.getClass.getName)
}
