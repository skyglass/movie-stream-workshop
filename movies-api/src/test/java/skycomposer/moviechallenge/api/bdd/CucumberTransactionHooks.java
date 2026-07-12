package skycomposer.moviechallenge.api.bdd;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.bdd.user.fixture.UserAccessFixture;
import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class CucumberTransactionHooks {

    private final PlatformTransactionManager transactionManager;
    private final MovieCatalogFixture movieCatalog;
    private final UserAccessFixture userAccess;
    private final RestApiFixture restApi;
    private TransactionStatus transaction;

    public CucumberTransactionHooks(PlatformTransactionManager transactionManager,
                                    MovieCatalogFixture movieCatalog,
                                    UserAccessFixture userAccess,
                                    RestApiFixture restApi) {
        this.transactionManager = transactionManager;
        this.movieCatalog = movieCatalog;
        this.userAccess = userAccess;
        this.restApi = restApi;
    }

    @Before
    public void startScenarioTransaction() {
        movieCatalog.resetPersistentScenarioState();
        userAccess.resetPersistentScenarioState();
        restApi.reset();
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    @After
    public void rollBackScenarioTransaction() {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
        movieCatalog.resetPersistentScenarioState();
        userAccess.resetPersistentScenarioState();
        restApi.reset();
    }
}
