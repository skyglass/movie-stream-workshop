package com.ivanfranchin.moviesapi.bdd;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class CucumberTransactionHooks {

    private final PlatformTransactionManager transactionManager;
    private final MovieStreamCucumberFixture fixture;
    private TransactionStatus transaction;

    public CucumberTransactionHooks(PlatformTransactionManager transactionManager, MovieStreamCucumberFixture fixture) {
        this.transactionManager = transactionManager;
        this.fixture = fixture;
    }

    @Before
    public void startScenarioTransaction() {
        fixture.resetPersistentScenarioState();
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    @After
    public void rollBackScenarioTransaction() {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
        fixture.resetPersistentScenarioState();
    }
}
