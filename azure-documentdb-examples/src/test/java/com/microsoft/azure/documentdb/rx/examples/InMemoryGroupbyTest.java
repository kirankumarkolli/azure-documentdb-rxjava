/**
 * The MIT License (MIT)
 * Copyright (c) 2017 Microsoft Corporation
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.microsoft.azure.documentdb.rx.examples;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.documentdb.ConnectionPolicy;
import com.microsoft.azure.documentdb.ConsistencyLevel;
import com.microsoft.azure.documentdb.Database;
import com.microsoft.azure.documentdb.Document;
import com.microsoft.azure.documentdb.DocumentClientException;
import com.microsoft.azure.documentdb.DocumentCollection;
import com.microsoft.azure.documentdb.FeedOptions;
import com.microsoft.azure.documentdb.FeedResponsePage;
import com.microsoft.azure.documentdb.ResourceResponse;
import com.microsoft.azure.documentdb.SqlParameter;
import com.microsoft.azure.documentdb.SqlParameterCollection;
import com.microsoft.azure.documentdb.SqlQuerySpec;
import com.microsoft.azure.documentdb.rx.AsyncDocumentClient;

import rx.Observable;
import rx.observables.GroupedObservable;

public class InMemoryGroupbyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryGroupbyTest.class);
    private static final String DATABASE_ID = "in-memory-groupby";

    private AsyncDocumentClient asyncClient;

    private DocumentCollection createdCollection;

    @Before
    public void setUp() throws Exception {

        asyncClient = new AsyncDocumentClient.Builder()
                .withServiceEndpoint(TestConfigurations.HOST)
                .withMasterKey(TestConfigurations.MASTER_KEY)
                .withConnectionPolicy(ConnectionPolicy.GetDefault())
                .withConsistencyLevel(ConsistencyLevel.Session)
                .build();

        // Clean up the database.
        this.cleanUpGeneratedDatabases();

        Database databaseDefinition = new Database();
        databaseDefinition.setId(DATABASE_ID);

        DocumentCollection collectionDefinition = new DocumentCollection();
        collectionDefinition.setId(UUID.randomUUID().toString());

        // create database
        ResourceResponse<Database> databaseCreationResponse = asyncClient.createDatabase(databaseDefinition, null)
                .toBlocking().single();


        // create collection
        createdCollection = asyncClient
                .createCollection(databaseCreationResponse.getResource().getSelfLink(), collectionDefinition, null)
                .toBlocking().single().getResource();

        int numberOfPayers = 10;
        int numberOfDocumentsPerPayer = 10;

        for (int i = 0; i < numberOfPayers; i++) {

            for (int j = 0; j < numberOfDocumentsPerPayer; j++) {

                LocalDateTime currentTime = LocalDateTime.now();

                Document doc = new Document(String.format("{ "
                        + "'id' : '%s',"
                        + "'site_id': 'ABC', "
                        + "'payer_id': %d, "
                        + " 'created_time' : %d "
                        + "}", UUID.randomUUID().toString(), i, currentTime.getSecond()));
                asyncClient.createDocument(createdCollection.getSelfLink(), doc, null, true).toBlocking().single();

                Thread.sleep(100);
            }
        }
        System.out.println("finished inserting documents");
    }

    @After
    public void shutdown() throws DocumentClientException {
        asyncClient.close();
    }

    /**
     * If you want to understand the steps in more details see {@link #groupByInMemory_MoreDetail()}
     * @throws Exception
     */
    @Test
    public void groupByInMemory() throws Exception {

        // if you want to understand the steps in more details see groupByInMemoryMoreDetail()

        int requestPageSize = 3;
        FeedOptions options = new FeedOptions();
        options.setPageSize(requestPageSize);

        Observable<Document> documentsObservable = asyncClient
                .queryDocuments(createdCollection.getSelfLink(),
                        new SqlQuerySpec("SELECT * FROM root r WHERE r.site_id=@site_id",
                                new SqlParameterCollection(new SqlParameter("@site_id", "ABC"))),
                        options)
                .flatMap(page -> Observable.from(page.getResults()));

        final LocalDateTime now = LocalDateTime.now();

        List<List<Document>> resultsGroupedAsLists = documentsObservable
                .filter(doc -> Math.abs(now.getSecond() - doc.getInt("created_time")) <= 90)
                .groupBy(doc -> doc.getInt("payer_id")).flatMap(grouped -> grouped.toList())
                .toList()
                .toBlocking()
                .single();

        for(List<Document> resultsForEachPayer :resultsGroupedAsLists) {
            System.out.println("documents with payer_id : " + resultsForEachPayer.get(0).getInt("payer_id") + " are " + resultsForEachPayer);
        }
    }

    /**
     * This does the same thing as {@link #groupByInMemory_MoreDetail()} but with pedagogical details
     * @throws Exception
     */
    @Test
    public void groupByInMemory_MoreDetail() throws Exception {

        int requestPageSize = 3;
        FeedOptions options = new FeedOptions();
        options.setPageSize(requestPageSize);


        Observable<Document> documentsObservable = asyncClient
                .queryDocuments(createdCollection.getSelfLink(),
                        new SqlQuerySpec("SELECT * FROM root r WHERE r.site_id=@site_id",
                                new SqlParameterCollection(new SqlParameter("@site_id", "ABC"))),
                        options)
                .flatMap(page -> Observable.from(page.getResults()));

        final LocalDateTime now = LocalDateTime.now();

        Observable<GroupedObservable<Integer, Document>> groupedByPayerIdObservable = documentsObservable
                .filter(doc -> Math.abs(now.getSecond() - doc.getInt("created_time")) <= 90)
                .groupBy(doc -> doc.getInt("payer_id"));

        Observable<List<Document>> docsGroupedAsList = groupedByPayerIdObservable.flatMap(grouped -> {
            Observable<List<Document>> list = grouped.toList();
            return list;
        });

        List<List<Document>> resultsGroupedAsLists = docsGroupedAsList.toList().toBlocking().single();

        for(List<Document> resultsForEachPayer : resultsGroupedAsLists) {
            System.out.println("documents with payer_id : " + resultsForEachPayer.get(0).getInt("payer_id") + " are " + resultsForEachPayer);
        }
    }

    private void cleanUpGeneratedDatabases() throws DocumentClientException {
        LOGGER.info("cleanup databases invoked");

        String[] allDatabaseIds = { DATABASE_ID };

        for (String id : allDatabaseIds) {
            try {
                List<FeedResponsePage<Database>> feedResponsePages = asyncClient
                        .queryDatabases(new SqlQuerySpec("SELECT * FROM root r WHERE r.id=@id",
                                new SqlParameterCollection(new SqlParameter("@id", id))), null)
                        .toList().toBlocking().single();

                if (!feedResponsePages.get(0).getResults().isEmpty()) {
                    Database res = feedResponsePages.get(0).getResults().get(0);
                    LOGGER.info("deleting a database " + feedResponsePages.get(0));
                    asyncClient.deleteDatabase(res.getSelfLink(), null).toBlocking().single();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
