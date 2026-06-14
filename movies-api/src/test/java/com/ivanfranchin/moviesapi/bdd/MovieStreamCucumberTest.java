package com.ivanfranchin.moviesapi.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../docs/capabilities")
@ConfigurationParameter(key = "cucumber.glue", value = "com.ivanfranchin.moviesapi.bdd")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
class MovieStreamCucumberTest {
}
