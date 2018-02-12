- Fat Jar of the Azure documentdb Rx Java SDK (SNAPSHOT version) is provided here
- Examples can be found at https://github.com/Azure/azure-documentdb-rxjava/tree/master/azure-documentdb-examples
- The examples reference FeedResponsePage whereas the SDK now uses FeedResponse instead. This is a breaking change.
eg; The line at https://github.com/Azure/azure-documentdb-rxjava/blob/master/azure-documentdb-examples/src/test/java/com/microsoft/azure/documentdb/rx/examples/DocumentCRUDAsyncAPITest.java#L470 

Should be changed 
from 	: List<FeedResponsePage<Database>> feedResponsePages = asyncClient.queryDatabases(...)
to	: List<FeedResponse<Database>> feedResponse = asyncClient.queryDatabases(...)