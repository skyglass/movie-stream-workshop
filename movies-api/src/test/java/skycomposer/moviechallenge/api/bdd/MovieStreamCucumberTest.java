package skycomposer.moviechallenge.api.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../docs/specs")
@ConfigurationParameter(key = "cucumber.glue", value = "skycomposer.moviechallenge.api.bdd")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
class MovieStreamCucumberTest {
}
